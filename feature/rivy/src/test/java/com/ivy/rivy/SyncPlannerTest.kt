package com.ivy.rivy

import com.ivy.rivy.sync.PendingChange
import com.ivy.rivy.sync.canonicalEntity
import com.ivy.rivy.sync.indexKey
import com.ivy.rivy.sync.isMarkedDeleted
import com.ivy.rivy.sync.isMassDelete
import com.ivy.rivy.sync.planPush
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class SyncPlannerTest {

    private fun item(title: String, amount: Double = 1000.0) = JsonObject(
        mapOf("title" to JsonPrimitive(title), "amount" to JsonPrimitive(amount))
    )

    @Test
    fun `hash ignores the order of keys`() {
        val a = RivyJson.parseToJsonElement("""{"title":"Makan","amount":15000.0,"tags":["a","b"]}""").jsonObject
        val b = RivyJson.parseToJsonElement("""{"tags":["a","b"],"amount":15000.0,"title":"Makan"}""").jsonObject

        recordHash(a) shouldBe recordHash(b)
    }

    @Test
    fun `hash changes when the content changes`() {
        recordHash(item("Makan")) shouldNotBe recordHash(item("Makan siang"))
        recordHash(item("Makan", 1000.0)) shouldNotBe recordHash(item("Makan", 1500.0))
    }

    @Test
    fun `nothing to push when everything matches the index`() {
        val local = mapOf("1" to item("a"), "2" to item("b"))
        val index = local.entries.associate { (id, data) -> indexKey("transaction", id) to recordHash(data) }

        planPush("transaction", local, index).shouldBeEmpty()
    }

    @Test
    fun `new and edited items are pushed`() {
        val index = mapOf(indexKey("transaction", "1") to recordHash(item("a")))
        val edited = item("a, edited")
        val added = item("b")

        val changes = planPush("transaction", mapOf("1" to edited, "2" to added), index)

        changes shouldContainExactlyInAnyOrder listOf(
            PendingChange("transaction", "1", edited, recordHash(edited)),
            PendingChange("transaction", "2", added, recordHash(added)),
        )
    }

    @Test
    fun `items gone from the device are pushed as deletions`() {
        val index = mapOf(
            indexKey("transaction", "1") to recordHash(item("a")),
            indexKey("transaction", "2") to recordHash(item("b")),
        )

        val changes = planPush("transaction", mapOf("1" to item("a")), index)

        changes shouldBe listOf(PendingChange("transaction", "2", null, ""))
    }

    @Test
    fun `other kinds in the index are left alone`() {
        val index = mapOf(
            indexKey("tag", "1") to "x",
            indexKey("tag_association", "1_2") to "y",
            indexKey("account", "9") to "z",
        )

        planPush("tag", mapOf("1" to item("a")), index).map { it.id } shouldBe listOf("1")
        planPush("category", emptyMap(), index).shouldBeEmpty()
    }

    @Test
    fun `a wiped device is not mistaken for many deletions by hand`() {
        isMassDelete(deletions = 3800, indexed = 3800) shouldBe true
        isMassDelete(deletions = 40, indexed = 60) shouldBe true
        // A few deletions, or many out of a lot more, are normal use.
        isMassDelete(deletions = 5, indexed = 6) shouldBe false
        isMassDelete(deletions = 40, indexed = 3800) shouldBe false
        isMassDelete(deletions = 0, indexed = 0) shouldBe false
    }

    @Test
    fun `leftover sync columns never count as a change`() {
        val fromOneDevice = RivyJson.parseToJsonElement(
            """{"name":"Cash","isSynced":false,"isDeleted":false,"lastSyncTime":1700000000000}"""
        ).jsonObject
        val fromAnother = RivyJson.parseToJsonElement(
            """{"name":"Cash","isSynced":true,"isDeleted":false,"lastSyncTime":0}"""
        ).jsonObject

        recordHash(canonicalEntity(fromOneDevice)) shouldBe recordHash(canonicalEntity(fromAnother))
        canonicalEntity(fromOneDevice).keys shouldBe fromOneDevice.keys
    }

    @Test
    fun `rows marked deleted are treated as gone`() {
        isMarkedDeleted(RivyJson.parseToJsonElement("""{"isDeleted":true}""").jsonObject) shouldBe true
        isMarkedDeleted(RivyJson.parseToJsonElement("""{"isDeleted":false}""").jsonObject) shouldBe false
        isMarkedDeleted(RivyJson.parseToJsonElement("""{"name":"x"}""").jsonObject) shouldBe false
    }
}
