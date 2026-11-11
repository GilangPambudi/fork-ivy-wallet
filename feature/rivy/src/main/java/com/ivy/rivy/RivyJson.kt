package com.ivy.rivy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * JSON settings for everything Rivy sends or stores. Every field is always
 * written, so the same item always produces the same text.
 */
val RivyJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}

/** Text form of [element] that does not depend on the order of object keys. */
fun canonicalString(element: JsonElement): String = buildString { appendCanonical(element) }

private fun StringBuilder.appendCanonical(element: JsonElement) {
    when (element) {
        is JsonObject -> {
            append('{')
            element.keys.sorted().forEachIndexed { index, key ->
                if (index > 0) append(',')
                append(JsonPrimitive(key).toString())
                append(':')
                appendCanonical(element.getValue(key))
            }
            append('}')
        }

        is JsonArray -> {
            append('[')
            element.forEachIndexed { index, item ->
                if (index > 0) append(',')
                appendCanonical(item)
            }
            append(']')
        }

        else -> append(element.toString())
    }
}

fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private const val HASH_LENGTH = 32

/** Fingerprint of a synced item. Two items with the same content share it. */
fun recordHash(data: JsonObject): String = sha256Hex(canonicalString(data)).take(HASH_LENGTH)
