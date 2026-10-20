package com.msahil432.multitool.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [CoroutineWorker] that handles direct HTTP/HTTPS file transfers of video and GIF resources.
 *
 * Persists data to app-specific external storage, streams progress updates to [DownloadRepository],
 * and gates concurrency to at most 3 simultaneous active downloads.
 */
class MediaDownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return Result.failure()

        val db = (appContext.applicationContext as MultiToolApp).database
        val repo = DownloadRepository(db.downloadDao())
        val item = repo.getById(downloadId) ?: return Result.failure()

        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "Media download execution started",
            data = mapOf(
                "downloadId" to downloadId,
                "mediaType" to item.mediaType.name,
                "fileName" to item.fileName
            )
        )

        // HLS/DASH streams are delegated to HlsMergeWorker
        if (item.mediaType == MediaType.HLS_STREAM || item.mediaType == MediaType.DASH_STREAM) {
            return handleHlsDownload(repo, item)
        }

        return try {
            repo.updateProgress(downloadId, 0, -1, 0)

            val downloadsDir = File(appContext.getExternalFilesDir(null), "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val targetFile = File(downloadsDir, item.fileName)
            val finalFile = getUniqueFile(targetFile)

            val url = URL(item.mediaUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
                connect()
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(finalFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Throttle progress updates: every ~50KB
                        if (downloadedBytes % (50 * 1024) < 8192) {
                            val percent = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            } else -1
                            repo.updateProgress(downloadId, downloadedBytes, totalBytes, percent)
                        }
                    }
                }
            }

            repo.markCompleted(downloadId, finalFile.absolutePath, downloadedBytes)

            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Media download finished successfully",
                data = mapOf(
                    "downloadId" to downloadId,
                    "fileSize" to downloadedBytes,
                    "path" to finalFile.absolutePath
                )
            )

            scheduleNextIfAvailable(appContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for downloadId=$downloadId", e)
            Sentry.captureException(e)
            repo.markFailed(downloadId, e.message ?: "Network download error")
            scheduleNextIfAvailable(appContext)
            Result.failure()
        }
    }

    private suspend fun handleHlsDownload(repo: DownloadRepository, item: DownloadItem): Result {
        repo.updateProgress(item.id, 0, -1, -1)
        HlsMergeWorker.schedule(appContext, item.id, item.mediaUrl, item.fileName)
        return Result.success()
    }

    private fun getUniqueFile(file: File): File {
        if (!file.exists()) return file
        val name = file.nameWithoutExtension
        val ext = file.extension
        var counter = 1
        var candidate: File
        do {
            candidate = File(file.parent, "${name}_($counter).$ext")
            counter++
        } while (candidate.exists())
        return candidate
    }

    companion object {
        private const val TAG = "MediaDownloadWorker"
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val MAX_CONCURRENT = 3

        /**
         * Schedules a WorkManager download task for [downloadId].
         */
        fun schedule(context: Context, downloadId: Long) {
            val inputData = workDataOf(KEY_DOWNLOAD_ID to downloadId)
            val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setInputData(inputData)
                .addTag("media_download")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "Download_$downloadId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Checks active download concurrency and schedules the next queued task if capacity permits.
         */
        suspend fun scheduleNextIfAvailable(context: Context) {
            try {
                val db = (context.applicationContext as MultiToolApp).database
                val repo = DownloadRepository(db.downloadDao())
                val activeCount = repo.activeCount()

                if (activeCount < MAX_CONCURRENT) {
                    val nextQueued = repo.nextQueued()
                    if (nextQueued != null) {
                        schedule(context, nextQueued.id)
                    }
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }
}
