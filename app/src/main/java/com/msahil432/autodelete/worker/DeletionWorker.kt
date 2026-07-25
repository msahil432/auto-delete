package com.msahil432.autodelete.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msahil432.autodelete.data.AppDatabase
import com.msahil432.autodelete.data.History
import java.io.File

class DeletionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val deletionId = inputData.getLong(KEY_DELETION_ID, -1L)
        
        val db = AppDatabase.getDatabase(appContext)
        val dao = db.appDao()

        val scheduledDeletion = dao.getScheduledDeletion(deletionId)
        
        val file = File(filePath)
        val status = if (file.exists()) {
            if (file.delete()) "Deleted" else "Failed"
        } else {
            "Skipped"
        }

        // Add history entry
        if (scheduledDeletion != null) {
            dao.insertHistory(
                History(
                    fileName = file.name,
                    folderPath = file.parent ?: "",
                    fileSize = if (file.exists()) file.length() else 0L,
                    createdTime = scheduledDeletion.createdTime,
                    scheduledTime = scheduledDeletion.scheduledTime,
                    deletedTime = System.currentTimeMillis(),
                    status = status
                )
            )
            // Remove from scheduled deletions since it's done
            dao.removeScheduledDeletion(deletionId)
        }

        return if (status == "Failed") Result.failure() else Result.success()
    }

    companion object {
        const val KEY_FILE_PATH = "FILE_PATH"
        const val KEY_DELETION_ID = "DELETION_ID"
    }
}
