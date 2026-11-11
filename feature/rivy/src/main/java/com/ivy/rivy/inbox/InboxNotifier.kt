package com.ivy.rivy.inbox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ivy.domain.AppStarter
import com.ivy.navigation.RivyInboxScreen
import com.ivy.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/** Tells the owner that transactions from bank emails wait for approval. */
class InboxNotifier @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val appStarter: AppStarter,
) {
    @Suppress("TooGenericExceptionCaught")
    fun notifyNewItems(count: Int) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Transaksi dari email bank",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )

            val openInbox = appStarter.getRootIntent().apply {
                putExtra(RivyInboxScreen.EXTRA_OPEN, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$count transaksi baru menunggu persetujuan")
                .setContentText("Ketuk untuk membuka kotak masuk.")
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        REQUEST_CODE,
                        openInbox,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Covers a missing notification permission as well.
            Timber.e(e, "Rivy: could not show the inbox notification")
        }
    }

    private companion object {
        const val CHANNEL_ID = "rivy_inbox"
        const val NOTIFICATION_ID = 7401
        const val REQUEST_CODE = 7401
    }
}
