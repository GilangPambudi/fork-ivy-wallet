package com.ivy.rivy.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncOutcome {
    /** @param newInboxItems transactions from bank emails that just arrived and wait for approval */
    data class Done(val pushed: Int, val pulled: Int, val newInboxItems: Int) : SyncOutcome

    data object NotConnected : SyncOutcome

    /** The server no longer accepts this device's session. */
    data object SignedOut : SyncOutcome

    /** Nothing was sent: [count] items are gone from this device, see [isMassDelete]. */
    data class MassDelete(val count: Int) : SyncOutcome

    data class Failed(val message: String) : SyncOutcome
}

/**
 * Keeps this device and the server the same. The sync itself is [SyncRun];
 * this class gives it the server, the local data and the saved progress, and
 * makes sure only one sync runs at a time.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: SyncApi,
    private val prefs: SyncPrefs,
    private val index: SyncIndex,
    private val sources: SyncSources,
) {
    private val mutex = Mutex()

    /**
     * @param allowMassDelete send the deletions even when [isMassDelete] says
     * it looks like the data on this device was wiped
     */
    suspend fun sync(allowMassDelete: Boolean = false): SyncOutcome = exclusive {
        runSync(allowMassDelete)
    }

    /** Throws away the data on this device and takes what the server has. */
    suspend fun replaceLocalWithServer(): SyncOutcome = exclusive {
        for (source in sources.all) {
            source.clear()
        }
        index.clear()
        prefs.lastSeq = 0
        runSync(allowMassDelete = false)
    }

    /**
     * Forgets what was synced before and syncs from the start. Everything on
     * this device and everything on the server ends up in both places.
     */
    suspend fun mergeWithServer(): SyncOutcome = exclusive {
        index.clear()
        prefs.lastSeq = 0
        runSync(allowMassDelete = false)
    }

    /** Forgets what was synced before, for a new connection to a server. */
    suspend fun reset() = exclusive {
        index.clear()
        prefs.lastSeq = 0
    }

    suspend fun serverHasData(): Boolean = withContext(Dispatchers.IO) {
        api.pull(prefs.serverUrl, prefs.token, since = 0, limit = 1).records.isNotEmpty()
    }

    private suspend fun <T> exclusive(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSync(allowMassDelete: Boolean): SyncOutcome {
        if (!prefs.connected) return SyncOutcome.NotConnected
        val url = prefs.serverUrl
        val token = prefs.token

        return try {
            val entries = index.entries()
            if (entries.isEmpty()) {
                // Nothing is known about earlier syncs, so start from the top.
                prefs.lastSeq = 0
            }
            val run = SyncRun(
                transport = object : SyncTransport {
                    override suspend fun push(records: List<WireRecord>): PushResponse =
                        api.push(url, token, records)

                    override suspend fun pull(since: Long, limit: Int): PullResponse =
                        api.pull(url, token, since, limit)
                },
                sources = sources.all,
                index = entries,
                checkpoint = { lastSeq ->
                    index.save()
                    prefs.lastSeq = lastSeq
                },
                now = { Instant.now().toString() },
            )

            when (val result = run.run(prefs.lastSeq, allowMassDelete)) {
                is SyncRunResult.MassDelete -> SyncOutcome.MassDelete(result.count)
                is SyncRunResult.Done -> {
                    prefs.lastSyncAt = System.currentTimeMillis()
                    prefs.lastError = ""
                    SyncOutcome.Done(result.pushed, result.pulled, result.newInboxItems)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncApiException) {
            if (e.status == HTTP_UNAUTHORIZED || e.status == HTTP_FORBIDDEN) {
                prefs.token = ""
                prefs.lastError = "Sesi berakhir. Sambungkan lagi."
                SyncOutcome.SignedOut
            } else {
                fail(e.message ?: "Server menolak permintaan.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Rivy: sync failed")
            fail("Tidak bisa menghubungi server: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fail(message: String): SyncOutcome {
        prefs.lastError = message
        return SyncOutcome.Failed(message)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
