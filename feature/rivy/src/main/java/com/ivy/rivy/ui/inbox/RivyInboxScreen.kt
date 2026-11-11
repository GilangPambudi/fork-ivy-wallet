package com.ivy.rivy.ui.inbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.base.model.TransactionType
import com.ivy.navigation.RivyInboxScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

@Composable
fun RivyInboxScreenImpl() {
    val viewModel: RivyInboxViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()

    AskForNotifications()
    RivyInboxUi(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

/** New bank transactions are announced with a notification, which Android 13+ asks consent for. */
@Composable
private fun AskForNotifications() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("DEPRECATION")
private fun RivyInboxUi(
    uiState: RivyInboxState,
    onEvent: (RivyInboxEvent) -> Unit,
) {
    val nav = navigation()
    val editor = uiState.editor

    // The system back button closes the form first, then leaves the screen.
    val editorOpen = editor != null
    DisposableEffect(editorOpen) {
        nav.onBackPressed[RivyInboxScreen] = {
            if (editorOpen) onEvent(RivyInboxEvent.CloseEditor)
            editorOpen
        }
        onDispose {
            nav.onBackPressed.remove(RivyInboxScreen)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (editor != null) "Ubah transaksi" else "Kotak masuk transaksi") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (editor != null) onEvent(RivyInboxEvent.CloseEditor) else nav.back()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    if (editor == null) {
                        IconButton(
                            enabled = !uiState.busy,
                            onClick = { onEvent(RivyInboxEvent.Refresh) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Periksa transaksi baru"
                            )
                        }
                    }
                }
            )
        },
        content = { padding ->
            if (editor != null) {
                Editor(padding = padding, uiState = uiState, editor = editor, onEvent = onEvent)
            } else {
                InboxList(padding = padding, uiState = uiState, onEvent = onEvent)
            }
        }
    )
}

@Composable
private fun InboxList(
    padding: PaddingValues,
    uiState: RivyInboxState,
    onEvent: (RivyInboxEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.busy) {
            item(key = "progress") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (uiState.message.isNotEmpty()) {
            item(key = "message") {
                Text(text = uiState.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!uiState.loading && uiState.cards.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Tidak ada transaksi yang menunggu persetujuan.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        items(items = uiState.cards, key = { it.id }) { card ->
            InboxCardItem(card = card, enabled = !uiState.busy, onEvent = onEvent)
        }
    }
}

@Composable
private fun InboxCardItem(
    card: InboxCard,
    enabled: Boolean,
    onEvent: (RivyInboxEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = card.amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (card.headline.isNotEmpty()) {
                Text(text = card.headline, style = MaterialTheme.typography.bodyLarge)
            }
            if (card.detail.isNotEmpty()) {
                Text(text = card.detail, style = MaterialTheme.typography.bodySmall)
            }
            if (card.readable) {
                Text(
                    text = if (card.canApprove) "Usulan: ${card.proposal}" else "Akun belum dikenali. Pilih lewat Ubah.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (card.readable) {
                    Button(
                        enabled = enabled && card.canApprove,
                        onClick = { onEvent(RivyInboxEvent.Approve(card.id)) }
                    ) {
                        Text(text = "Setujui")
                    }
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { onEvent(RivyInboxEvent.Edit(card.id)) }
                    ) {
                        Text(text = "Ubah")
                    }
                }
                TextButton(
                    enabled = enabled,
                    onClick = { onEvent(RivyInboxEvent.Dismiss(card.id)) }
                ) {
                    Text(text = "Abaikan")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(
    padding: PaddingValues,
    uiState: RivyInboxState,
    editor: EditorState,
    onEvent: (RivyInboxEvent) -> Unit,
) {
    val isTransfer = editor.type == TransactionType.TRANSFER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = editor.summary, style = MaterialTheme.typography.bodyLarge)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionType.entries.forEach { type ->
                FilterChip(
                    selected = editor.type == type,
                    onClick = { onEvent(RivyInboxEvent.SetType(type)) },
                    label = { Text(text = typeLabel(type)) }
                )
            }
        }

        Picker(
            label = if (isTransfer) "Dari akun" else "Akun",
            choices = uiState.accounts,
            selected = editor.accountId,
            noneLabel = null,
            onSelect = { id -> if (id != null) onEvent(RivyInboxEvent.SetAccount(id)) }
        )
        if (isTransfer) {
            Picker(
                label = "Ke akun",
                choices = uiState.accounts,
                selected = editor.toAccountId,
                noneLabel = null,
                onSelect = { id -> if (id != null) onEvent(RivyInboxEvent.SetToAccount(id)) }
            )
        }

        editor.parts.forEachIndexed { index, part ->
            PartCard(
                index = index,
                part = part,
                editor = editor,
                categories = uiState.categories,
                onEvent = onEvent
            )
        }

        OutlinedButton(onClick = { onEvent(RivyInboxEvent.AddPart) }) {
            Text(text = "Pecah")
        }

        if (editor.canRemember && editor.parts.size == 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = editor.remember,
                    onCheckedChange = { onEvent(RivyInboxEvent.SetRemember(it)) }
                )
                Text(
                    text = "Usulkan judul dan kategori ini lagi untuk tempat yang sama",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (editor.error.isNotEmpty()) {
            Text(
                text = editor.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.busy,
            onClick = { onEvent(RivyInboxEvent.Save) }
        ) {
            Text(text = "Setujui dan catat")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PartCard(
    index: Int,
    part: EditorPart,
    editor: EditorState,
    categories: ImmutableList<Choice>,
    onEvent: (RivyInboxEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editor.parts.size > 1) {
                Text(
                    text = "Bagian ${index + 1}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = part.title,
                onValueChange = { onEvent(RivyInboxEvent.SetPartTitle(index, it)) },
                label = { Text(text = "Judul") },
                singleLine = true
            )
            if (index == 0) {
                Text(
                    text = if (editor.parts.size > 1) {
                        "Jumlah (sisa dari total): ${editor.firstPartAmount}"
                    } else {
                        "Jumlah: ${editor.firstPartAmount}"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = part.amountText,
                    onValueChange = { onEvent(RivyInboxEvent.SetPartAmount(index, it)) },
                    label = { Text(text = "Jumlah") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            if (editor.type != TransactionType.TRANSFER) {
                Picker(
                    label = "Kategori",
                    choices = categories,
                    selected = part.categoryId,
                    noneLabel = "Tanpa kategori",
                    onSelect = { id -> onEvent(RivyInboxEvent.SetPartCategory(index, id)) }
                )
            }
            if (index > 0) {
                TextButton(onClick = { onEvent(RivyInboxEvent.RemovePart(index)) }) {
                    Text(text = "Hapus bagian ini")
                }
            }
        }
    }
}

/**
 * A button that opens a list to pick one of [choices].
 *
 * @param noneLabel when not null, the list starts with an entry of this name that picks nothing
 */
@Composable
private fun Picker(
    label: String,
    choices: ImmutableList<Choice>,
    selected: UUID?,
    noneLabel: String?,
    onSelect: (UUID?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = choices.firstOrNull { it.id == selected }?.name ?: noneLabel ?: "Pilih"

    Box {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        ) {
            Text(text = "$label: $selectedName")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (noneLabel != null) {
                DropdownMenuItem(
                    text = { Text(text = noneLabel) },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
            }
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(text = choice.name) },
                    onClick = {
                        expanded = false
                        onSelect(choice.id)
                    }
                )
            }
        }
    }
}
