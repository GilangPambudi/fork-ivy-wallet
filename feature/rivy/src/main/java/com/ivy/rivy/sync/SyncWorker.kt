package com.ivy.rivy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.rivy.inbox.InboxNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncEngine(): SyncEngine
    fun inboxNotifier(): InboxNotifier
}

/** Syncs in the background and tells the owner about new bank transactions. */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val outcome = entryPoint.syncEngine().sync()
        if (outcome is SyncOutcome.Done && outcome.newInboxItems > 0) {
            entryPoint.inboxNotifier().notifyNewItems(outcome.newInboxItems)
        }
        // A failed sync is simply tried again at the next scheduled run.
        return Result.success()
    }
}
