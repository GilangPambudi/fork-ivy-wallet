package com.ivy.rivy.inbox

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val KIND_PAYMENT = "payment"
const val KIND_TRANSFER = "transfer"
const val KIND_TOPUP = "topup"
const val KIND_INCOMING = "incoming"
const val KIND_POCKET_MOVE = "pocket_move"

const val STATUS_PENDING = "pending"
const val STATUS_APPROVED = "approved"
const val STATUS_DISMISSED = "dismissed"

/** A transaction the server read from a bank email. It waits for the owner's decision. */
@Keep
@Serializable
data class InboxItem(
    @SerialName("bank")
    val bank: String = "",
    @SerialName("kind")
    val kind: String = "",
    @SerialName("amount")
    val amount: Double = 0.0,
    @SerialName("currency")
    val currency: String = "IDR",
    /** The merchant or person on the other side. */
    @SerialName("counterparty")
    val counterparty: String = "",
    @SerialName("source")
    val source: String = "",
    @SerialName("destination")
    val destination: String = "",
    @SerialName("method")
    val method: String = "",
    @SerialName("reference")
    val reference: String = "",
    @SerialName("occurred_at")
    val occurredAt: String = "",
    @SerialName("status")
    val status: String = STATUS_PENDING,
    /** False when the email came from a bank but could not be read. */
    @SerialName("parsed")
    val parsed: Boolean = true,
    @SerialName("subject")
    val subject: String = "",
    /** Id of the other email of the same transfer, if there is one. */
    @SerialName("pair_id")
    val pairId: String = "",
    @SerialName("note")
    val note: String = "",
)

/** The title and category the owner last gave a merchant. Only ever a proposal. */
@Keep
@Serializable
data class MerchantRule(
    @SerialName("counterparty")
    val counterparty: String = "",
    @SerialName("title")
    val title: String = "",
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("type")
    val type: String = "",
)

/** Which account in the app belongs to an account a bank mentions in its emails. */
@Keep
@Serializable
data class AccountLink(
    @SerialName("key")
    val key: String = "",
    @SerialName("account_id")
    val accountId: String = "",
)
