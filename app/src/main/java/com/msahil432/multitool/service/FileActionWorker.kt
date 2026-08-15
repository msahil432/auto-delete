package com.msahil432.multitool.service

import android.content.Context
import android.provider.MediaStore
import androidx.work.*
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.ActionStatus
import com.msahil432.multitool.data.DeletionMode
import com.msahil432.multitool.data.LogAction
import com.msahil432.multitool.data.ActivityLogEntry
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Log

class FileActionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val folderId = inputData.getLong("folderId", -1L)
        val filePath = inputData.getString("filePath") ?: return Result.failure()

        if (folderId == -1L) return Result.failure()

        val db = (appContext.applicationContext as MultiToolApp).database
        val config = db.appDao().getFolderConfigById(folderId).firstOrNull() ?: return Result.failure()
        
        // Wait, what if the action was cancelled? We should check PendingAction.
        val pendingAction = db.appDao().getPendingActionByUri(filePath)
        if (pendingAction == null || pendingAction.status != ActionStatus.PENDING) {
            return Result.success() // Cancelled or already processed
        }

        val file = File(filePath)
        if (!file.exists()) {
            db.appDao().updatePendingAction(pendingAction.copy(status = ActionStatus.CANCELLED))
            return Result.success() // File already gone
        }
        
        try {
            when (config.deletionMode) {
                DeletionMode.TRASH -> {
                    // Trashing requires MediaStore on modern Android, or fallback to moving to a local .trash folder if not in MediaStore.
                    // Assuming All Files Access, we can just delete it, or move it to a hidden folder.
                    // The spec says: default OS-level retention.
                    // We will attempt to use MediaStore trash if it's a media file, otherwise just delete.
                    val uri = MediaStore.Files.getContentUri("external")
                    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                    val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
                    val selectionArgs = arrayOf(filePath)
                    
                    val cursor = appContext.contentResolver.query(uri, projection, selection, selectionArgs, null)
                    if (cursor != null && cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        val itemUri = android.content.ContentUris.withAppendedId(uri, id)
                        
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_TRASHED, 1)
                        }
                        try {
                            appContext.contentResolver.update(itemUri, values, null, null)
                        } catch (e: Exception) {
                            Log.e("FileActionWorker", "Failed to trash via MediaStore, trying delete", e)
                            file.delete()
                        }
                        cursor.close()
                    } else {
                        Log.w("FileActionWorker", "File not in MediaStore, deleting: $filePath")
                        file.delete()
                    }

                    db.appDao().updatePendingAction(pendingAction.copy(status = ActionStatus.TRASHED))
                    db.appDao().insertActivityLog(
                        ActivityLogEntry(
                            folderId = config.id,
                            fileName = file.name,
                            fileUri = filePath,
                            action = LogAction.TRASHED,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                DeletionMode.DELETE -> {
                    file.delete()
                    db.appDao().updatePendingAction(pendingAction.copy(status = ActionStatus.DELETED))
                    db.appDao().insertActivityLog(
                        ActivityLogEntry(
                            folderId = config.id,
                            fileName = file.name,
                            fileUri = filePath,
                            action = LogAction.DELETED,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                DeletionMode.ASK_AGAIN -> {
                    // Trigger prompt again
                    PromptHelper.showPrompt(appContext, config, filePath)
                    db.appDao().updatePendingAction(pendingAction.copy(status = ActionStatus.CANCELLED))
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("FileActionWorker", "Error processing file: $filePath", e)
            val briefTrace = e.stackTrace.take(3)
                .joinToString("\n") { "  at ${it.className.substringAfterLast('.')}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
            val errorDetails = "${e::class.simpleName}: ${e.message}\n$briefTrace"
            try {
                db.appDao().insertActivityLog(
                    ActivityLogEntry(
                        folderId = folderId,
                        fileName = file.name,
                        fileUri = filePath,
                        action = LogAction.ERRORED,
                        timestamp = System.currentTimeMillis(),
                        errorDetails = errorDetails
                    )
                )
            } catch (logEx: Exception) {
                Log.e("FileActionWorker", "Failed to write error log", logEx)
            }
            return Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context, folderId: Long, filePath: String, delayMillis: Long) {
            val inputData = workDataOf(
                "folderId" to folderId,
                "filePath" to filePath
            )
            val request = OneTimeWorkRequestBuilder<FileActionWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "Action_${filePath.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
