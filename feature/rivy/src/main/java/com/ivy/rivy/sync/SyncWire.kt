package com.ivy.rivy.sync

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val TYPE_TRANSACTION = "transaction"
const val TYPE_INBOX_ITEM = "inbox_item"
const val TYPE_MERCHANT_RULE = "merchant_rule"
const val TYPE_ACCOUNT_LINK = "account_link"

@Keep
@Serializable
data class ServerConfig(
    @SerialName("google_client_id")
    val googleClientId: String = "",
    @SerialName("inbox_enabled")
    val inboxEnabled: Boolean = false,
)

/** One synced item as it travels between a device and the server. */
@Keep
@Serializable
data class WireRecord(
    @SerialName("type")
    val type: String = "",
    @SerialName("id")
    val id: String = "",
    @SerialName("data")
    val data: JsonObject = JsonObject(emptyMap()),
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("deleted")
    val deleted: Boolean = false,
    @SerialName("seq")
    val seq: Long = 0,
)

@Keep
@Serializable
data class PushRequest(
    @SerialName("records")
    val records: List<WireRecord> = emptyList(),
)

@Keep
@Serializable
data class PushResponse(
    @SerialName("applied")
    val applied: Int = 0,
    @SerialName("unchanged")
    val unchanged: Int = 0,
    /** Items the server already had a newer version of. */
    @SerialName("stale")
    val stale: List<WireRecord> = emptyList(),
)

@Keep
@Serializable
data class PullResponse(
    @SerialName("records")
    val records: List<WireRecord> = emptyList(),
    @SerialName("next_since")
    val nextSince: Long = 0,
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Keep
@Serializable
data class AuthRequest(
    @SerialName("id_token")
    val idToken: String = "",
    @SerialName("device_name")
    val deviceName: String = "",
)

@Keep
@Serializable
data class AuthResponse(
    @SerialName("token")
    val token: String = "",
    @SerialName("email")
    val email: String = "",
)

@Keep
@Serializable
data class ErrorResponse(
    @SerialName("error")
    val error: String = "",
)
