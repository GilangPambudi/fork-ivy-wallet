package com.ivy.rivy.inbox

import com.ivy.base.model.TransactionType
import com.ivy.rivy.sha256Hex
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val ID_HASH_LENGTH = 32
private const val CENTS = 100.0

/** What kind of transaction an email most likely is. The owner can always change it. */
internal fun defaultType(item: InboxItem, paired: Boolean): TransactionType = when {
    paired -> TransactionType.TRANSFER
    item.kind == KIND_INCOMING -> TransactionType.INCOME
    item.kind == KIND_TOPUP || item.kind == KIND_POCKET_MOVE -> TransactionType.TRANSFER
    else -> TransactionType.EXPENSE
}

/** A merchant name in the form used to recognize it again. */
internal fun merchantKey(counterparty: String): String =
    counterparty.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

internal fun merchantRuleId(counterparty: String): String =
    "mr-" + sha256Hex(merchantKey(counterparty)).take(ID_HASH_LENGTH)

/**
 * Names the account of the owner an email is about: the account money left,
 * or for incoming money the account it arrived in.
 */
internal fun ownAccountKey(item: InboxItem): String {
    val name = if (item.kind == KIND_INCOMING) item.destination else item.source
    return (item.bank + "|" + name.ifBlank { item.bank }).lowercase(Locale.ROOT)
}

/** Names where a transfer or top up went, for example an e-wallet number. */
internal fun destinationKey(item: InboxItem): String =
    (item.bank + "|to|" + item.destination.ifBlank { item.counterparty }).lowercase(Locale.ROOT)

internal fun accountLinkId(key: String): String = "al-" + sha256Hex(key).take(ID_HASH_LENGTH)

/**
 * Turns a merchant name from a bank email into a title proposal.
 * "WARUNG BU TINI, LWK" becomes "Warung Bu Tini, Lwk". Names that already
 * have lowercase letters are kept as they are.
 */
internal fun proposeTitle(counterparty: String): String {
    val clean = counterparty.trim().replace(Regex("\\s+"), " ")
    if (clean.any { it.isLowerCase() }) return clean
    return clean.split(" ").joinToString(" ") { word ->
        word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}

/** Reads an amount typed by the owner: "12.500", "12500" or "12.500,50". */
internal fun parseAmountInput(text: String): Double? {
    val clean = text.trim().replace(" ", "").replace(".", "").replace(",", ".")
    if (clean.isEmpty()) return null
    return clean.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

/**
 * What is left of [total] for the first part of a split after the other parts
 * took theirs. Negative when the other parts ask for more than there is.
 */
internal fun splitRemainder(total: Double, others: List<Double>): Double =
    ((total - others.sum()) * CENTS).roundToLong() / CENTS

internal fun formatAmount(amount: Double, currency: String): String {
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    val whole = abs(amount - amount.roundToLong()) < 1 / (CENTS * 2)
    val number = DecimalFormat(if (whole) "#,##0" else "#,##0.00", symbols).format(amount)
    val prefix = if (currency == "IDR" || currency.isBlank()) "Rp" else currency
    return "$prefix $number"
}

internal fun parseInstant(text: String): Instant? =
    runCatching { Instant.parse(text) }.getOrNull()

internal fun bankName(bank: String): String = when (bank) {
    "bca" -> "BCA"
    "jago" -> "Jago"
    "blu" -> "blu"
    else -> bank
}
