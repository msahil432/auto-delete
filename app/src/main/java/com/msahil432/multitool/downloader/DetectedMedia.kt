package com.msahil432.multitool.downloader

import com.msahil432.multitool.data.MediaType

/**
 * Describes how a media resource was detected on the loaded web page.
 */
enum class DetectionStrategy {
    /** Found via DOM inspection (<video>, <source>, or <img> elements). */
    DOM_ELEMENT,
    /** Intercepted from a network resource request matching video/GIF extensions or headers. */
    NETWORK_INTERCEPT,
    /** Captured or detected via canvas recording or non-standard container analysis. */
    CANVAS_CAPTURE
}

/**
 * Retrieval status of remote media file size.
 */
enum class SizeFetchStatus {
    /** Size retrieval is actively pending in the background. */
    FETCHING,
    /** File size was successfully retrieved. */
    SUCCESS,
    /** Size could not be determined due to network error, HTTP status, or missing headers. */
    FAILED
}

/**
 * Formats a byte count into a human-readable size string (e.g., "1.4 MB", "512.0 KB", "120 B").
 *
 * @param bytes Size in bytes. If negative, returns "Cannot fetch the size".
 */
fun formatFileSize(bytes: Long): String {
    if (bytes < 0L) return "Cannot fetch the size"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}

/**
 * Represents a single media resource discovered on a loaded web page.
 *
 * Emitted by [WebViewMediaDetector] and presented to the user for selection.
 */
data class DetectedMedia(
    /** Direct network URL of the media resource (or pseudo-URI for canvas captures). */
    val url: String,
    /** MIME type if known or guessed (e.g. "video/mp4", "image/gif"). */
    val mimeType: String?,
    /** Suggested human-readable file name. */
    val suggestedFileName: String,
    /** Categorized [MediaType] of this resource. */
    val mediaType: MediaType,
    /** How this media resource was discovered. */
    val detectionStrategy: DetectionStrategy,
    /** Estimated size in bytes if known from headers, or -1 if unknown. */
    val estimatedSizeBytes: Long = -1L,
    /** Retrieval status of the file size. */
    val sizeFetchStatus: SizeFetchStatus = if (estimatedSizeBytes > 0L) SizeFetchStatus.SUCCESS else SizeFetchStatus.FETCHING,
    /** Duration in seconds for video/audio streams if known, or -1.0 if unknown. */
    val durationSeconds: Double = -1.0,
    /** Video/image width in pixels if known, or -1 if unknown. */
    val width: Int = -1,
    /** Video/image height in pixels if known, or -1 if unknown. */
    val height: Int = -1,
    /** Thumbnail or poster image URL if available from the page. */
    val thumbnailUrl: String? = null
) {
    /**
     * Formatted string representing the media item's size, loading state, or failure message.
     */
    val formattedSize: String
        get() = when (sizeFetchStatus) {
            SizeFetchStatus.FETCHING -> "Fetching size..."
            SizeFetchStatus.SUCCESS -> if (estimatedSizeBytes > 0L) formatFileSize(estimatedSizeBytes) else "Cannot fetch the size"
            SizeFetchStatus.FAILED -> "Cannot fetch the size"
        }
}

