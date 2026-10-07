package org.traccar.client

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class ActivityRecognitionDetector(
    private val scope: ComponentCoroutineScope,
    context: Context,
    config: Config,
    private val state: StateFlow<State>,
) : SignalSource {

    private val stopTimeoutSeconds = config.location.stopTimeoutSeconds

    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)

    override val signals = MutableSharedFlow<Signal>(extraBufferCapacity = 8)

    private var pendingIntent: PendingIntent? = null
    private var samplingPendingIntent: PendingIntent? = null
    private var stopTimeoutJob: Job? = null
    private var lastActivityTime = 0L

    init {
        scope.launch {
            ActivityRecognitionReceiver.events.collect { handleResult(it) }
        }
        scope.observeState(state, State::locationMode, inactive = LocationMode.Off) { mode ->
            stopTimeoutJob?.cancel()
            stopTimeoutJob = null
            if (mode == LocationMode.Off) {
                ensureUnregistered()
            } else {
                ensureRegistered()
                if (mode == LocationMode.Active) startSampling() else stopSampling()
            }
        }
    }

    private fun ensureRegistered() {
        if (pendingIntent != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION)
        ) {
            Log.log("Activity recognition permission missing, skipping")
            return
        }
        val newPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, ActivityRecognitionReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        pendingIntent = newPendingIntent

        val request = ActivityTransitionRequest(
            listOf(DetectedActivity.STILL, DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.RUNNING, DetectedActivity.WALKING).flatMap { activity ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map { transition ->
                    ActivityTransition.Builder().setActivityType(activity).setActivityTransition(transition).build()
                }
            }
        )
        client.requestActivityTransitionUpdates(request, newPendingIntent)
            .addOnSuccessListener { Log.log("Activity transitions registered") }
            .addOnFailureListener { Log.log("Activity transitions failed: $it") }
    }

    private fun startSampling() {
        if (pendingIntent == null || samplingPendingIntent != null) return
        lastActivityTime = SystemClock.elapsedRealtime()
        val newSamplingPendingIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            Intent(appContext, ActivityRecognitionReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        samplingPendingIntent = newSamplingPendingIntent
        client.requestActivityUpdates(30_000, newSamplingPendingIntent)
            .addOnSuccessListener { Log.log("Activity sampling requested") }
            .addOnFailureListener { Log.log("Activity sampling failed: $it") }
    }

    private fun ensureUnregistered() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        stopSampling()
        pendingIntent?.let {
            client.removeActivityTransitionUpdates(it)
            Log.log("Activity transitions removed")
        }
        pendingIntent = null
    }

    private fun stopSampling() {
        samplingPendingIntent?.let { client.removeActivityUpdates(it) }
        samplingPendingIntent = null
    }

    private fun handleResult(intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            handleSample(intent)
        } else {
            handleTransition(intent)
        }
    }

    private fun handleTransition(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            Log.log("Activity transition: ${activityName(event.activityType)} ${transitionName(event.transitionType)}")
            if (event.activityType != DetectedActivity.STILL) return@forEach
            lastActivityTime = maxOf(lastActivityTime, event.elapsedRealTimeNanos / 1_000_000)
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) onStillEnter()
            else onStillExit()
        }
    }

    private fun handleSample(intent: Intent) {
        if (samplingPendingIntent == null) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val activity = result.mostProbableActivity
        Log.log("Activity sample: ${activityName(activity.type)} ${activity.confidence}%")
        val time = result.elapsedRealtimeMillis
        if (time <= lastActivityTime || SystemClock.elapsedRealtime() - time !in 0..60_000) return
        if (activity.confidence < 75) return
        lastActivityTime = time
        when (activity.type) {
            DetectedActivity.STILL -> onStillEnter()
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING, DetectedActivity.WALKING -> onStillExit()
            else -> return
        }
        stopSampling() // resume transition-based detection
    }

    private fun activityName(type: Int): String = when (type) {
        DetectedActivity.STILL -> "still"
        DetectedActivity.IN_VEHICLE -> "in_vehicle"
        DetectedActivity.ON_BICYCLE -> "on_bicycle"
        DetectedActivity.RUNNING -> "running"
        DetectedActivity.WALKING -> "walking"
        else -> "unknown"
    }

    private fun transitionName(type: Int): String = when (type) {
        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "enter"
        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "exit"
        else -> "unknown"
    }

    private fun onStillEnter() {
        if (!state.value.enabled || state.value.paused || stopTimeoutJob?.isActive == true) return
        Log.log("Stop detection: arming ${stopTimeoutSeconds}s timeout")
        stopTimeoutJob = scope.launch {
            delay(stopTimeoutSeconds.seconds)
            signals.emit(Signal.StationaryEnter)
        }
    }

    private fun onStillExit() {
        if (stopTimeoutJob?.isActive == true) Log.log("Stop detection: cancelled")
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        scope.launch { signals.emit(Signal.StationaryExit) }
    }
}

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.log("Activity recognition broadcast received")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                sharedTracker()
                events.tryEmit(intent)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        val events = MutableSharedFlow<Intent>(replay = 1, extraBufferCapacity = 8)
    }
}
