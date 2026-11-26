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
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.file.FileSystem
import com.ivy.data.repository.AccountRepository
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun fileUri(): Uri? = sharedPrefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun lastSuccess(): Instant? = sharedPrefs.getLong(KEY_LAST_SUCCESS, 0L)
        .takeIf { it > 0L }
        ?.let(Instant::ofEpochMilli)

    fun lastError(): String? = sharedPrefs.getString(KEY_LAST_ERROR, null)

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
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        sharedPrefs.putString(KEY_URI, uri.toString())
        sharedPrefs.remove(KEY_LAST_SUCCESS)
        sharedPrefs.remove(KEY_LAST_ERROR)
        scheduleDaily()
        schedule(delayMinutes = 0L)
    }

    fun disconnect() {
        val uri = fileUri()
        sharedPrefs.remove(KEY_URI)
        sharedPrefs.remove(KEY_LAST_SUCCESS)
        sharedPrefs.remove(KEY_LAST_ERROR)
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(DAILY_WORK_NAME)
        if (uri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
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
        val now = timeProvider.utcNow()
        val result = runCatching {
            val balances = accountRepository.findAll().map { account ->
                account to calcAccBalanceAct(CalcAccBalanceAct.Input(account)).balance.toDouble()
            }
            val csv = exportCsvUseCase.exportCsv(
                plainNumbers = true,
                balances = balances,
                balancesTime = now,
            ) { findAll() }
            fileSystem.writeToFile(uri, csv, charset = Charsets.UTF_8, mode = "wt")
                .onLeft { throw it.e }
        }
        result.onSuccess {
            sharedPrefs.putLong(KEY_LAST_SUCCESS, now.toEpochMilli())
            sharedPrefs.remove(KEY_LAST_ERROR)
        }.onFailure {
            sharedPrefs.putString(KEY_LAST_ERROR, it.message ?: it.javaClass.simpleName)
        }
        return result.isSuccess
    }

    companion object {
        private const val KEY_URI = "drive_export_uri"
        private const val KEY_LAST_SUCCESS = "drive_export_last_success"
        private const val KEY_LAST_ERROR = "drive_export_last_error"
        private const val WORK_NAME = "drive_export"
        private const val DAILY_WORK_NAME = "drive_export_daily"
        private const val TABLE_TRANSACTIONS = "transactions"
        private const val TABLE_ACCOUNTS = "accounts"
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
