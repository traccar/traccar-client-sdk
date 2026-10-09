package org.traccar.client

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationFilter(
    config: Config,
    private val stateStore: StateStore,
) : PositionProcessor {

    private val locationConfig: LocationConfig = config.location
    private var lastAccepted: Position? = stateStore.state.value.lastAcceptedLocation
    private var lastProcessedPaused: Boolean = stateStore.state.value.paused

    override suspend fun process(position: Position): Position? {
        if (position.latitude == null || position.longitude == null) {
            Log.log("Heartbeat accepted")
            return position
        }
        val currentPaused = stateStore.state.value.paused
        if (currentPaused != lastProcessedPaused) {
            lastProcessedPaused = currentPaused
            persistAccepted(position)
            Log.log("Transition accepted ${position.latitude},${position.longitude}")
            return position
        }
        val previous = lastAccepted
        if (previous == null) {
            persistAccepted(position)
            Log.log("Location accepted ${position.latitude},${position.longitude}")
            return position
        }
        val timeTrigger = locationConfig.intervalSeconds > 0 &&
            (position.time - previous.time) >= locationConfig.intervalSeconds * 1000L
        val distanceTrigger = locationConfig.distanceMeters > 0 &&
            distance(previous, position) >= locationConfig.distanceMeters
        val angleTrigger = locationConfig.angleDegrees > 0 &&
            previous.bearing != null && position.bearing != null &&
            bearingChange(previous.bearing, position.bearing) >= locationConfig.angleDegrees
        if (timeTrigger || distanceTrigger || angleTrigger) {
            persistAccepted(position)
            Log.log("Location accepted ${position.latitude},${position.longitude}")
            return position
        }
        Log.log("Location filtered ${position.latitude},${position.longitude}")
        return null
    }

    private suspend fun persistAccepted(position: Position) {
        lastAccepted = position
        stateStore.update { it.copy(lastAcceptedLocation = position) }
    }

    private fun distance(from: Position, to: Position): Double {
        val fromLatitudeRadians = from.latitude!! * PI / 180
        val toLatitudeRadians = to.latitude!! * PI / 180
        val latitudeDelta = (to.latitude - from.latitude) * PI / 180
        val longitudeDelta = (to.longitude!! - from.longitude!!) * PI / 180
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(fromLatitudeRadians) * cos(toLatitudeRadians) * sin(longitudeDelta / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine))
    }

    private fun bearingChange(from: Double, to: Double): Double {
        val diff = abs(from - to)
        return if (diff > 180) 360 - diff else diff
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
