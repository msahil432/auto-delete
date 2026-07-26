package com.msahil432.autodelete.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.msahil432.autodelete.AutoDeleteApp
import com.msahil432.autodelete.data.ActionStatus
import com.msahil432.autodelete.data.ActivityLogEntry
import com.msahil432.autodelete.data.FolderConfig
import com.msahil432.autodelete.data.LogAction
import com.msahil432.autodelete.data.PendingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val folderId = intent.getLongExtra("folderId", -1L)
        val filePath = intent.getStringExtra("filePath") ?: return
        
        // Dismiss notification
        NotificationManagerCompat.from(context).cancel(filePath.hashCode())
        
        if (folderId == -1L) return

        val db = (context.applicationContext as AutoDeleteApp).database
        CoroutineScope(Dispatchers.IO).launch {
            val config = db.appDao().getFolderConfigById(folderId).firstOrNull() ?: return@launch
            
            if (intent.action == "com.msahil432.autodelete.ACTION_KEEP") {
                db.appDao().insertActivityLog(
                    ActivityLogEntry(
                        folderId = config.id,
                        fileName = filePath.substringAfterLast("/"),
                        fileUri = filePath,
                        action = LogAction.KEPT,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else if (intent.action == "com.msahil432.autodelete.ACTION_SCHEDULE") {
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
                
                val durationMillis = parseTimePeriod(timePeriod)
                db.appDao().insertPendingAction(
                    PendingAction(
                        folderId = config.id,
                        fileUri = filePath,
                        scheduledAt = System.currentTimeMillis() + durationMillis,
                        status = ActionStatus.PENDING
                    )
                )
                FileActionWorker.schedule(context, config.id, filePath, durationMillis)
                
                updateRecentlyUsed(db, config, timePeriod)
            }
        }
    }
    
    private suspend fun updateRecentlyUsed(db: com.msahil432.autodelete.data.AppDatabase, config: FolderConfig, timePeriod: String) {
        val currentList = config.recentlyUsedPeriods.split(",").filter { it.isNotBlank() }.toMutableList()
        currentList.remove(timePeriod)
        currentList.add(0, timePeriod)
        val newList = currentList.take(4).joinToString(",")
        db.appDao().updateFolderConfig(config.copy(recentlyUsedPeriods = newList))
    }

    private fun parseTimePeriod(period: String): Long {
        val lower = period.lowercase().trim()
        val num = lower.filter { it.isDigit() }.toLongOrNull() ?: 1L
        return when {
            lower.contains("sec") -> num * 1000L
            lower.contains("min") -> num * 60_000L
            lower.contains("h") -> num * 3600_000L
            lower.contains("d") -> num * 86_400_000L
            lower.contains("w") -> num * 604_800_000L
            lower.contains("mo") -> num * 2_592_000_000L
            else -> 60_000L
        }
    }
}
