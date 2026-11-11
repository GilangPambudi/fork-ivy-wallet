package com.ivy.rivy.inbox

import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.rivy.RivyJson
import com.ivy.rivy.sync.SyncScheduler
import com.ivy.rivy.sync.TYPE_ACCOUNT_LINK
import com.ivy.rivy.sync.TYPE_INBOX_ITEM
import com.ivy.rivy.sync.TYPE_MERCHANT_RULE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboxRepository @Inject constructor(
    private val store: RivyStore,
    private val writeTransactionDao: WriteTransactionDao,
    private val scheduler: SyncScheduler,
) {
    /** Entries that wait for a decision, newest first. */
    suspend fun pending(): List<InboxEntry> {
        val items = LinkedHashMap<String, InboxItem>()
        for ((id, data) in store.all(TYPE_INBOX_ITEM)) {
            val item = decodeItem(data) ?: continue
            if (item.status == STATUS_PENDING) items[id] = item
        }
        return pairEntries(items).sortedByDescending { parseInstant(it.item.occurredAt) ?: Instant.EPOCH }
    }

    suspend fun proposal(entry: InboxEntry): Proposal {
        val links = store.all(TYPE_ACCOUNT_LINK)
        val rule = store.all(TYPE_MERCHANT_RULE)[merchantRuleId(entry.item.counterparty)]
            ?.let { runCatching { RivyJson.decodeFromJsonElement(MerchantRule.serializer(), it) }.getOrNull() }
            ?.takeIf { entry.item.counterparty.isNotBlank() && !entry.paired }

        val type = rule?.type?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
            ?: defaultType(entry.item, entry.paired)
        val incoming = entry.incoming
        return Proposal(
            type = type,
            title = rule?.title ?: if (entry.paired) "" else proposeTitle(entry.item.counterparty),
            categoryId = rule?.categoryId?.toUuidOrNull(),
            accountId = linkedAccount(links, ownAccountKey(entry.outgoing)),
            toAccountId = if (incoming != null) {
                linkedAccount(links, ownAccountKey(incoming))
            } else {
                linkedAccount(links, destinationKey(entry.item))
            },
        )
    }

    /** Records the entry as the owner decided and remembers the choices for next time. */
    suspend fun approve(entry: InboxEntry, approval: Approval) = withContext(Dispatchers.IO) {
        val isTransfer = approval.type == TransactionType.TRANSFER
        val dateTime = parseInstant(entry.outgoing.occurredAt) ?: Instant.now()
        val transactionIds = approval.parts.map { part ->
            val transaction = TransactionEntity(
                accountId = approval.accountId,
                type = approval.type,
                amount = part.amount,
                toAccountId = if (isTransfer) approval.toAccountId else null,
                toAmount = if (isTransfer) part.amount else null,
                title = part.title.trim().ifEmpty { null },
                description = sourceLine(entry),
                dateTime = dateTime,
                categoryId = if (isTransfer) null else part.categoryId,
                id = UUID.randomUUID(),
            )
            writeTransactionDao.save(transaction)
            transaction.id.toString()
        }

        resolve(entry, STATUS_APPROVED, transactionIds)
        rememberChoices(entry, approval)
        scheduler.syncSoon(SYNC_DELAY_SECONDS)
    }

    /** Removes the entry from the inbox without recording anything. */
    suspend fun dismiss(entry: InboxEntry) {
        resolve(entry, STATUS_DISMISSED, emptyList())
        scheduler.syncSoon(SYNC_DELAY_SECONDS)
    }

    private suspend fun resolve(entry: InboxEntry, status: String, transactionIds: List<String>) {
        val stored = store.all(TYPE_INBOX_ITEM)
        val updated = LinkedHashMap<String, JsonObject>()
        for (id in listOfNotNull(entry.id, entry.partnerId)) {
            val data = stored[id] ?: continue
            updated[id] = JsonObject(
                data + mapOf(
                    "status" to JsonPrimitive(status),
                    "transaction_ids" to JsonArray(transactionIds.map { JsonPrimitive(it) }),
                    "resolved_at" to JsonPrimitive(Instant.now().toString()),
                )
            )
        }
        store.put(TYPE_INBOX_ITEM, updated)
    }

    private suspend fun rememberChoices(entry: InboxEntry, approval: Approval) {
        val links = LinkedHashMap<String, JsonObject>()
        links += accountLink(ownAccountKey(entry.outgoing), approval.accountId)
        val toAccountId = approval.toAccountId
        if (approval.type == TransactionType.TRANSFER && toAccountId != null) {
            val incoming = entry.incoming
            val key = if (incoming != null) ownAccountKey(incoming) else destinationKey(entry.item)
            links += accountLink(key, toAccountId)
        }
        store.put(TYPE_ACCOUNT_LINK, links)

        val part = approval.parts.singleOrNull()
        val counterparty = entry.item.counterparty
        if (approval.remember && part != null && counterparty.isNotBlank() && !entry.paired) {
            val rule = MerchantRule(
                counterparty = merchantKey(counterparty),
                title = part.title.trim(),
                categoryId = part.categoryId?.toString().orEmpty(),
                type = approval.type.name,
            )
            store.put(
                TYPE_MERCHANT_RULE,
                mapOf(
                    merchantRuleId(counterparty) to
                        RivyJson.encodeToJsonElement(MerchantRule.serializer(), rule).jsonObject
                )
            )
        }
    }

    private fun accountLink(key: String, accountId: UUID): Pair<String, JsonObject> =
        accountLinkId(key) to RivyJson.encodeToJsonElement(
            AccountLink.serializer(),
            AccountLink(key = key, accountId = accountId.toString())
        ).jsonObject

    private fun linkedAccount(links: Map<String, JsonObject>, key: String): UUID? =
        (links[accountLinkId(key)]?.get("account_id") as? JsonPrimitive)?.content?.toUuidOrNull()

    private fun decodeItem(data: JsonObject): InboxItem? =
        runCatching { RivyJson.decodeFromJsonElement(InboxItem.serializer(), data) }.getOrNull()

    private companion object {
        const val SYNC_DELAY_SECONDS = 5L
    }
}

private fun String.toUuidOrNull(): UUID? =
    if (isBlank()) null else runCatching { UUID.fromString(this) }.getOrNull()
