package com.msahil432.multitool.service

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.ActivityLogEntry
import com.msahil432.multitool.data.FolderConfig
import com.msahil432.multitool.data.LogAction
import java.io.File

/**
 * Shared helper for the Move Rule operation.
 *
 * Uses [Context.contentResolver] streams (openInputStream / openOutputStream) instead of
 * raw [java.io.FileInputStream] / [java.io.FileOutputStream], which avoids the EACCES
 * (Permission denied) error on scoped-storage devices even when MANAGE_EXTERNAL_STORAGE
 * has been granted at the system level.
 *
 * Callers must be on a background thread (suspend or IO dispatcher).
 */
object MoveHelper {

    private const val TAG = "MoveHelper"

    /**
     * Performs the move operation for [filePath] according to [config].
     *
     * Outcomes:
     *  - Success  → logs MOVED + destination path, fires no notification
     *  - MANAGE_EXTERNAL_STORAGE not granted → logs ERRORED, fires actionable notification
     *  - Destination not configured → logs ERRORED, fires error notification
     *  - IO / other failure → logs ERRORED + brief stack trace, fires error notification
     */
    suspend fun performMove(context: Context, config: FolderConfig, filePath: String) {
        val db = (context.applicationContext as MultiToolApp).database

        // ── 1. Guard: MANAGE_EXTERNAL_STORAGE must be granted ───────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val msg = "\"All Files Access\" permission not granted. " +
                    "Go to Settings → Apps → Multi Tool → Permissions → " +
                    "Files and Media → Allow access to all files."
            Log.e(TAG, "Move aborted — MANAGE_EXTERNAL_STORAGE not granted")
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.ERRORED,
                    timestamp = System.currentTimeMillis(),
                    errorDetails = "MANAGE_EXTERNAL_STORAGE not granted"
                )
            )
            fireErrorNotification(context, filePath, msg)
            return
        }

        // ── 2. Guard: destination must be configured ─────────────────────────────
        val destUriString = config.moveDestinationPath
        if (destUriString.isNullOrBlank()) {
            val msg = "No destination folder configured. " +
                    "Open the folder settings and pick a Move destination."
            Log.e(TAG, "Move aborted — no destination configured")
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.ERRORED,
                    timestamp = System.currentTimeMillis(),
                    errorDetails = "No destination configured"
                )
            )
            fireErrorNotification(context, filePath, msg)
            return
        }

        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            Log.w(TAG, "Source file no longer exists: $filePath")
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.ERRORED,
                    timestamp = System.currentTimeMillis(),
                    errorDetails = "Source file not found at move time"
                )
            )
            return
        }

        // ── 3. Resolve destination directory ─────────────────────────────────────
        val destDir: File = resolvePath(destUriString)

        try {
            if (!destDir.exists()) destDir.mkdirs()

            // ── 4. Build unique file name (conflict resolution) ──────────────────
            val baseName = sourceFile.nameWithoutExtension
            val ext = sourceFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
            var destFile = File(destDir, sourceFile.name)
            var counter = 1
            while (destFile.exists()) {
                destFile = File(destDir, "${baseName}_$counter$ext")
                counter++
            }

            // ── 5. Copy via ContentResolver streams ──────────────────────────────
            //    This works correctly when MANAGE_EXTERNAL_STORAGE is granted,
            //    unlike raw FileInputStream which may still get EACCES on some OEMs.
            val sourceUri = Uri.fromFile(sourceFile)
            val destUri   = Uri.fromFile(destFile)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Could not open output stream for ${destFile.absolutePath}")
            } ?: throw IllegalStateException("Could not open input stream for $filePath")

            // Delete the original only after a successful copy
            if (!sourceFile.delete()) {
                Log.w(TAG, "Copied but could not delete source: $filePath")
            }

            // ── 6. Log success ────────────────────────────────────────────────────
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
            Log.d(TAG, "Moved ${sourceFile.name} → ${destFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Move failed for $filePath", e)
            val briefTrace = e.stackTrace.take(3)
                .joinToString("\n") {
                    "  at ${it.className.substringAfterLast('.')}.${it.methodName}" +
                            "(${it.fileName}:${it.lineNumber})"
                }
            val errorDetails = "${e::class.simpleName}: ${e.message}\n$briefTrace"
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.ERRORED,
                    timestamp = System.currentTimeMillis(),
                    errorDetails = errorDetails
                )
            )
            fireErrorNotification(context, filePath, "Move failed: ${e.localizedMessage}")
        }
    }

    // ── Path resolution (SAF tree URI → friendly /storage/emulated/0/... path) ──

    private fun resolvePath(uriString: String): File {
        if (uriString.startsWith("/")) return File(uriString)
        // SAF tree URI  (e.g. content://...externalstorage.../tree/primary:Photos/Backup)
        val uriPath = Uri.parse(uriString).path ?: uriString
        val friendlyPath = uriPath
            .removePrefix("/tree/primary:")
            .removePrefix("/tree/")
            .let { if (!it.startsWith("/")) "/storage/emulated/0/$it" else it }
        return File(friendlyPath)
    }

    // ── Error notification helper ─────────────────────────────────────────────────

    fun fireErrorNotification(context: Context, filePath: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = android.app.NotificationChannel(
                "move_errors", "Move Errors", NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "move_errors")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Multi Tool — Move Failed")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "File: ${filePath.substringAfterLast("/")}\n\n$message"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context)
            .notify(filePath.hashCode() xor 0x7E770001, notification)
    }
}
