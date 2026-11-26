package com.ivy.legacy.driveexport

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.room.InvalidationTracker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.file.FileSystem
import com.ivy.data.model.Account
import com.ivy.data.model.Transaction
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import com.ivy.legacy.data.model.Month
import com.ivy.legacy.data.model.TimePeriod
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DriveExportState(
    val connected: Boolean,
    val running: Boolean,
    val lastSuccess: Instant?,
    val lastError: String?,
)

@Singleton
class DriveExport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: IvyRoomDatabase,
    private val sharedPrefs: SharedPrefs,
    private val accountRepository: AccountRepository,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val fileSystem: FileSystem,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val _state = MutableStateFlow(readState(running = false))
    val state: StateFlow<DriveExportState> = _state.asStateFlow()

    fun fileUri(): Uri? = uri(KEY_URI)

    private fun recentFileUri(): Uri? = uri(KEY_URI_RECENT)

    private fun uri(key: String): Uri? = sharedPrefs.getString(key, null)?.let(Uri::parse)

    fun lastSuccess(): Instant? = sharedPrefs.getLong(KEY_LAST_SUCCESS, 0L)
        .takeIf { it > 0L }
        ?.let(Instant::ofEpochMilli)

    fun lastError(): String? = sharedPrefs.getString(KEY_LAST_ERROR, null)

    private fun readState(running: Boolean) = DriveExportState(
        connected = fileUri() != null,
        running = running,
        lastSuccess = lastSuccess(),
        lastError = lastError(),
    )

    fun start() {
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(TABLE_TRANSACTIONS, TABLE_ACCOUNTS) {
                override fun onInvalidated(tables: Set<String>) {
                    schedule()
                }
            }
        )
        if (fileUri() != null) scheduleDaily()
    }

    fun connect(uri: Uri) {
        takePermission(uri)
        sharedPrefs.putString(KEY_URI, uri.toString())
        sharedPrefs.remove(KEY_LAST_SUCCESS)
        sharedPrefs.remove(KEY_LAST_ERROR)
        _state.value = readState(running = false)
        scheduleDaily()
    }

    fun connectRecent(uri: Uri) {
        takePermission(uri)
        sharedPrefs.putString(KEY_URI_RECENT, uri.toString())
    }

    private fun takePermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, URI_FLAGS)
    }

    fun disconnect() {
        listOfNotNull(fileUri(), recentFileUri()).forEach { uri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, URI_FLAGS) }
        }
        listOf(KEY_URI, KEY_URI_RECENT, KEY_LAST_SUCCESS, KEY_LAST_ERROR).forEach(sharedPrefs::remove)
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(DAILY_WORK_NAME)
        _state.value = readState(running = false)
    }

    fun schedule(delayMinutes: Long = 1L) {
        if (fileUri() == null) return
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DriveExportWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
        )
    }

    private fun scheduleDaily() {
        workManager.enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveExportWorker>(1L, TimeUnit.DAYS).build()
        )
    }

    suspend fun export(): Boolean {
        val uri = fileUri() ?: return true
        _state.value = _state.value.copy(running = true)
        val now = timeProvider.utcNow()
        val result = runCatching {
            val balances = accountRepository.findAll().map { account ->
                account to calcAccBalanceAct(CalcAccBalanceAct.Input(account)).balance.toDouble()
            }
            write(uri, balances, now) { findAll() }
            recentFileUri()?.let { recentUri ->
                val from = recentFrom()
                write(recentUri, balances, now) { findAll().filter { it.time >= from } }
            }
        }
        result.onSuccess {
            sharedPrefs.putLong(KEY_LAST_SUCCESS, now.toEpochMilli())
            sharedPrefs.remove(KEY_LAST_ERROR)
        }.onFailure {
            sharedPrefs.putString(KEY_LAST_ERROR, it.message ?: it.javaClass.simpleName)
        }
        _state.value = readState(running = false)
        return result.isSuccess
    }

    private suspend fun write(
        uri: Uri,
        balances: List<Pair<Account, Double>>,
        now: Instant,
        scope: suspend TransactionRepository.() -> List<Transaction>,
    ) {
        val csv = exportCsvUseCase.exportCsv(
            plainNumbers = true,
            balances = balances,
            balancesTime = now,
            exportScope = scope,
        )
        fileSystem.writeToFile(uri, csv, charset = Charsets.UTF_8, mode = "wt")
            .onLeft { throw it.e }
    }

    private fun recentFrom(): Instant {
        val startDay = sharedPrefs.getInt(SharedPrefs.START_DATE_OF_MONTH, 1)
        val current = TimePeriod.currentMonth(startDay)
        val date = current.month!!.toDate().withYear(current.year!!).minusMonths(RECENT_MONTHS - 1L)
        return TimePeriod(month = Month.fromMonthValue(date.monthValue), year = date.year)
            .toRange(startDay, timeConverter, timeProvider)
            .from()
    }

    companion object {
        private const val KEY_URI = "drive_export_uri"
        private const val KEY_URI_RECENT = "drive_export_recent_uri"
        private const val KEY_LAST_SUCCESS = "drive_export_last_success"
        private const val KEY_LAST_ERROR = "drive_export_last_error"
        private const val WORK_NAME = "drive_export"
        private const val DAILY_WORK_NAME = "drive_export_daily"
        private const val TABLE_TRANSACTIONS = "transactions"
        private const val TABLE_ACCOUNTS = "accounts"
        private const val RECENT_MONTHS = 3
        private const val URI_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

@HiltWorker
class DriveExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveExport: DriveExport,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        if (driveExport.export()) Result.success() else Result.failure()
}
