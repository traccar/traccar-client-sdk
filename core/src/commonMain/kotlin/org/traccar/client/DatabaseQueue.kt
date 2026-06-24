package org.traccar.client

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.traccar.client.db.Database

class DatabaseQueue(driver: SqlDriver) : PositionQueue {

    private val queries = Database(driver).positionQueries
    private val mutex = Mutex()

    override suspend fun enqueue(position: Position) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                queries.enqueue(
                    latitude = position.latitude,
                    longitude = position.longitude,
                    accuracy = position.accuracy,
                    time = position.time,
                    altitude = position.altitude,
                    speed = position.speed,
                    bearing = position.bearing,
                    battery = position.battery?.toLong(),
                    charging = position.charging?.let { if (it) 1L else 0L },
                    batteryHealth = position.batteryHealth,
                    batteryVoltage = position.batteryVoltage?.toLong(),
                    batteryTemperature = position.batteryTemperature,
                )
            }
        }
    }

    override suspend fun peek(): Position? = withContext(Dispatchers.IO) {
        mutex.withLock {
            queries.peek().executeAsOneOrNull()?.let { row ->
                Position(
                    id = row.id,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    accuracy = row.accuracy,
                    time = row.time,
                    altitude = row.altitude,
                    speed = row.speed,
                    bearing = row.bearing,
                    battery = row.battery?.toInt(),
                    charging = row.charging?.let { it != 0L },
                    batteryHealth = row.batteryHealth,
                    batteryVoltage = row.batteryVoltage?.toInt(),
                    batteryTemperature = row.batteryTemperature,
                )
            }
        }
    }

    override suspend fun removeFirst() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                queries.removeFirst()
            }
        }
    }

    override suspend fun peek(limit: Int): List<Position> = withContext(Dispatchers.IO) {
        mutex.withLock {
            queries.peekBatch(limit.toLong()).executeAsList().map { row ->
                Position(
                    id = row.id,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    accuracy = row.accuracy,
                    time = row.time,
                    altitude = row.altitude,
                    speed = row.speed,
                    bearing = row.bearing,
                    battery = row.battery?.toInt(),
                    charging = row.charging?.let { it != 0L },
                    batteryHealth = row.batteryHealth,
                    batteryVoltage = row.batteryVoltage?.toInt(),
                    batteryTemperature = row.batteryTemperature,
                )
            }
        }
    }

    override suspend fun remove(maxId: Long) {
        if (maxId <= 0) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                queries.removeBatch(maxId)
            }
        }
    }
}
