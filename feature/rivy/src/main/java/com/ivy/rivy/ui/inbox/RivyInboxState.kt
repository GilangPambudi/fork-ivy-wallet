package com.ivy.rivy.ui.inbox

import androidx.compose.runtime.Immutable
import com.ivy.base.model.TransactionType
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

/** An account or category the owner can pick. */
@Immutable
data class Choice(
    val id: UUID,
    val name: String,
)

/** One entry of the inbox as shown in the list. */
@Immutable
data class InboxCard(
    val id: String,
    val amount: String,
    val headline: String,
    val detail: String,
    /** What a tap on "Setujui" records, or empty when there is nothing to propose. */
    val proposal: String,
    val canApprove: Boolean,
    /** False for a bank email that could not be read. It can only be dismissed. */
    val readable: Boolean,
)

@Immutable
data class EditorPart(
    val title: String,
    /** Typed amount. Not used for the first part, which gets what the others leave. */
    val amountText: String,
    val categoryId: UUID?,
)

/** The form in which the owner changes or splits an entry before approving it. */
@Immutable
data class EditorState(
    val entryId: String,
    val summary: String,
    val currency: String,
    val total: Double,
    val type: TransactionType,
    val accountId: UUID?,
    val toAccountId: UUID?,
    val parts: ImmutableList<EditorPart>,
    /** Amount of the first part: the total minus all other parts. */
    val firstPartAmount: String,
    val canRemember: Boolean,
    val remember: Boolean,
    val error: String,
)

@Immutable
data class RivyInboxState(
    val loading: Boolean,
    val busy: Boolean,
    val cards: ImmutableList<InboxCard>,
    val accounts: ImmutableList<Choice>,
    val categories: ImmutableList<Choice>,
    val editor: EditorState?,
    val message: String,
)

sealed interface RivyInboxEvent {
    data object Refresh : RivyInboxEvent
    data class Approve(val id: String) : RivyInboxEvent
    data class Edit(val id: String) : RivyInboxEvent
    data class Dismiss(val id: String) : RivyInboxEvent

    data object CloseEditor : RivyInboxEvent
    data class SetType(val type: TransactionType) : RivyInboxEvent
    data class SetAccount(val id: UUID) : RivyInboxEvent
    data class SetToAccount(val id: UUID) : RivyInboxEvent
    data class SetPartTitle(val index: Int, val title: String) : RivyInboxEvent
    data class SetPartAmount(val index: Int, val amount: String) : RivyInboxEvent
    data class SetPartCategory(val index: Int, val id: UUID?) : RivyInboxEvent
    data object AddPart : RivyInboxEvent
    data class RemovePart(val index: Int) : RivyInboxEvent
    data class SetRemember(val remember: Boolean) : RivyInboxEvent
    data object Save : RivyInboxEvent
}
