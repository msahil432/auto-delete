package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents the current lifecycle state of a download operation.
 */
enum class DownloadStatus {
    /** Download is queued but not yet started. */
    QUEUED,
    /** Download is actively in progress. */
    DOWNLOADING,
    /** Download completed successfully. */
    COMPLETED,
    /** Download failed — eligible for retry. */
    FAILED,
    /** Download was cancelled by the user. */
    CANCELLED
}

/**
 * Describes the type of media resource being detected or downloaded.
 */
enum class MediaType {
    /** Standard video file (mp4, webm, mkv). */
    VIDEO,
    /** Animated GIF image. */
    GIF,
    /** Animated WebP image. */
    ANIMATED_WEBP,
    /** HTTP Live Streaming manifest (m3u8). */
    HLS_STREAM,
    /** Dynamic Adaptive Streaming over HTTP manifest (mpd). */
    DASH_STREAM
}

/**
 * Entity representing a single download item persisted in Room.
 *
 * Tracks source URL, direct media URL, local file path, progress, status, and metadata
 * for the download list and management features.
 */
@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt")]
)
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The original web page URL where the media was discovered (enables reload functionality). */
    val sourcePageUrl: String,
    /** The direct network URL of the media resource being downloaded. */
    val mediaUrl: String,
    /** Human-readable filename derived from the URL or page context. */
    val fileName: String,
    /** MIME type if known (e.g., "video/mp4", "image/gif"). */
    val mimeType: String?,
    /** Categorized [MediaType] of this resource. */
    val mediaType: MediaType,
    /** Current lifecycle status of this download. */
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** Download progress percentage (0–100), or -1 if indeterminate. */
    val progressPercent: Int = 0,
    /** Total file size in bytes if known from Content-Length, or -1 if unknown. */
    val totalBytes: Long = -1L,
    /** Bytes downloaded so far. */
    val downloadedBytes: Long = 0L,
    /** Absolute path to the downloaded file on local disk, or null until download begins. */
    val localFilePath: String? = null,
    /** Error message description if [status] is [DownloadStatus.FAILED]. */
    val errorMessage: String? = null,
    /** Epoch timestamp in milliseconds when this download was enqueued. */
    val createdAt: Long,
    /** Epoch timestamp in milliseconds when this download was last updated. */
    val updatedAt: Long,
    /** Optional thumbnail URL extracted from the page/poster for preview display. */
    val thumbnailUrl: String? = null
)
