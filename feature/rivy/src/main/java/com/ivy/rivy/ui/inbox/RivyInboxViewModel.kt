package com.ivy.rivy.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.rivy.inbox.Approval
import com.ivy.rivy.inbox.ApprovalPart
import com.ivy.rivy.inbox.InboxEntry
import com.ivy.rivy.inbox.InboxRepository
import com.ivy.rivy.inbox.KIND_INCOMING
import com.ivy.rivy.inbox.KIND_PAYMENT
import com.ivy.rivy.inbox.KIND_POCKET_MOVE
import com.ivy.rivy.inbox.KIND_TOPUP
import com.ivy.rivy.inbox.KIND_TRANSFER
import com.ivy.rivy.inbox.Proposal
import com.ivy.rivy.inbox.RivyStore
import com.ivy.rivy.inbox.bankName
import com.ivy.rivy.inbox.formatAmount
import com.ivy.rivy.inbox.parseAmountInput
import com.ivy.rivy.inbox.parseInstant
import com.ivy.rivy.inbox.splitRemainder
import com.ivy.rivy.sync.SyncEngine
import com.ivy.rivy.sync.SyncOutcome
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@Stable
@HiltViewModel
@Suppress("TooManyFunctions")
class RivyInboxViewModel @Inject constructor(
    private val inbox: InboxRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val engine: SyncEngine,
    private val store: RivyStore,
) : ComposeViewModel<RivyInboxState, RivyInboxEvent>() {

    private val loading = mutableStateOf(true)
    private val busy = mutableStateOf(false)
    private val cards = mutableStateOf<ImmutableList<InboxCard>>(persistentListOf())
    private val accounts = mutableStateOf<ImmutableList<Choice>>(persistentListOf())
    private val categories = mutableStateOf<ImmutableList<Choice>>(persistentListOf())
    private val editor = mutableStateOf<EditorState?>(null)
    private val message = mutableStateOf("")

    private var entries: Map<String, InboxEntry> = emptyMap()
    private var proposals: Map<String, Proposal> = emptyMap()

    @Composable
    override fun uiState(): RivyInboxState {
        LaunchedEffect(Unit) {
            // Reloads right away and again whenever a sync brings something new.
            store.version.collect { load() }
        }

        return RivyInboxState(
            loading = loading.value,
            busy = busy.value,
            cards = cards.value,
            accounts = accounts.value,
            categories = categories.value,
            editor = editor.value,
            message = message.value,
        )
    }

    override fun onEvent(event: RivyInboxEvent) {
        when (event) {
            RivyInboxEvent.Refresh -> refresh()
            is RivyInboxEvent.Approve -> approve(event.id)
            is RivyInboxEvent.Edit -> edit(event.id)
            is RivyInboxEvent.Dismiss -> dismiss(event.id)
            RivyInboxEvent.CloseEditor -> editor.value = null
            is RivyInboxEvent.SetType -> updateEditor { it.copy(type = event.type) }
            is RivyInboxEvent.SetAccount -> updateEditor { it.copy(accountId = event.id) }
            is RivyInboxEvent.SetToAccount -> updateEditor { it.copy(toAccountId = event.id) }
            is RivyInboxEvent.SetPartTitle -> updatePart(event.index) { it.copy(title = event.title) }
            is RivyInboxEvent.SetPartAmount -> updatePart(event.index) { it.copy(amountText = event.amount) }
            is RivyInboxEvent.SetPartCategory -> updatePart(event.index) { it.copy(categoryId = event.id) }
            RivyInboxEvent.AddPart -> updateEditor {
                it.copy(parts = (it.parts + EditorPart(title = "", amountText = "", categoryId = null)).toImmutableList())
            }

            is RivyInboxEvent.RemovePart -> updateEditor {
                if (event.index > 0 && event.index < it.parts.size) {
                    it.copy(parts = it.parts.filterIndexed { index, _ -> index != event.index }.toImmutableList())
                } else {
                    it
                }
            }

            is RivyInboxEvent.SetRemember -> updateEditor { it.copy(remember = event.remember) }
            RivyInboxEvent.Save -> save()
        }
    }

    private suspend fun load() {
        val accountList = accountRepository.findAll()
        val categoryList = categoryRepository.findAll()
        val accountNames = accountList.associate { it.id.value to it.name.value }
        val categoryNames = categoryList.associate { it.id.value to it.name.value }

        val pending = inbox.pending()
        val newProposals = HashMap<String, Proposal>()
        val newCards = ArrayList<InboxCard>()
        for (entry in pending) {
            val raw = inbox.proposal(entry)
            // An account or category that was deleted since is not proposed.
            val proposal = raw.copy(
                accountId = raw.accountId?.takeIf { it in accountNames },
                toAccountId = raw.toAccountId?.takeIf { it in accountNames },
                categoryId = raw.categoryId?.takeIf { it in categoryNames },
            )
            newProposals[entry.id] = proposal
            newCards += card(entry, proposal, accountNames, categoryNames)
        }

        entries = pending.associateBy { it.id }
        proposals = newProposals
        accounts.value = accountList.map { Choice(it.id.value, it.name.value) }.toImmutableList()
        categories.value = categoryList.map { Choice(it.id.value, it.name.value) }.toImmutableList()
        cards.value = newCards.toImmutableList()
        loading.value = false
    }

    private fun card(
        entry: InboxEntry,
        proposal: Proposal,
        accountNames: Map<UUID, String>,
        categoryNames: Map<UUID, String>,
    ): InboxCard {
        val item = entry.item
        if (!item.parsed) {
            return InboxCard(
                id = entry.id,
                amount = "Email tidak terbaca",
                headline = listOf(bankName(item.bank), item.subject).filter { it.isNotBlank() }.joinToString(" · "),
                detail = item.note,
                proposal = "",
                canApprove = false,
                readable = false,
            )
        }

        val incoming = entry.incoming
        val headline = if (incoming != null) {
            "Transfer ${bankName(entry.outgoing.bank)} ke ${bankName(incoming.bank)}"
        } else {
            item.counterparty.ifBlank { kindLabel(item.kind) }
        }
        val detail = listOf(kindLabel(item.kind), bankName(item.bank), item.method, formatWhen(item.occurredAt))
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val account = proposal.accountId?.let { accountNames[it] }
        val toAccount = proposal.toAccountId?.let { accountNames[it] }
        val isTransfer = proposal.type == TransactionType.TRANSFER
        val canApprove = if (isTransfer) {
            account != null && toAccount != null && proposal.accountId != proposal.toAccountId
        } else {
            account != null
        }
        val proposalText = when {
            !canApprove -> ""
            isTransfer -> "Transfer · $account ke $toAccount"
            else -> listOf(
                typeLabel(proposal.type),
                proposal.title,
                proposal.categoryId?.let { categoryNames[it] }.orEmpty(),
                account.orEmpty(),
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }

        return InboxCard(
            id = entry.id,
            amount = formatAmount(item.amount, item.currency),
            headline = headline,
            detail = detail,
            proposal = proposalText,
            canApprove = canApprove,
            readable = true,
        )
    }

    private fun refresh() = launchBusy {
        when (val outcome = engine.sync()) {
            is SyncOutcome.Done -> message.value = ""
            is SyncOutcome.Failed -> message.value = outcome.message
            is SyncOutcome.MassDelete -> message.value = "Sinkronisasi ditahan. Buka Pengaturan, Sinkronisasi."
            SyncOutcome.SignedOut -> message.value = "Sesi berakhir. Sambungkan lagi di Pengaturan, Sinkronisasi."
            SyncOutcome.NotConnected -> message.value = "Belum tersambung. Buka Pengaturan, Sinkronisasi."
        }
        load()
    }

    private fun approve(id: String) {
        val entry = entries[id] ?: return
        val proposal = proposals[id] ?: return
        val accountId = proposal.accountId ?: return
        val isTransfer = proposal.type == TransactionType.TRANSFER
        if (isTransfer && (proposal.toAccountId == null || proposal.toAccountId == accountId)) return

        launchBusy {
            inbox.approve(
                entry,
                Approval(
                    type = proposal.type,
                    accountId = accountId,
                    toAccountId = proposal.toAccountId,
                    parts = listOf(ApprovalPart(proposal.title, entry.item.amount, proposal.categoryId)),
                    // Nothing was changed, so there is nothing new to remember.
                    remember = false,
                )
            )
            message.value = "Tercatat."
        }
    }

    private fun dismiss(id: String) {
        val entry = entries[id] ?: return
        launchBusy {
            inbox.dismiss(entry)
            message.value = "Diabaikan, tidak dicatat."
        }
    }

    private fun edit(id: String) {
        val entry = entries[id] ?: return
        val proposal = proposals[id] ?: return
        val item = entry.item
        if (!item.parsed) return

        val canRemember = item.counterparty.isNotBlank() && !entry.paired
        val summary = listOf(
            formatAmount(item.amount, item.currency),
            item.counterparty,
            bankName(item.bank),
            formatWhen(item.occurredAt),
        ).filter { it.isNotBlank() }.joinToString(" · ")

        message.value = ""
        editor.value = withFirstPartAmount(
            EditorState(
                entryId = id,
                summary = summary,
                currency = item.currency,
                total = item.amount,
                type = proposal.type,
                accountId = proposal.accountId,
                toAccountId = proposal.toAccountId,
                parts = persistentListOf(
                    EditorPart(title = proposal.title, amountText = "", categoryId = proposal.categoryId)
                ),
                firstPartAmount = "",
                canRemember = canRemember,
                remember = canRemember,
                error = "",
            )
        )
    }

    private fun save() {
        val state = editor.value ?: return
        val entry = entries[state.entryId] ?: return
        val accountId = state.accountId
        if (accountId == null) {
            showEditorError("Pilih akun dulu.")
            return
        }
        val isTransfer = state.type == TransactionType.TRANSFER
        val toAccountId = state.toAccountId
        if (isTransfer && (toAccountId == null || toAccountId == accountId)) {
            showEditorError("Pilih akun tujuan yang berbeda dari akun asal.")
            return
        }

        val typed = state.parts.drop(1).map { parseAmountInput(it.amountText) }
        val others = typed.filterNotNull().filter { it > 0.0 }
        if (others.size != typed.size) {
            showEditorError("Isi jumlah tiap bagian dengan angka lebih dari 0.")
            return
        }
        val first = splitRemainder(state.total, others)
        if (first <= 0.0) {
            showEditorError("Jumlah bagian-bagiannya melebihi total transaksi.")
            return
        }

        val parts = state.parts.mapIndexed { index, part ->
            ApprovalPart(
                title = part.title,
                amount = if (index == 0) first else others[index - 1],
                categoryId = part.categoryId,
            )
        }
        launchBusy {
            inbox.approve(
                entry,
                Approval(
                    type = state.type,
                    accountId = accountId,
                    toAccountId = if (isTransfer) toAccountId else null,
                    parts = parts,
                    remember = state.remember && state.canRemember,
                )
            )
            editor.value = null
            message.value = "Tercatat."
        }
    }

    private fun showEditorError(text: String) {
        editor.value = editor.value?.copy(error = text)
    }

    private fun updateEditor(change: (EditorState) -> EditorState) {
        val state = editor.value ?: return
        editor.value = withFirstPartAmount(change(state).copy(error = ""))
    }

    private fun updatePart(index: Int, change: (EditorPart) -> EditorPart) = updateEditor { state ->
        if (index in state.parts.indices) {
            state.copy(
                parts = state.parts.mapIndexed { i, part -> if (i == index) change(part) else part }.toImmutableList()
            )
        } else {
            state
        }
    }

    private fun withFirstPartAmount(state: EditorState): EditorState {
        val others = state.parts.drop(1).map { parseAmountInput(it.amountText) ?: 0.0 }
        return state.copy(
            firstPartAmount = formatAmount(splitRemainder(state.total, others), state.currency)
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Rivy: inbox action failed")
                message.value = e.message ?: "Gagal: ${e.javaClass.simpleName}"
            } finally {
                busy.value = false
            }
        }
    }

    private fun formatWhen(occurredAt: String): String =
        parseInstant(occurredAt)?.let { whenFormat.format(it) }.orEmpty()

    private fun kindLabel(kind: String): String = when (kind) {
        KIND_PAYMENT -> "Pembayaran"
        KIND_TRANSFER -> "Transfer keluar"
        KIND_TOPUP -> "Isi saldo"
        KIND_INCOMING -> "Uang masuk"
        KIND_POCKET_MOVE -> "Pindah kantong"
        else -> "Transaksi"
    }

    private companion object {
        val whenFormat: DateTimeFormatter = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm", Locale("id", "ID"))
            .withZone(ZoneId.systemDefault())
    }
}

internal fun typeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "Pengeluaran"
    TransactionType.INCOME -> "Pemasukan"
    TransactionType.TRANSFER -> "Transfer"
}
