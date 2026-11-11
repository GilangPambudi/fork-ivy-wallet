package com.ivy.rivy.inbox

import com.ivy.base.model.TransactionType
import java.util.UUID

/**
 * One thing to decide in the inbox. A transfer between two of the owner's
 * banks arrives as two emails; they are shown and decided as one entry.
 */
data class InboxEntry(
    val id: String,
    val item: InboxItem,
    val partnerId: String? = null,
    val partner: InboxItem? = null,
) {
    val paired: Boolean get() = partner != null

    /** The email of the bank the money left. */
    val outgoing: InboxItem
        get() = if (partner != null && item.kind == KIND_INCOMING) partner else item

    /** The email of the bank the money arrived at, for a paired transfer. */
    val incoming: InboxItem?
        get() = when {
            partner == null -> null
            item.kind == KIND_INCOMING -> item
            else -> partner
        }
}

/** What the app proposes for an entry. Nothing here is used without the owner's approval. */
data class Proposal(
    val type: TransactionType,
    val title: String,
    val categoryId: UUID?,
    val accountId: UUID?,
    val toAccountId: UUID?,
)

data class ApprovalPart(
    val title: String,
    val amount: Double,
    val categoryId: UUID?,
)

/** The owner's decision: how an entry is recorded. More than one part splits it. */
data class Approval(
    val type: TransactionType,
    val accountId: UUID,
    val toAccountId: UUID?,
    val parts: List<ApprovalPart>,
    /** Propose this title and category the next time the same merchant shows up. */
    val remember: Boolean,
)

/** Joins the two emails of one transfer into a single entry. */
internal fun pairEntries(items: Map<String, InboxItem>): List<InboxEntry> {
    // The later email of a pair points at the earlier one.
    val partnerOf = HashMap<String, String>()
    for ((id, item) in items) {
        val target = item.pairId
        if (target.isNotEmpty() && target in items && target !in partnerOf) {
            partnerOf[target] = id
        }
    }
    val partners = partnerOf.values.toSet()
    return items.filterKeys { it !in partners }.map { (id, item) ->
        val partnerId = partnerOf[id]
        InboxEntry(id = id, item = item, partnerId = partnerId, partner = partnerId?.let { items[it] })
    }
}

/** Where the transaction came from, kept in the description so the original name is not lost. */
internal fun sourceLine(entry: InboxEntry): String {
    val item = entry.outgoing
    return listOf(item.counterparty, item.method, bankName(item.bank))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
