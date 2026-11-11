package com.ivy.rivy.sync

import com.ivy.rivy.recordHash
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/** The two calls a sync makes to the server. */
internal interface SyncTransport {
    suspend fun push(records: List<WireRecord>): PushResponse
    suspend fun pull(since: Long, limit: Int): PullResponse
}

internal sealed interface SyncRunResult {
    /** @param newInboxItems bank transactions that just arrived and wait for approval */
    data class Done(val pushed: Int, val pulled: Int, val newInboxItems: Int) : SyncRunResult

    /** Nothing was sent: [count] items are gone from this device, see [isMassDelete]. */
    data class MassDelete(val count: Int) : SyncRunResult
}

/**
 * One sync: sends what changed on this device, then fetches what changed
 * elsewhere. When the same item changed in two places, the server keeps the
 * later edit and this device takes it over.
 *
 * @param index fingerprints of the items as last synced, by [indexKey]. It is
 * updated as the sync goes.
 * @param checkpoint called with the number of the last applied server change
 * whenever progress should be saved, so an interrupted sync continues from there.
 * @param now the current time in RFC 3339, stamped on the changes that are sent
 */
internal class SyncRun(
    private val transport: SyncTransport,
    private val sources: List<RecordSource>,
    private val index: MutableMap<String, String>,
    private val checkpoint: (lastSeq: Long) -> Unit,
    private val now: () -> String,
) {
    private val byType = sources.associateBy { it.type }

    /**
     * @param lastSeq number of the last server change this device has applied
     * @param allowMassDelete send deletions even when it looks like the data
     * on this device was wiped
     */
    suspend fun run(lastSeq: Long, allowMassDelete: Boolean): SyncRunResult {
        val changes = mutableListOf<PendingChange>()
        for (source in sources) {
            changes += planPush(source.type, source.readAll(), index)
        }
        val deletions = changes.count { it.data == null }
        if (!allowMassDelete && isMassDelete(deletions, index.size)) {
            return SyncRunResult.MassDelete(deletions)
        }

        var pulled = 0
        var newInboxItems = 0
        var since = lastSeq

        val timestamp = now()
        for (chunk in changes.chunked(PUSH_CHUNK)) {
            val response = transport.push(chunk.map { it.toWire(timestamp) })
            for (change in chunk) {
                val key = indexKey(change.type, change.id)
                if (change.data == null) index.remove(key) else index[key] = change.hash
            }
            // Items the server had a later edit of: take the server's version.
            val applied = applyRemote(response.stale)
            pulled += applied.changed
            newInboxItems += applied.newInboxItems
            checkpoint(since)
        }

        do {
            val page = transport.pull(since, PULL_LIMIT)
            val applied = applyRemote(page.records)
            pulled += applied.changed
            newInboxItems += applied.newInboxItems
            since = maxOf(since, page.nextSince)
            checkpoint(since)
        } while (page.hasMore && page.records.isNotEmpty())

        return SyncRunResult.Done(pushed = changes.size, pulled = pulled, newInboxItems = newInboxItems)
    }

    private data class Applied(val changed: Int, val newInboxItems: Int)

    /** Writes items received from the server into the local data. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun applyRemote(records: List<WireRecord>): Applied {
        var changed = 0
        var newInboxItems = 0

        for ((type, group) in records.groupBy { it.type }) {
            // A kind this version of the app does not know is left alone.
            val source = byType[type] ?: continue
            val upserts = LinkedHashMap<String, JsonObject>()
            val hashes = HashMap<String, String>()

            for (record in group) {
                val key = indexKey(type, record.id)
                if (record.deleted) {
                    upserts.remove(record.id)
                    hashes.remove(key)
                    if (index.remove(key) != null) {
                        source.delete(record.id)
                        changed++
                    }
                    continue
                }

                val data = try {
                    source.normalize(record.id, record.data)
                } catch (e: Exception) {
                    Timber.e(e, "Rivy: skipped an unusable $type from the server")
                    continue
                }
                val hash = recordHash(data)
                if (index[key] == hash) continue

                if (type == TYPE_INBOX_ITEM && key !in index && isPending(data)) {
                    newInboxItems++
                }
                upserts[record.id] = data
                hashes[key] = hash
            }

            if (upserts.isNotEmpty()) {
                source.upsert(upserts)
                index.putAll(hashes)
                changed += upserts.size
            }
        }
        return Applied(changed, newInboxItems)
    }

    private fun isPending(data: JsonObject): Boolean =
        (data["status"] as? JsonPrimitive)?.content == "pending"

    private fun PendingChange.toWire(timestamp: String) = WireRecord(
        type = type,
        id = id,
        data = data ?: JsonObject(emptyMap()),
        updatedAt = timestamp,
        deleted = data == null,
    )

    private companion object {
        const val PUSH_CHUNK = 300
        const val PULL_LIMIT = 500
    }
}
