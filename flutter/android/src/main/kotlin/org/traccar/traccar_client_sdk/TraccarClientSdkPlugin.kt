package org.traccar.traccar_client_sdk

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.traccar.client.Accuracy
import org.traccar.client.Config
import org.traccar.client.LocationConfig
import org.traccar.client.NotificationConfig
import org.traccar.client.sharedTracker
import org.traccar.client.startTracking

class TraccarClientSdkPlugin :
    FlutterPlugin,
    MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "traccar_client_sdk")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "init" -> scope.launchHandler(result) {
                sharedTracker(parseConfig(call.arguments as Map<*, *>))
                null
            }
            "setConfig" -> scope.launchHandler(result) {
                sharedTracker()!!.updateConfig(parseConfig(call.arguments as Map<*, *>))
                null
            }
            "start" -> scope.launchHandler(result) {
                sharedTracker()!!.startTracking(context)
                null
            }
            "stop" -> scope.launchHandler(result) {
                sharedTracker()?.stop()
                null
            }
            "requestPosition" -> scope.launchHandler(result) {
                val alarm = (call.arguments as? Map<*, *>)?.get("alarm") as? String
                sharedTracker()?.requestPosition(alarm) ?: false
            }
            "isTracking" -> scope.launchHandler(result) {
                sharedTracker()?.state?.value?.enabled ?: false
            }
            "getLogs" -> scope.launchHandler(result) {
                sharedTracker()?.getLogs()?.map {
                    mapOf("time" to it.time, "message" to it.message)
                } ?: emptyList<Map<String, Any>>()
            }
            "clearLogs" -> scope.launchHandler(result) {
                sharedTracker()?.clearLogs()
                null
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    private fun CoroutineScope.launchHandler(result: Result, block: suspend () -> Any?) {
        launch {
            try {
                result.success(block())
            } catch (e: Throwable) {
                result.error(e::class.simpleName ?: "error", e.message, null)
            }
        }
    }

    private fun parseConfig(args: Map<*, *>): Config {
        val location = args["location"] as Map<*, *>
        val notification = args["notification"] as Map<*, *>
        return Config(
            serverUrl = args["serverUrl"] as String,
            deviceId = args["deviceId"] as String,
            location = LocationConfig(
                accuracy = Accuracy.valueOf(location["accuracy"] as String),
                distanceMeters = (location["distanceMeters"] as Number).toInt(),
                intervalSeconds = (location["intervalSeconds"] as Number).toInt(),
                angleDegrees = (location["angleDegrees"] as Number).toInt(),
                stopDetection = location["stopDetection"] as Boolean,
                stopTimeoutSeconds = (location["stopTimeoutSeconds"] as Number).toInt(),
                stationaryRadiusMeters = (location["stationaryRadiusMeters"] as Number).toInt(),
                heartbeatIntervalSeconds = (location["heartbeatIntervalSeconds"] as Number).toInt(),
            ),
            wakeLock = args["wakeLock"] as Boolean,
            buffer = args["buffer"] as Boolean,
            preferPlatformProviders = args["preferPlatformProviders"] as Boolean,
            notification = NotificationConfig(text = notification["text"] as String),
            headers = (args["headers"] as? Map<*, *>)?.map { (k, v) -> k.toString() to v.toString() }?.toMap() ?: emptyMap(),
            imei = args["imei"] as? String,
            attributes = (args["attributes"] as? Map<*, *>)?.map { (k, v) -> k.toString() to v.toString() }?.toMap() ?: emptyMap(),
            uploadJson = args["uploadJson"] as? Boolean ?: false,
        )
    }
}
