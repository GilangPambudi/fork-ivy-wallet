package com.ivy.rivy.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel

@Composable
fun RivySyncScreenImpl() {
    val viewModel: RivySyncViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()

    RivySyncUi(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RivySyncUi(
    uiState: RivySyncState,
    onEvent: (RivySyncEvent) -> Unit,
) {
    val nav = navigation()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Sinkronisasi") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        },
        content = { padding ->
            Content(padding = padding, uiState = uiState, onEvent = onEvent)
        }
    )

    when (uiState.dialog) {
        SyncDialog.FirstSync -> FirstSyncDialog(onEvent = onEvent)
        SyncDialog.MassDelete -> MassDeleteDialog(count = uiState.massDeleteCount, onEvent = onEvent)
        null -> Unit
    }
}

@Composable
private fun Content(
    padding: PaddingValues,
    uiState: RivySyncState,
    onEvent: (RivySyncEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (uiState.connected) {
            Connected(uiState = uiState, onEvent = onEvent)
        } else {
            NotConnected(uiState = uiState, onEvent = onEvent)
        }

        if (uiState.message.isNotEmpty()) {
            Text(text = uiState.message, style = MaterialTheme.typography.bodyMedium)
        }
        if (uiState.error.isNotEmpty()) {
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun NotConnected(
    uiState: RivySyncState,
    onEvent: (RivySyncEvent) -> Unit,
) {
    val context = LocalContext.current
    var serverUrl by rememberSaveable { mutableStateOf(uiState.serverUrl) }

    Text(
        text = "Sambungkan HP ini ke server Rivy supaya datanya sama di semua HP. " +
            "Cukup masuk dengan akun Google, tanpa kata sandi.",
        style = MaterialTheme.typography.bodyMedium
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text(text = "Alamat server") },
        placeholder = { Text(text = "https://rivy.contoh.com") },
        singleLine = true,
        enabled = !uiState.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.busy,
        onClick = { onEvent(RivySyncEvent.Connect(serverUrl, context)) }
    ) {
        Text(text = "Sambungkan dengan Google")
    }
}

@Composable
private fun Connected(
    uiState: RivySyncState,
    onEvent: (RivySyncEvent) -> Unit,
) {
    Field(label = "Server", value = uiState.serverUrl)
    Field(label = "Akun", value = uiState.email)
    Field(label = "Terakhir sinkron", value = uiState.lastSync)

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.busy,
        onClick = { onEvent(RivySyncEvent.SyncNow) }
    ) {
        Text(text = "Sinkronkan sekarang")
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.busy,
        onClick = { onEvent(RivySyncEvent.Disconnect) }
    ) {
        Text(text = "Putuskan sambungan")
    }
    Text(
        text = "Sinkronisasi juga jalan sendiri tiap 15 menit dan setiap aplikasi dibuka.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FirstSyncDialog(onEvent: (RivySyncEvent) -> Unit) {
    AlertDialog(
        // Closing without choosing would leave the device half connected.
        onDismissRequest = {},
        title = { Text(text = "Server sudah punya data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pakai data server: data di HP ini dihapus lalu diganti data dari server. " +
                        "Pilih ini untuk HP baru."
                )
                Text(
                    text = "Gabungkan: data di HP ini dan data di server disatukan. " +
                        "Akun atau kategori yang sama bisa jadi dobel."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(RivySyncEvent.UseServerData) }) {
                Text(text = "Pakai data server")
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { onEvent(RivySyncEvent.MergeData) }) {
                    Text(text = "Gabungkan")
                }
                TextButton(onClick = { onEvent(RivySyncEvent.Disconnect) }) {
                    Text(text = "Batal")
                }
            }
        }
    )
}

@Composable
private fun MassDeleteDialog(
    count: Int,
    onEvent: (RivySyncEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(RivySyncEvent.CloseDialog) },
        title = { Text(text = "$count data hilang dari HP ini") },
        text = {
            Text(
                text = "Sinkronisasi ditahan supaya data itu tidak ikut terhapus di server dan HP lain. " +
                    "Kalau memang sengaja dihapus, pilih Hapus di semua. " +
                    "Kalau tidak, pilih Pulihkan untuk mengambilnya lagi dari server."
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(RivySyncEvent.RestoreFromServer) }) {
                Text(text = "Pulihkan")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(RivySyncEvent.DeleteEverywhere) }) {
                Text(text = "Hapus di semua")
            }
        }
    )
}
