package org.traccar.reactnative

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.traccar.client.Accuracy
import org.traccar.client.Config
import org.traccar.client.LocationConfig
import org.traccar.client.NotificationConfig
import org.traccar.client.requestPosition
import org.traccar.client.sharedTracker
import org.traccar.client.startTracking

class TraccarClientSdkModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun getName() = "TraccarClientSdk"

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    @ReactMethod
    fun initTracker(config: ReadableMap, promise: Promise) = scope.resolve(promise) {
        sharedTracker(parseConfig(config))
        null
    }

    @ReactMethod
    fun setConfig(config: ReadableMap, promise: Promise) = scope.resolve(promise) {
        sharedTracker()!!.updateConfig(parseConfig(config))
        null
    }

    @ReactMethod
    fun start(promise: Promise) = scope.resolve(promise) {
        sharedTracker()!!.startTracking(reactContext)
        null
    }

    @ReactMethod
    fun stop(promise: Promise) = scope.resolve(promise) {
        sharedTracker()?.stop()
        null
    }

    @ReactMethod
    fun requestPosition(alarm: String?, promise: Promise) = scope.resolve(promise) {
        sharedTracker()?.requestPosition(reactContext, alarm) ?: false
    }

    @ReactMethod
    fun isTracking(promise: Promise) = scope.resolve(promise) {
        sharedTracker()?.state?.value?.enabled ?: false
    }

    @ReactMethod
    fun getLogs(promise: Promise) = scope.resolve(promise) {
        val logs = sharedTracker()?.getLogs().orEmpty()
        Arguments.createArray().apply {
            logs.forEach { entry ->
                pushMap(
                    Arguments.createMap().apply {
                        putDouble("time", entry.time.toDouble())
                        putString("message", entry.message)
                    },
                )
            }
        }
    }

    @ReactMethod
    fun clearLogs(promise: Promise) = scope.resolve(promise) {
        sharedTracker()?.clearLogs()
        null
    }

    private fun CoroutineScope.resolve(promise: Promise, block: suspend () -> Any?) {
        launch {
            try {
                promise.resolve(block())
            } catch (e: Throwable) {
                promise.reject(e::class.simpleName ?: "error", e.message, e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConfig(config: ReadableMap): Config {
        val location = config.getMap("location")!!
        val notification = config.getMap("notification")!!
        return Config(
            serverUrl = config.getString("serverUrl")!!,
            headers = config.getMap("headers")?.toHashMap() as? Map<String, String> ?: emptyMap(),
            deviceId = config.getString("deviceId")!!,
            location = LocationConfig(
                accuracy = Accuracy.valueOf(location.getString("accuracy")!!),
                distanceMeters = location.getInt("distanceMeters"),
                intervalSeconds = location.getInt("intervalSeconds"),
                angleDegrees = location.getInt("angleDegrees"),
                stopDetection = location.getBoolean("stopDetection"),
                stopTimeoutSeconds = location.getInt("stopTimeoutSeconds"),
                stationaryRadiusMeters = location.getInt("stationaryRadiusMeters"),
                heartbeatIntervalSeconds = location.getInt("heartbeatIntervalSeconds"),
            ),
            wakeLock = config.getBoolean("wakeLock"),
            buffer = config.getBoolean("buffer"),
            preferPlatformProviders = config.getBoolean("preferPlatformProviders"),
            notification = NotificationConfig(text = notification.getString("text")!!),
        )
    }
}
