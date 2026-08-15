package com.msahil432.multitool.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.msahil432.multitool.MainActivity
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.R
import com.msahil432.multitool.data.NotificationRepository
import java.util.concurrent.TimeUnit

class NotificationDigestWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = appContext.applicationContext as? MultiToolApp ?: return Result.success()
        val repo = NotificationRepository(app.database.notificationDao())

        val undelivered = repo.getUndeliveredSync()
        if (undelivered.isEmpty()) {
            return Result.success()
        }

        val count = undelivered.size
        createNotificationChannel(appContext)

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "notification_vault")
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            DIGEST_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build summary string from distinct apps
        val pm = appContext.packageManager
        val distinctAppNames = undelivered.map { item ->
            try {
                val appInfo = pm.getApplicationInfo(item.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                item.packageName
            }
        }.distinct()

        val appsSummary = distinctAppNames.take(3).joinToString(", ") +
                if (distinctAppNames.size > 3) " +${distinctAppNames.size - 3} more" else ""

        val title = if (count == 1) "1 notification while you were focused" else "$count notifications while you were focused"
        val contentText = "From $appsSummary"

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("Notification Vault")
        undelivered.take(5).forEach { item ->
            val label = try {
                val appInfo = pm.getApplicationInfo(item.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                item.packageName
            }
            val line = if (!item.title.isNullOrBlank()) {
                "$label: ${item.title}"
            } else {
                label
            }
            style.addLine(line)
        }
        if (undelivered.size > 5) {
            style.addLine("+${undelivered.size - 5} more")
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(DIGEST_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }

        // Mark rows delivered
        repo.markAllDelivered()

        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "notification_vault_digest"
        const val DIGEST_NOTIFICATION_ID = 2002
        const val WORK_NAME_SCHEDULED = "notification_vault_digest_scheduled"
        const val WORK_NAME_ONETIME = "notification_vault_digest_onetime"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Notification Vault Digest",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Delivers consolidated digests of held notifications after focus sessions"
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            }
        }
    }
}
