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
    /** Duration in seconds for video/audio streams if known, or -1.0 if unknown. */
    val durationSeconds: Double = -1.0,
    /** Video/image width in pixels if known, or -1 if unknown. */
    val width: Int = -1,
    /** Video/image height in pixels if known, or -1 if unknown. */
    val height: Int = -1,
    /** Thumbnail or poster image URL if available from the page. */
    val thumbnailUrl: String? = null
)
