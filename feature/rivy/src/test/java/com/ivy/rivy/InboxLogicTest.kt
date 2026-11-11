package com.ivy.rivy

import com.ivy.base.model.TransactionType
import com.ivy.rivy.inbox.InboxEntry
import com.ivy.rivy.inbox.InboxItem
import com.ivy.rivy.inbox.KIND_INCOMING
import com.ivy.rivy.inbox.KIND_PAYMENT
import com.ivy.rivy.inbox.KIND_POCKET_MOVE
import com.ivy.rivy.inbox.KIND_TOPUP
import com.ivy.rivy.inbox.KIND_TRANSFER
import com.ivy.rivy.inbox.accountLinkId
import com.ivy.rivy.inbox.defaultType
import com.ivy.rivy.inbox.destinationKey
import com.ivy.rivy.inbox.formatAmount
import com.ivy.rivy.inbox.merchantRuleId
import com.ivy.rivy.inbox.ownAccountKey
import com.ivy.rivy.inbox.pairEntries
import com.ivy.rivy.inbox.parseAmountInput
import com.ivy.rivy.inbox.proposeTitle
import com.ivy.rivy.inbox.sourceLine
import com.ivy.rivy.inbox.splitRemainder
import com.ivy.rivy.sync.normalizeServerUrl
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class InboxLogicTest {

    private val payment = InboxItem(
        bank = "bca", kind = KIND_PAYMENT, amount = 18000.0, counterparty = "Toko Sumber Rejeki",
        source = "0123****45", method = "QRIS", occurredAt = "2026-11-09T11:23:47Z",
    )
    private val transferOut = InboxItem(
        bank = "jago", kind = KIND_TRANSFER, amount = 650000.0, source = "500500500500",
        counterparty = "BUDI SANTOSO BCA Digital", destination = "BUDI SANTOSO BCA Digital",
    )
    private val transferIn = InboxItem(
        bank = "blu", kind = KIND_INCOMING, amount = 650000.0, destination = "blu",
        counterparty = "BUDI SANTOSO BANK JAGO UUS", pairId = "out",
    )

    @Test
    fun `an email is proposed as the kind of transaction it most likely is`() {
        defaultType(payment, paired = false) shouldBe TransactionType.EXPENSE
        defaultType(transferOut, paired = false) shouldBe TransactionType.EXPENSE
        defaultType(transferOut, paired = true) shouldBe TransactionType.TRANSFER
        defaultType(transferIn, paired = false) shouldBe TransactionType.INCOME
        defaultType(InboxItem(kind = KIND_TOPUP), paired = false) shouldBe TransactionType.TRANSFER
        defaultType(InboxItem(kind = KIND_POCKET_MOVE), paired = false) shouldBe TransactionType.TRANSFER
    }

    @Test
    fun `title proposal tidies names written in capitals`() {
        proposeTitle("WARUNG BU TINI, LWK") shouldBe "Warung Bu Tini, Lwk"
        proposeTitle("  KOPI   KENANGAN ") shouldBe "Kopi Kenangan"
        proposeTitle("Toko Sumber Rejeki") shouldBe "Toko Sumber Rejeki"
        proposeTitle("") shouldBe ""
    }

    @Test
    fun `the same merchant is recognized however it is spaced or cased`() {
        merchantRuleId("WARUNG BU TINI") shouldBe merchantRuleId("  warung  bu tini ")
        merchantRuleId("WARUNG BU TINI") shouldNotBe merchantRuleId("WARUNG BU TONO")
    }

    @Test
    fun `an email names the owner's account it is about`() {
        ownAccountKey(payment) shouldBe "bca|0123****45"
        ownAccountKey(transferIn) shouldBe "blu|blu"
        // No account in the email: the bank itself stands for it.
        ownAccountKey(InboxItem(bank = "jago", kind = KIND_INCOMING)) shouldBe "jago|jago"
        destinationKey(InboxItem(bank = "jago", kind = KIND_TOPUP, destination = "081200000001")) shouldBe
            "jago|to|081200000001"
        accountLinkId("bca|0123****45") shouldNotBe accountLinkId("blu|blu")
    }

    @Test
    fun `typed amounts are read the Indonesian way`() {
        parseAmountInput("12500") shouldBe 12500.0
        parseAmountInput("12.500") shouldBe 12500.0
        parseAmountInput(" 1.250.000,50 ") shouldBe 1250000.5
        parseAmountInput("") shouldBe null
        parseAmountInput("abc") shouldBe null
        parseAmountInput("-5") shouldBe null
    }

    @Test
    fun `the first part of a split gets what the others leave`() {
        splitRemainder(18000.0, emptyList()) shouldBe 18000.0
        splitRemainder(18000.0, listOf(5000.0, 3000.0)) shouldBe 10000.0
        splitRemainder(100.1, listOf(33.4, 33.3)) shouldBe 33.4
        splitRemainder(18000.0, listOf(20000.0)) shouldBe -2000.0
    }

    @Test
    fun `amounts are shown in rupiah style`() {
        formatAmount(18000.0, "IDR") shouldBe "Rp 18.000"
        formatAmount(1750000.0, "IDR") shouldBe "Rp 1.750.000"
        formatAmount(12000.5, "IDR") shouldBe "Rp 12.000,50"
        formatAmount(10.0, "USD") shouldBe "USD 10"
    }

    @Test
    fun `the two emails of a transfer become one entry`() {
        val entries = pairEntries(mapOf("out" to transferOut, "in" to transferIn, "pay" to payment))

        entries.map { it.id } shouldBe listOf("out", "pay")
        val transfer = entries.first()
        transfer.partnerId shouldBe "in"
        transfer.paired shouldBe true
        transfer.outgoing shouldBe transferOut
        transfer.incoming shouldBe transferIn
        entries.last().paired shouldBe false
        entries.last().incoming shouldBe null
    }

    @Test
    fun `an email whose pair is already decided stands alone`() {
        val entries = pairEntries(mapOf("in" to transferIn))

        entries shouldBe listOf(InboxEntry(id = "in", item = transferIn))
    }

    @Test
    fun `the original name is kept for the description`() {
        sourceLine(InboxEntry(id = "pay", item = payment)) shouldBe "Toko Sumber Rejeki · QRIS · BCA"
        sourceLine(InboxEntry(id = "x", item = InboxItem(bank = "jago", kind = KIND_INCOMING))) shouldBe "Jago"
    }

    @Test
    fun `a typed server address is cleaned up`() {
        normalizeServerUrl(" rivy.example.com/ ") shouldBe "https://rivy.example.com"
        normalizeServerUrl("https://rivy.example.com") shouldBe "https://rivy.example.com"
        normalizeServerUrl("http://192.168.1.5:8080") shouldBe "http://192.168.1.5:8080"
        normalizeServerUrl("   ") shouldBe null
        normalizeServerUrl("rivy example.com") shouldBe null
    }
}
