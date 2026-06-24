package org.traccar.client

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val serverUrl: String,
    val deviceId: String,
    val location: LocationConfig = LocationConfig(),
    val wakeLock: Boolean = false,
    val buffer: Boolean = true,
    val preferPlatformProviders: Boolean = false,
    val notification: NotificationConfig = NotificationConfig(),
    val headers: Map<String, String> = emptyMap(),
    val imei: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val uploadJson: Boolean = false,
) {
    constructor(serverUrl: String, deviceId: String) : this(serverUrl, deviceId, LocationConfig())
}

@Serializable
data class NotificationConfig(
    val text: String = "Location tracking",
)

@Serializable
data class LocationConfig(
    val accuracy: Accuracy = Accuracy.MEDIUM,
    val distanceMeters: Int = 75,
    val intervalSeconds: Int = 300,
    val angleDegrees: Int = 0,
    val stopDetection: Boolean = true,
    val stopTimeoutSeconds: Int = 60,
    val stationaryRadiusMeters: Int = 100,
    val heartbeatIntervalSeconds: Int = 0,
)

@Serializable
enum class Accuracy {
    HIGHEST,
    HIGH,
    MEDIUM,
    LOW,
}

val LocationConfig.effective: LocationConfig
    get() = if (accuracy == Accuracy.HIGHEST) {
        copy(distanceMeters = 0, intervalSeconds = 0, stopDetection = false)
    } else {
        this
    }
