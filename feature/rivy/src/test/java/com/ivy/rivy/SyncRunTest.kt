package com.ivy.rivy

import com.ivy.rivy.sync.PullResponse
import com.ivy.rivy.sync.PushResponse
import com.ivy.rivy.sync.RecordSource
import com.ivy.rivy.sync.SyncRun
import com.ivy.rivy.sync.SyncRunResult
import com.ivy.rivy.sync.SyncTransport
import com.ivy.rivy.sync.TYPE_INBOX_ITEM
import com.ivy.rivy.sync.TYPE_TRANSACTION
import com.ivy.rivy.sync.WireRecord
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/** Two devices syncing through a server that behaves like the real one. */
class SyncRunTest {

    /** Keeps the latest version of every record, like the Go server does. */
    private class FakeServer : SyncTransport {
        private val records = LinkedHashMap<String, WireRecord>()
        private var seq = 0L
        var pulls = 0

        override suspend fun push(records: List<WireRecord>): PushResponse {
            val stale = mutableListOf<WireRecord>()
            var applied = 0
            for (record in records) {
                val key = record.type + "/" + record.id
                val existing = this.records[key]
                if (existing != null && existing.updatedAt >= record.updatedAt) {
                    stale += existing
                } else {
                    seq++
                    this.records[key] = record.copy(seq = seq)
                    applied++
                }
            }
            return PushResponse(applied = applied, stale = stale)
        }

        override suspend fun pull(since: Long, limit: Int): PullResponse {
            pulls++
            val newer = records.values.filter { it.seq > since }.sortedBy { it.seq }
            val page = newer.take(limit)
            return PullResponse(
                records = page,
                nextSince = page.lastOrNull()?.seq ?: since,
                hasMore = newer.size > limit,
            )
        }

        /** What the email inbox does: a record appears without any device pushing it. */
        fun insert(type: String, id: String, data: JsonObject, updatedAt: String) {
            seq++
            records[type + "/" + id] = WireRecord(type = type, id = id, data = data, updatedAt = updatedAt, seq = seq)
        }

        fun live(type: String): Map<String, JsonObject> =
            records.values.filter { it.type == type && !it.deleted }.associate { it.id to it.data }
    }

    private class FakeSource(override val type: String) : RecordSource {
        val items = LinkedHashMap<String, JsonObject>()

        override suspend fun readAll(): Map<String, JsonObject> = items.toMap()

        override fun normalize(id: String, data: JsonObject): JsonObject {
            require("broken" !in data) { "unusable" }
            return data
        }

        override suspend fun upsert(items: Map<String, JsonObject>) {
            this.items.putAll(items)
        }

        override suspend fun delete(id: String) {
            items.remove(id)
        }

        override suspend fun clear() = items.clear()
    }

    private class Device(private val server: FakeServer) {
        val transactions = FakeSource(TYPE_TRANSACTION)
        val inbox = FakeSource(TYPE_INBOX_ITEM)
        val index = HashMap<String, String>()
        var lastSeq = 0L

        /** The clock of this device, as the timestamp of its next sync. */
        var clock = "2026-11-11T10:00:00Z"

        suspend fun sync(allowMassDelete: Boolean = false): SyncRunResult = SyncRun(
            transport = server,
            sources = listOf(transactions, inbox),
            index = index,
            checkpoint = { lastSeq = it },
            now = { clock },
        ).run(lastSeq, allowMassDelete)
    }

    private fun trn(title: String) = JsonObject(mapOf("title" to JsonPrimitive(title)))

    private fun done(pushed: Int = 0, pulled: Int = 0, newInboxItems: Int = 0) =
        SyncRunResult.Done(pushed, pulled, newInboxItems)

    @Test
    fun `what one device has reaches the other`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        phone.transactions.items["1"] = trn("Makan")
        phone.transactions.items["2"] = trn("Bensin")

        phone.sync() shouldBe done(pushed = 2)
        tablet.sync() shouldBe done(pulled = 2)

        tablet.transactions.items shouldBe phone.transactions.items
    }

    @Test
    fun `a sync with nothing new sends and changes nothing`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        phone.transactions.items["1"] = trn("Makan")
        phone.sync()
        tablet.sync()

        phone.sync() shouldBe done()
        tablet.sync() shouldBe done()
    }

    @Test
    fun `an edit and a deletion reach the other device`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        phone.transactions.items["1"] = trn("Makan")
        phone.transactions.items["2"] = trn("Bensin")
        phone.sync()
        tablet.sync()

        phone.clock = "2026-11-11T11:00:00Z"
        phone.transactions.items["1"] = trn("Makan siang")
        phone.transactions.items.remove("2")
        phone.sync() shouldBe done(pushed = 2)
        tablet.sync() shouldBe done(pulled = 2)

        tablet.transactions.items shouldBe mapOf("1" to trn("Makan siang"))
        server.live(TYPE_TRANSACTION) shouldBe mapOf("1" to trn("Makan siang"))
    }

    @Test
    fun `when both devices edit the same item the later edit wins everywhere`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        phone.transactions.items["1"] = trn("Makan")
        phone.sync()
        tablet.sync()

        phone.transactions.items["1"] = trn("dari HP")
        tablet.transactions.items["1"] = trn("dari tablet")
        phone.clock = "2026-11-11T11:00:00Z"
        tablet.clock = "2026-11-11T12:00:00Z"
        phone.sync()
        tablet.sync()
        phone.sync()

        phone.transactions.items["1"] shouldBe trn("dari tablet")
        tablet.transactions.items["1"] shouldBe trn("dari tablet")
    }

    @Test
    fun `an edit older than the server's is dropped for the server's version`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        phone.transactions.items["1"] = trn("Makan")
        phone.sync()
        tablet.sync()

        phone.clock = "2026-11-11T12:00:00Z"
        phone.transactions.items["1"] = trn("dari HP")
        phone.sync()
        // The tablet's clock is behind, so its edit counts as the earlier one.
        tablet.clock = "2026-11-11T11:00:00Z"
        tablet.transactions.items["1"] = trn("dari tablet")
        tablet.sync()

        tablet.transactions.items["1"] shouldBe trn("dari HP")
        server.live(TYPE_TRANSACTION)["1"] shouldBe trn("dari HP")
        tablet.sync() shouldBe done()
    }

    @Test
    fun `a wiped device does not delete everything on the server`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        repeat(40) { phone.transactions.items["$it"] = trn("t$it") }
        phone.sync()

        phone.transactions.items.clear()
        phone.clock = "2026-11-11T11:00:00Z"

        phone.sync() shouldBe SyncRunResult.MassDelete(40)
        server.live(TYPE_TRANSACTION).size shouldBe 40

        // Only after the owner confirms.
        phone.sync(allowMassDelete = true) shouldBe done(pushed = 40)
        server.live(TYPE_TRANSACTION).size shouldBe 0
    }

    @Test
    fun `starting over merges the device and the server`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        repeat(40) { phone.transactions.items["$it"] = trn("t$it") }
        phone.sync()
        phone.transactions.items.clear()
        phone.transactions.items["new"] = trn("baru")

        // What "Pulihkan" does: forget what was synced and sync from the start.
        phone.index.clear()
        phone.lastSeq = 0
        phone.clock = "2026-11-11T11:00:00Z"
        phone.sync() shouldBe done(pushed = 1, pulled = 40)

        phone.transactions.items.size shouldBe 41
        server.live(TYPE_TRANSACTION).size shouldBe 41
    }

    @Test
    fun `everything arrives when the server has more than one page`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        repeat(1234) { phone.transactions.items["$it"] = trn("t$it") }

        phone.sync() shouldBe done(pushed = 1234)
        server.pulls = 0
        tablet.sync() shouldBe done(pulled = 1234)

        tablet.transactions.items.size shouldBe 1234
        server.pulls shouldBe 3
    }

    @Test
    fun `a bank transaction from the server is counted as new once`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        val pending = JsonObject(mapOf("status" to JsonPrimitive("pending"), "amount" to JsonPrimitive(18000)))
        server.insert(TYPE_INBOX_ITEM, "eml-1", pending, "2026-11-11T09:00:00Z")

        phone.sync() shouldBe done(pulled = 1, newInboxItems = 1)
        phone.sync() shouldBe done()

        // Approved on the phone: the tablet gets it, but there is nothing left to announce.
        phone.inbox.items["eml-1"] = JsonObject(pending + ("status" to JsonPrimitive("approved")))
        phone.sync() shouldBe done(pushed = 1)
        tablet.sync() shouldBe done(pulled = 1, newInboxItems = 0)
        tablet.inbox.items["eml-1"] shouldBe phone.inbox.items["eml-1"]
    }

    @Test
    fun `an unusable item and an unknown kind from the server are skipped`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        server.insert(TYPE_TRANSACTION, "bad", JsonObject(mapOf("broken" to JsonPrimitive(true))), "2026-11-11T09:00:00Z")
        server.insert("from_a_newer_app", "x", trn("?"), "2026-11-11T09:00:00Z")
        server.insert(TYPE_TRANSACTION, "good", trn("Makan"), "2026-11-11T09:00:00Z")

        phone.sync() shouldBe done(pulled = 1)

        phone.transactions.items shouldBe mapOf("good" to trn("Makan"))
        // Nothing the phone did not understand is sent back or deleted.
        phone.sync() shouldBe done()
        server.live(TYPE_TRANSACTION).keys shouldBe setOf("bad", "good")
    }

    @Test
    fun `an interrupted sync continues where it stopped`() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val tablet = Device(server)
        repeat(700) { phone.transactions.items["$it"] = trn("t$it") }
        phone.sync()

        // The tablet loses the connection after the first page.
        val flaky = object : SyncTransport {
            var calls = 0
            override suspend fun push(records: List<WireRecord>) = server.push(records)
            override suspend fun pull(since: Long, limit: Int): PullResponse {
                calls++
                check(calls == 1) { "connection lost" }
                return server.pull(since, limit)
            }
        }
        val interrupted = runCatching {
            SyncRun(flaky, listOf(tablet.transactions, tablet.inbox), tablet.index, { tablet.lastSeq = it }, { tablet.clock })
                .run(tablet.lastSeq, allowMassDelete = false)
        }
        interrupted.isFailure shouldBe true
        tablet.transactions.items.size shouldBe 500

        tablet.sync() shouldBe done(pulled = 200)
        tablet.transactions.items.size shouldBe 700
    }
}
