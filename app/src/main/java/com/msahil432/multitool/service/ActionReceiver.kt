package com.msahil432.multitool.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.ActionStatus
import com.msahil432.multitool.data.ActivityLogEntry
import com.msahil432.multitool.data.FolderConfig
import com.msahil432.multitool.data.LogAction
import com.msahil432.multitool.data.PendingAction
import com.msahil432.multitool.data.TimePeriodPreset
import com.msahil432.multitool.data.decodeTimePeriodPresets
import com.msahil432.multitool.data.encodeTimePeriodPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val folderId = intent.getLongExtra("folderId", -1L)
        val filePath = intent.getStringExtra("filePath") ?: return

        // Dismiss notification
        NotificationManagerCompat.from(context).cancel(filePath.hashCode())

        if (folderId == -1L) return

        val db = (context.applicationContext as MultiToolApp).database
        CoroutineScope(Dispatchers.IO).launch {
            val config = db.appDao().getFolderConfigById(folderId).firstOrNull() ?: return@launch

            if (intent.action == "com.msahil432.multitool.ACTION_KEEP") {
                db.appDao().insertActivityLog(
                    ActivityLogEntry(
                        folderId = config.id,
                        fileName = filePath.substringAfterLast("/"),
                        fileUri = filePath,
                        action = LogAction.KEPT,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else if (intent.action == "com.msahil432.multitool.ACTION_MOVE") {
                performMove(context, db, config, filePath)
            } else if (intent.action == "com.msahil432.multitool.ACTION_SCHEDULE") {
                // New format: millis + label sent directly
                val millis = intent.getLongExtra("timePeriodMillis", -1L)
                val label  = intent.getStringExtra("timePeriodLabel")

                val preset: TimePeriodPreset = if (millis > 0 && label != null) {
                    // New notification path — millis already known
                    TimePeriodPreset(label = label, millis = millis)
                } else {
                    // Legacy fallback: parse from old "timePeriod" string extra
                    val timePeriod = intent.getStringExtra("timePeriod") ?: return@launch
                    if (timePeriod.equals("never", ignoreCase = true)) {
                        db.appDao().insertActivityLog(
                            ActivityLogEntry(
                                folderId = config.id,
                                fileName = filePath.substringAfterLast("/"),
                                fileUri = filePath,
                                action = LogAction.KEPT,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        return@launch
                    }
                    TimePeriodPreset(label = timePeriod, millis = parseTimePeriod(timePeriod))
                }

                db.appDao().insertPendingAction(
                    PendingAction(
                        folderId = config.id,
                        fileUri = filePath,
                        scheduledAt = System.currentTimeMillis() + preset.millis,
                        status = ActionStatus.PENDING
                    )
                )
                FileActionWorker.schedule(context, config.id, filePath, preset.millis)
                updateRecentlyUsed(db, config, preset)
            }
        }
    }

    private suspend fun performMove(
        context: Context,
        db: com.msahil432.multitool.data.AppDatabase,
        config: FolderConfig,
        filePath: String
    ) {
        MoveHelper.performMove(context, config, filePath)
    }

    private suspend fun updateRecentlyUsed(
        db: com.msahil432.multitool.data.AppDatabase,
        config: FolderConfig,
        used: TimePeriodPreset
    ) {
        val current = decodeTimePeriodPresets(config.recentlyUsedPeriods).toMutableList()
        current.removeAll { it.millis == used.millis }
        current.add(0, used)
        val trimmed = current.take(4)
        db.appDao().updateFolderConfig(config.copy(recentlyUsedPeriods = encodeTimePeriodPresets(trimmed)))
    }

    /** Fallback parser for legacy "timePeriod" string extras from old notification intents. */
    private fun parseTimePeriod(period: String): Long {
        val lower = period.lowercase().trim()
        val num = lower.filter { it.isDigit() }.toLongOrNull() ?: 1L
        return when {
            lower.contains("sec") -> num * 1000L
            lower.contains("min") -> num * 60_000L
            lower.contains("h")   -> num * 3_600_000L
            lower.contains("d")   -> num * 86_400_000L
            lower.contains("w")   -> num * 604_800_000L
            lower.contains("mo")  -> num * 2_592_000_000L
            else                  -> 60_000L
        }
    }
}
