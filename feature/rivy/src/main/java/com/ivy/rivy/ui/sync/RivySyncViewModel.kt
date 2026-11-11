package com.ivy.rivy.ui.sync

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.rivy.sync.GoogleSignIn
import com.ivy.rivy.sync.SyncApi
import com.ivy.rivy.sync.SyncEngine
import com.ivy.rivy.sync.SyncOutcome
import com.ivy.rivy.sync.SyncPrefs
import com.ivy.rivy.sync.SyncScheduler
import com.ivy.rivy.sync.normalizeServerUrl
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class SyncDialog {
    /** The server already has data: the owner chooses what happens to the data on this device. */
    FirstSync,

    /** A lot is gone from this device: the owner chooses whether it is removed everywhere. */
    MassDelete,
}

data class RivySyncState(
    val connected: Boolean,
    val serverUrl: String,
    val email: String,
    val lastSync: String,
    val busy: Boolean,
    val message: String,
    val error: String,
    val dialog: SyncDialog?,
    val massDeleteCount: Int,
)

sealed interface RivySyncEvent {
    /** @param activityContext the Activity the Google account picker is shown on */
    data class Connect(val serverUrl: String, val activityContext: Context) : RivySyncEvent
    data object SyncNow : RivySyncEvent
    data object Disconnect : RivySyncEvent
    data object UseServerData : RivySyncEvent
    data object MergeData : RivySyncEvent
    data object DeleteEverywhere : RivySyncEvent
    data object RestoreFromServer : RivySyncEvent
    data object CloseDialog : RivySyncEvent
}

@Stable
@HiltViewModel
class RivySyncViewModel @Inject constructor(
    private val api: SyncApi,
    private val prefs: SyncPrefs,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val googleSignIn: GoogleSignIn,
) : ComposeViewModel<RivySyncState, RivySyncEvent>() {

    private val connected = mutableStateOf(prefs.connected)
    private val serverUrl = mutableStateOf(prefs.serverUrl)
    private val email = mutableStateOf(prefs.email)
    private val lastSyncAt = mutableStateOf(prefs.lastSyncAt)
    private val busy = mutableStateOf(false)
    private val message = mutableStateOf("")
    private val error = mutableStateOf(prefs.lastError)
    private val dialog = mutableStateOf<SyncDialog?>(null)
    private val massDeleteCount = mutableStateOf(0)

    @Composable
    override fun uiState(): RivySyncState = RivySyncState(
        connected = connected.value,
        serverUrl = serverUrl.value,
        email = email.value,
        lastSync = formatTime(lastSyncAt.value),
        busy = busy.value,
        message = message.value,
        error = error.value,
        dialog = dialog.value,
        massDeleteCount = massDeleteCount.value,
    )

    override fun onEvent(event: RivySyncEvent) {
        when (event) {
            is RivySyncEvent.Connect -> connect(event.serverUrl, event.activityContext)
            RivySyncEvent.SyncNow -> runSync { engine.sync() }
            RivySyncEvent.Disconnect -> disconnect()
            RivySyncEvent.UseServerData -> firstSync { engine.replaceLocalWithServer() }
            RivySyncEvent.MergeData -> firstSync { engine.mergeWithServer() }
            RivySyncEvent.DeleteEverywhere -> runSync { engine.sync(allowMassDelete = true) }
            RivySyncEvent.RestoreFromServer -> runSync { engine.mergeWithServer() }
            RivySyncEvent.CloseDialog -> dialog.value = null
        }
    }

    private fun connect(rawUrl: String, activityContext: Context) = launchBusy {
        val url = normalizeServerUrl(rawUrl)
        if (url == null) {
            error.value = "Isi alamat server dulu, contoh: https://rivy.contoh.com"
            return@launchBusy
        }
        val config = api.config(url)
        if (config.googleClientId.isBlank()) {
            error.value = "Server belum diatur untuk masuk dengan Google."
            return@launchBusy
        }
        val idToken = googleSignIn.idToken(activityContext, config.googleClientId)
        val session = api.signIn(url, idToken, Build.MODEL ?: "Android")

        prefs.serverUrl = url
        prefs.token = session.token
        prefs.email = session.email
        prefs.lastSyncAt = 0
        prefs.lastError = ""
        engine.reset()
        refresh()

        if (engine.serverHasData()) {
            dialog.value = SyncDialog.FirstSync
        } else {
            show(engine.sync())
            scheduler.start()
        }
    }

    private fun firstSync(sync: suspend () -> SyncOutcome) {
        dialog.value = null
        launchBusy {
            show(sync())
            scheduler.start()
        }
    }

    private fun runSync(sync: suspend () -> SyncOutcome) {
        dialog.value = null
        launchBusy { show(sync()) }
    }

    private fun disconnect() = launchBusy {
        scheduler.stop()
        val url = prefs.serverUrl
        val token = prefs.token
        prefs.clearSession()
        dialog.value = null
        message.value = "Sambungan diputus. Data di HP ini tetap ada."
        refresh()
        // Best effort: the session is gone on this device either way.
        runCatching { api.signOut(url, token) }
    }

    private fun show(outcome: SyncOutcome) {
        when (outcome) {
            is SyncOutcome.Done ->
                message.value = "Selesai. Terkirim ${outcome.pushed}, diterima ${outcome.pulled}."

            is SyncOutcome.MassDelete -> {
                massDeleteCount.value = outcome.count
                dialog.value = SyncDialog.MassDelete
            }

            is SyncOutcome.Failed -> error.value = outcome.message
            SyncOutcome.SignedOut -> error.value = "Sesi berakhir. Sambungkan lagi."
            SyncOutcome.NotConnected -> error.value = "Belum tersambung ke server."
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = ""
        error.value = ""
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Rivy: sync screen action failed")
                error.value = e.message ?: "Gagal: ${e.javaClass.simpleName}"
            } finally {
                busy.value = false
                refresh()
            }
        }
    }

    private fun refresh() {
        connected.value = prefs.connected
        serverUrl.value = prefs.serverUrl
        email.value = prefs.email
        lastSyncAt.value = prefs.lastSyncAt
    }

    private fun formatTime(epochMillis: Long): String =
        if (epochMillis <= 0) {
            "Belum pernah"
        } else {
            timeFormat.format(Instant.ofEpochMilli(epochMillis))
        }

    private companion object {
        val timeFormat: DateTimeFormatter = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm", Locale("id", "ID"))
            .withZone(ZoneId.systemDefault())
    }
}
