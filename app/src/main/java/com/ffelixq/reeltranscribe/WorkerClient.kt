package com.ffelixq.reeltranscribe

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ApiTranscript(
    val transcript: String,
    val language: String?
)

class WorkerClient {
    fun transcribe(baseUrl: String, accessToken: String, sourceUrl: String): ApiTranscript {
        val endpoint = baseUrl.trim().trimEnd('/') + "/transcribe"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (accessToken.isNotBlank()) {
                setRequestProperty("X-App-Token", accessToken)
            }
        }

        val payload = JSONObject().put("url", sourceUrl).toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        val json = runCatching { JSONObject(body) }.getOrNull()
        if (status !in 200..299) {
            val message = json?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "Backend request failed"
            throw IOException(status.toString() + ": " + message)
        }

        val transcript = json?.optString("transcript").orEmpty().trim()
        if (transcript.isBlank()) {
            throw IOException("Backend returned an empty transcript")
        }

        return ApiTranscript(
            transcript = transcript,
            language = json?.optString("language")?.takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
