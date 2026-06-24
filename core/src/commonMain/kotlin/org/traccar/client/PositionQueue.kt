package org.traccar.client

interface PositionQueue {
    suspend fun enqueue(position: Position)
    suspend fun peek(): Position?
    suspend fun removeFirst()
    suspend fun peek(limit: Int): List<Position>
    suspend fun remove(maxId: Long)
}
