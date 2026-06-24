package org.traccar.client

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HttpUploader(
    private val config: Config,
    private val httpClient: HttpClient,
) : Uploader {

    override suspend fun upload(positions: List<Position>): Boolean = try {
        if (positions.isEmpty()) return true

        val response: HttpResponse = if (config.uploadJson) {
            val jsonArray = buildJsonArray {
                positions.forEach { position ->
                    add(buildJsonObject {
                        put("id", config.deviceId)
                        config.imei?.let { put("imei", it) }
                        position.latitude?.let { put("lat", it) }
                        position.longitude?.let { put("lon", it) }
                        put("timestamp", position.time / 1000)
                        position.accuracy?.let { put("accuracy", it) }
                        position.altitude?.let { put("altitude", it) }
                        position.speed?.let { put("speed", round(it * 1.94384 * 100) / 100) }
                        position.bearing?.let { put("bearing", it) }
                        position.battery?.let { put("batt", it) }
                        position.charging?.let { put("charge", it) }
                        position.alarm?.let { put("alarm", it) }
                        position.batteryHealth?.let { put("battery_health", it) }
                        position.batteryVoltage?.let { put("battery_voltage", it) }
                        position.batteryTemperature?.let { put("battery_temp", it) }
                        config.attributes.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                }
            }
            httpClient.post(config.serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(jsonArray.toString())
                config.headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
        } else {
            val position = positions.first()
            httpClient.submitForm(
                url = config.serverUrl,
                formParameters = Parameters.build {
                    append("id", config.deviceId)
                    config.imei?.let { append("imei", it) }
                    position.latitude?.let { append("lat", it.toString()) }
                    position.longitude?.let { append("lon", it.toString()) }
                    append("timestamp", (position.time / 1000).toString())
                    position.accuracy?.let { append("accuracy", it.toString()) }
                    position.altitude?.let { append("altitude", it.toString()) }
                    position.speed?.let { append("speed", (round(it * 1.94384 * 100) / 100).toString()) }
                    position.bearing?.let { append("bearing", it.toString()) }
                    position.battery?.let { append("batt", it.toString()) }
                    position.charging?.let { append("charge", it.toString()) }
                    position.alarm?.let { append("alarm", it) }
                    position.batteryHealth?.let { append("battery_health", it) }
                    position.batteryVoltage?.let { append("battery_voltage", it.toString()) }
                    position.batteryTemperature?.let { append("battery_temp", it.toString()) }
                    config.attributes.forEach { (key, value) ->
                        append(key, value)
                    }
                },
            ) {
                config.headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
        }
        Log.log("Upload response ${response.status.value}")
        response.status.isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.log("Upload error: ${e.message}")
        false
    }
}
