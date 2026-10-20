package com.msahil432.multitool.data

import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import kotlinx.coroutines.flow.Flow

/**
 * Repository wrapping [DownloadDao] for download lifecycle and state management.
 *
 * Provides business-level operations: enqueue, update progress, complete, fail,
 * retry, cancel, delete, and observe download history.
 */
class DownloadRepository(
    private val dao: DownloadDao,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Returns a reactive [Flow] of all download records, ordered newest first. */
    fun allDownloads(): Flow<List<DownloadItem>> = dao.getAllDownloads()

    /** Returns a reactive [Flow] of download records filtered by [status]. */
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>> =
        dao.getDownloadsByStatus(status)

    /** Returns a reactive [Flow] of the active download count for concurrency gating. */
    fun observeActiveCount(): Flow<Int> = dao.observeActiveDownloadCount()

    /** Returns the count of currently active (QUEUED or DOWNLOADING) downloads. */
    suspend fun activeCount(): Int = dao.getActiveDownloadCount()

    /** Returns the next queued download item ready to start, or null if none queued. */
    suspend fun nextQueued(): DownloadItem? = dao.getNextQueued()

    /** Retrieves a single download item by its [id], or null if not found. */
    suspend fun getById(id: Long): DownloadItem? = dao.getDownloadById(id)

    /**
     * Enqueues a new download item.
     *
     * @param sourcePageUrl Original web page URL where the media was discovered.
     * @param mediaUrl Direct network URL of the media resource.
     * @param fileName Suggested human-readable file name.
     * @param mimeType MIME type if known.
     * @param mediaType Categorized [MediaType].
     * @param thumbnailUrl Optional thumbnail URL for preview.
     * @return Row ID of the inserted download record.
     */
    suspend fun enqueue(
        sourcePageUrl: String,
        mediaUrl: String,
        fileName: String,
        mimeType: String?,
        mediaType: MediaType,
        thumbnailUrl: String? = null
    ): Long {
        return try {
            val now = clock()
            val id = dao.insert(
                DownloadItem(
                    sourcePageUrl = sourcePageUrl,
                    mediaUrl = mediaUrl,
                    fileName = fileName,
                    mimeType = mimeType,
                    mediaType = mediaType,
                    createdAt = now,
                    updatedAt = now,
                    thumbnailUrl = thumbnailUrl
                )
            )
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Download enqueued",
                data = mapOf("id" to id, "fileName" to fileName, "mediaType" to mediaType.name)
            )
            id
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Updates the progress of an active download.
     *
     * @param id Download item row ID.
     * @param downloadedBytes Bytes transferred so far.
     * @param totalBytes Total expected bytes, or -1 if unknown.
     * @param progressPercent Percentage (0–100), or -1 if indeterminate.
     */
    suspend fun updateProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        progressPercent: Int
    ) {
        try {
            dao.getDownloadById(id)?.let { item ->
                dao.update(
                    item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        progressPercent = progressPercent,
                        updatedAt = clock()
                    )
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Marks a download as completed successfully.
     *
     * @param id Download item row ID.
     * @param localFilePath Absolute path to the saved file on local storage.
     * @param totalBytes Final size of the downloaded file in bytes.
     */
    suspend fun markCompleted(id: Long, localFilePath: String, totalBytes: Long) {
        try {
            dao.getDownloadById(id)?.let { item ->
                dao.update(
                    item.copy(
                        status = DownloadStatus.COMPLETED,
                        localFilePath = localFilePath,
                        progressPercent = 100,
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        updatedAt = clock()
                    )
                )
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Download completed",
                    data = mapOf("id" to id, "bytes" to totalBytes, "path" to localFilePath)
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Marks a download as failed with an error description.
     *
     * @param id Download item row ID.
     * @param errorMessage Human-readable description of why the download failed.
     */
    suspend fun markFailed(id: Long, errorMessage: String) {
        try {
            dao.getDownloadById(id)?.let { item ->
                dao.update(
                    item.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = errorMessage,
                        updatedAt = clock()
                    )
                )
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Download failed",
                    data = mapOf("id" to id, "error" to errorMessage)
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Resets a failed or cancelled download back to QUEUED for retry.
     *
     * @param id Download item row ID.
     */
    suspend fun retry(id: Long) {
        try {
            dao.retryDownload(id, clock())
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Download retry requested",
                data = mapOf("id" to id)
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Marks an in-progress or queued download as cancelled by the user.
     *
     * @param id Download item row ID.
     */
    suspend fun cancel(id: Long) {
        try {
            dao.getDownloadById(id)?.let { item ->
                dao.update(
                    item.copy(
                        status = DownloadStatus.CANCELLED,
                        updatedAt = clock()
                    )
                )
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Download cancelled",
                    data = mapOf("id" to id)
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Deletes a single download record from the database.
     *
     * @param id Download item row ID to delete.
     */
    suspend fun deleteDownload(id: Long) {
        try {
            dao.getDownloadById(id)?.let { item ->
                dao.delete(item)
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Download record deleted",
                    data = mapOf("id" to id)
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Clears all completed download records from history. */
    suspend fun clearCompleted() {
        try {
            dao.deleteByStatus(DownloadStatus.COMPLETED)
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Cleared completed downloads"
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Clears all download records from history. */
    suspend fun clearAll() {
        try {
            dao.deleteAll()
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Cleared all downloads"
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}
