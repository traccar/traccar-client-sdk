package org.traccar.client

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round

class HttpUploader(
    private val config: Config,
    private val httpClient: HttpClient,
) : Uploader {

    override suspend fun upload(position: Position): Boolean = try {
        val response: HttpResponse = httpClient.submitForm(
            url = config.serverUrl,
            formParameters = Parameters.build {
                append("id", config.deviceId)
                position.latitude?.let { append("lat", it.toString()) }
                position.longitude?.let { append("lon", it.toString()) }
                append("timestamp", (position.time / 1000).toString())
                position.accuracy?.let { append("accuracy", it.toString()) }
                position.altitude?.let { append("altitude", it.toString()) }
                position.speed?.let { append("speed", it.toKnots().toString()) }
                position.bearing?.let { append("bearing", it.toString()) }
                position.battery?.let { append("batt", it.toString()) }
                position.charging?.let { append("charge", it.toString()) }
                position.alarm?.let { append("alarm", it) }
            },
        ) {
            config.headers.forEach { (name, value) -> headers.append(name, value) }
        }
        Log.log("Upload response ${response.status.value}")
        response.status.isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.log("Upload error: ${e.message}")
        false
    }

    private fun Double.toKnots(): Double = round(this * 1.94384 * 100) / 100
}
