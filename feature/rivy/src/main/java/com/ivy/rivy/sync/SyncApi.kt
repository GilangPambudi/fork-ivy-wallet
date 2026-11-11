package com.ivy.rivy.sync

import com.ivy.rivy.RivyJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import javax.inject.Inject

/** The server refused a request. [status] is the HTTP status code. */
class SyncApiException(message: String, val status: Int) : Exception(message)

/** Calls to the Rivy sync server. Every call throws when it fails. */
class SyncApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    suspend fun config(serverUrl: String): ServerConfig =
        decode(httpClient.get("$serverUrl/v1/config"), ServerConfig.serializer())

    suspend fun signIn(serverUrl: String, idToken: String, deviceName: String): AuthResponse {
        val response = httpClient.post("$serverUrl/v1/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(RivyJson.encodeToString(AuthRequest.serializer(), AuthRequest(idToken, deviceName)))
        }
        return decode(response, AuthResponse.serializer())
    }

    suspend fun signOut(serverUrl: String, token: String) {
        httpClient.post("$serverUrl/v1/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun push(serverUrl: String, token: String, records: List<WireRecord>): PushResponse {
        val response = httpClient.post("$serverUrl/v1/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(RivyJson.encodeToString(PushRequest.serializer(), PushRequest(records)))
        }
        return decode(response, PushResponse.serializer())
    }

    suspend fun pull(serverUrl: String, token: String, since: Long, limit: Int): PullResponse {
        val response = httpClient.get("$serverUrl/v1/sync/pull") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("since", since)
            parameter("limit", limit)
        }
        return decode(response, PullResponse.serializer())
    }

    private suspend fun <T> decode(response: HttpResponse, serializer: KSerializer<T>): T {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching {
                RivyJson.decodeFromString(ErrorResponse.serializer(), text).error
            }.getOrNull().orEmpty()
            throw SyncApiException(
                message.ifBlank { "Server menjawab ${response.status.value}" },
                response.status.value
            )
        }
        return RivyJson.decodeFromString(serializer, text)
    }
}
