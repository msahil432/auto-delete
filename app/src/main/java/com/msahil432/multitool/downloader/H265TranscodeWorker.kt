package com.msahil432.multitool.downloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import java.io.File

/**
 * [CoroutineWorker] that re-encodes a completed video download to H.265 (HEVC) using
 * the on-device hardware encoder via FFmpegKit. Only one job runs at a time.
 */
class H265TranscodeWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return Result.failure()

        val db = (appContext.applicationContext as MultiToolApp).database
        val repo = DownloadRepository(db.downloadDao())

        // Guard: allow only 1 concurrent transcode across different downloads
        val activeId = repo.getActivelyTranscodingId()
        if (activeId != null && activeId != downloadId) {
            // Another job is running; return success so WorkManager doesn't retry immediately
            return Result.success()
        }

        val item = repo.getById(downloadId) ?: return Result.failure()
        val inputPath = item.localFilePath ?: return Result.failure()
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            repo.markTranscodeFailed(downloadId, "Source video file not found")
            scheduleNextTranscodeIfAvailable(appContext)
            return Result.failure()
        }

        // 1. If the video file is already in H265, skip the transcoding
        if (VideoCodecDetector.isH265File(inputFile)) {
            val originalBytes = inputFile.length()
            repo.markTranscodeSkippedAlreadyH265(downloadId, originalBytes)
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "H265 transcode skipped - file is already H.265/HEVC",
                data = mapOf("downloadId" to downloadId, "fileName" to item.fileName, "bytes" to originalBytes)
            )
            scheduleNextTranscodeIfAvailable(appContext)
            return Result.success()
        }

        repo.markTranscodeStarted(downloadId)
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "H265 transcode started",
            data = mapOf("downloadId" to downloadId, "fileName" to item.fileName)
        )

        val outputFile = File(inputFile.parent, "${inputFile.nameWithoutExtension}_h265.mp4")
        if (outputFile.exists()) {
            outputFile.delete() // Clean up any partial output from a previous interrupted run
        }

        val command = "-y -i \"${inputFile.absolutePath}\" -c:v hevc_mediacodec -c:a copy -tag:v hvc1 \"${outputFile.absolutePath}\""

        return try {
            val session = FFmpegKit.execute(command)
            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                val originalBytes = inputFile.length()
                val compressedBytes = outputFile.length()

                // 2. If the size of the transcoded file is larger than the original, discard the transcoded file
                if (compressedBytes >= originalBytes) {
                    outputFile.delete()
                    repo.markTranscodeSkippedLarger(downloadId, originalBytes, compressedBytes)
                    Breadcrumbs.record(
                        category = Breadcrumbs.CAT_DOWNLOADER,
                        message = "H265 transcode discarded - transcoded file was larger",
                        data = mapOf(
                            "downloadId" to downloadId,
                            "originalBytes" to originalBytes,
                            "compressedBytes" to compressedBytes
                        )
                    )
                } else {
                    // Commit transcode completion to database first so DB points to valid outputFile
                    repo.markTranscodeCompleted(downloadId, originalBytes, compressedBytes, outputFile.absolutePath)
                    inputFile.delete() // Safely delete original after DB is updated
                    Breadcrumbs.record(
                        category = Breadcrumbs.CAT_DOWNLOADER,
                        message = "H265 transcode completed",
                        data = mapOf(
                            "downloadId" to downloadId,
                            "originalBytes" to originalBytes,
                            "compressedBytes" to compressedBytes,
                            "ratio" to "%.0f%%".format(compressedBytes * 100.0 / originalBytes.coerceAtLeast(1))
                        )
                    )
                }
                scheduleNextTranscodeIfAvailable(appContext)
                Result.success()
            } else {
                // 3. If the transcoding fails, record this error to show in downloads list
                val errMsg = "FFmpeg H265 failed with return code ${session.returnCode}"
                repo.markTranscodeFailed(downloadId, errMsg)
                Sentry.captureMessage(errMsg)
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                scheduleNextTranscodeIfAvailable(appContext)
                Result.failure()
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            val errMsg = e.message ?: "Transcoding execution error"
            repo.markTranscodeFailed(downloadId, errMsg)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            scheduleNextTranscodeIfAvailable(appContext)
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"

        /** Schedules a WorkManager job for transcoding [downloadId] to H.265. */
        fun schedule(context: Context, downloadId: Long) {
            val request = OneTimeWorkRequestBuilder<H265TranscodeWorker>()
                .setInputData(workDataOf(KEY_DOWNLOAD_ID to downloadId))
                .addTag("h265_transcode")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "H265_$downloadId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** Checks if any transcode is running; if not, schedules the next pending eligible download. */
        suspend fun scheduleNextTranscodeIfAvailable(context: Context) {
            try {
                val db = (context.applicationContext as MultiToolApp).database
                val repo = DownloadRepository(db.downloadDao())
                if (repo.transcodingCount() == 0) {
                    val next = repo.nextPendingTranscode()
                    if (next != null) {
                        schedule(context, next.id)
                    }
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }
}
