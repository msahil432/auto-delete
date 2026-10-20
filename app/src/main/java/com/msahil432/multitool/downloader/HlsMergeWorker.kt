package com.msahil432.multitool.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * [CoroutineWorker] that executes FFmpeg to download, assemble, and remux
 * HLS (.m3u8) or DASH stream segments into a unified MP4 file.
 */
class HlsMergeWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val m3u8Url = inputData.getString(KEY_M3U8_URL) ?: return Result.failure()
        val outputName = inputData.getString(KEY_OUTPUT_NAME) ?: "hls_stream.mp4"

        if (downloadId == -1L) return Result.failure()

        val db = (appContext.applicationContext as MultiToolApp).database
        val repo = DownloadRepository(db.downloadDao())

        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "HLS merge started",
            data = mapOf(
                "downloadId" to downloadId,
                "outputName" to outputName
            )
        )

        return try {
            val downloadsDir = File(appContext.getExternalFilesDir(null), "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val cleanName = if (outputName.endsWith(".mp4", ignoreCase = true)) {
                outputName
            } else {
                "${outputName.substringBeforeLast('.')}.mp4"
            }
            val outputFile = File(downloadsDir, cleanName)

            // Track time processed via FFmpegKit statistics callback
            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                val timeMs = statistics.time.toLong()
                if (timeMs > 0 && timeMs % 2000 < 100) {
                    try {
                        runBlocking {
                            repo.updateProgress(downloadId, timeMs, -1L, -1)
                        }
                    } catch (_: Exception) {
                        // Avoid crashing background callback
                    }
                }
            }

            // Remux HLS stream directly to MP4 container without re-encoding
            val command = "-y -i \"$m3u8Url\" -c copy -bsf:a aac_adtstoasc \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                val fileSize = outputFile.length()
                repo.markCompleted(downloadId, outputFile.absolutePath, fileSize)
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "HLS merge completed",
                    data = mapOf("downloadId" to downloadId, "fileSize" to fileSize)
                )
                MediaDownloadWorker.scheduleNextIfAvailable(appContext)
                Result.success()
            } else {
                val errorMsg = "FFmpeg returned code $returnCode, state ${session.state}"
                repo.markFailed(downloadId, errorMsg)
                Sentry.captureMessage("HLS merge failed: $errorMsg: ${session.failStackTrace}")
                MediaDownloadWorker.scheduleNextIfAvailable(appContext)
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "HLS merge failed for downloadId=$downloadId", e)
            Sentry.captureException(e)
            repo.markFailed(downloadId, e.message ?: "FFmpeg execution error")
            MediaDownloadWorker.scheduleNextIfAvailable(appContext)
            Result.failure()
        } finally {
            FFmpegKitConfig.enableStatisticsCallback(null)
        }
    }

    companion object {
        private const val TAG = "HlsMergeWorker"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_M3U8_URL = "m3u8_url"
        const val KEY_OUTPUT_NAME = "output_name"

        /**
         * Schedules a WorkManager job for merging an HLS or DASH stream.
         *
         * @param context Application context.
         * @param downloadId Row ID of the associated download record.
         * @param m3u8Url Network URL of the playlist/manifest.
         * @param outputName Desired output filename.
         */
        fun schedule(context: Context, downloadId: Long, m3u8Url: String, outputName: String) {
            val inputData = workDataOf(
                KEY_DOWNLOAD_ID to downloadId,
                KEY_M3U8_URL to m3u8Url,
                KEY_OUTPUT_NAME to outputName
            )
            val request = OneTimeWorkRequestBuilder<HlsMergeWorker>()
                .setInputData(inputData)
                .addTag("hls_merge")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "HLS_$downloadId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
