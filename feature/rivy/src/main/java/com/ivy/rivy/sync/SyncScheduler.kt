package com.ivy.rivy.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Decides when a background sync runs. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val prefs: SyncPrefs,
) {
    private val onlyWithNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Call when the app starts: syncs now and then every 15 minutes. */
    fun start() {
        if (!prefs.connected) return
        guarded {
            val periodic = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(onlyWithNetwork)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
        syncSoon()
    }

    /** Syncs once, as soon as there is a network connection. */
    fun syncSoon(delaySeconds: Long = 0) {
        if (!prefs.connected) return
        guarded {
            val once = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(onlyWithNetwork)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                once
            )
        }
    }

    fun stop() {
        guarded {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Scheduling must never take the app down.
            Timber.e(e, "Rivy: could not schedule a sync")
        }
    }

    private companion object {
        const val PERIODIC_WORK = "rivy_sync_periodic"
        const val ONE_TIME_WORK = "rivy_sync_once"
        const val PERIOD_MINUTES = 15L
    }
}
