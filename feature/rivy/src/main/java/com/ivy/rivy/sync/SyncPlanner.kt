package com.ivy.rivy.sync

import com.ivy.rivy.recordHash
import kotlinx.serialization.json.JsonObject

/** A change made on this device that the server does not know yet. */
data class PendingChange(
    val type: String,
    val id: String,
    /** The item as it is now, or null when it was deleted. */
    val data: JsonObject?,
    val hash: String,
)

internal fun indexKey(type: String, id: String): String = "$type/$id"

/**
 * Finds what changed on this device for one kind of item: items that are new
 * or differ from what was last synced, and items that are gone.
 *
 * @param local every item of [type] on this device, by id
 * @param index fingerprints of all kinds as last synced, by [indexKey]
 */
internal fun planPush(
    type: String,
    local: Map<String, JsonObject>,
    index: Map<String, String>,
): List<PendingChange> {
    val changes = mutableListOf<PendingChange>()
    for ((id, data) in local) {
        val hash = recordHash(data)
        if (index[indexKey(type, id)] != hash) {
            changes += PendingChange(type, id, data, hash)
        }
    }
    val prefix = "$type/"
    for (key in index.keys) {
        if (key.startsWith(prefix)) {
            val id = key.removePrefix(prefix)
            if (id !in local) {
                changes += PendingChange(type, id, null, "")
            }
        }
    }
    return changes
}

private const val MASS_DELETE_MINIMUM = 30

/**
 * True when so much is gone from this device that it looks like the app's data
 * was wiped, not like the owner deleting items one by one. Such a sync must be
 * confirmed before it removes the same items everywhere.
 */
internal fun isMassDelete(deletions: Int, indexed: Int): Boolean =
    deletions >= MASS_DELETE_MINIMUM && deletions * 2 >= indexed
