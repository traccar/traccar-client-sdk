package org.traccar.client

interface Uploader {
    suspend fun upload(positions: List<Position>): Boolean
}
