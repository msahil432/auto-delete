package com.msahil432.autodelete.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.msahil432.autodelete.AutoDeleteApp
import com.msahil432.autodelete.data.ActionStatus
import com.msahil432.autodelete.data.ActivityLogEntry
import com.msahil432.autodelete.data.FolderConfig
import com.msahil432.autodelete.data.LogAction
import com.msahil432.autodelete.data.PendingAction
import com.msahil432.autodelete.data.TimePeriodPreset
import com.msahil432.autodelete.data.decodeTimePeriodPresets
import com.msahil432.autodelete.data.encodeTimePeriodPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
            } else if (intent.action == "com.msahil432.autodelete.ACTION_MOVE") {
                performMove(context, db, config, filePath)
            } else if (intent.action == "com.msahil432.autodelete.ACTION_SCHEDULE") {
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
        db: com.msahil432.autodelete.data.AppDatabase,
        config: FolderConfig,
        filePath: String
    ) {
        val destUriString = config.moveDestinationPath
        val sourceFile = File(filePath)

        if (destUriString.isNullOrBlank() || !sourceFile.exists()) {
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.KEPT,
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.w("ActionReceiver", "Move skipped: dest=$destUriString exists=${sourceFile.exists()}")
            return
        }

        try {
            val destDir: File = if (destUriString.startsWith("/")) {
                File(destUriString)
            } else {
                val uriPath = Uri.parse(destUriString).path ?: destUriString
                val friendlyPath = uriPath
                    .removePrefix("/tree/primary:")
                    .removePrefix("/tree/")
                    .let { if (!it.startsWith("/")) "/storage/emulated/0/$it" else it }
                File(friendlyPath)
            }
            if (!destDir.exists()) destDir.mkdirs()

            val baseName = sourceFile.nameWithoutExtension
            val ext = sourceFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
            var destFile = File(destDir, sourceFile.name)
            var counter = 1
            while (destFile.exists()) {
                destFile = File(destDir, "${baseName}_$counter$ext")
                counter++
            }

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            sourceFile.delete()

            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = sourceFile.name,
                    fileUri = filePath,
                    action = LogAction.MOVED,
                    timestamp = System.currentTimeMillis(),
                    destinationPath = destFile.absolutePath
                )
            )
        } catch (e: Exception) {
            Log.e("ActionReceiver", "Move failed for $filePath", e)
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.KEPT,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun updateRecentlyUsed(
        db: com.msahil432.autodelete.data.AppDatabase,
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
