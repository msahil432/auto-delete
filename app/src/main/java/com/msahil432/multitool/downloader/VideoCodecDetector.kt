package com.msahil432.multitool.downloader

import android.media.MediaExtractor
import android.media.MediaFormat
import com.arthenica.ffmpegkit.FFprobeKit
import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import java.io.File

/**
 * Utility for detecting video codecs, specifically checking whether a local file
 * or remote stream is already encoded in H.265 (HEVC).
 */
object VideoCodecDetector {

    /**
     * Inspects a local video [file] to check if its primary video track is encoded in H.265/HEVC.
     *
     * First attempts inspection using Android standard [MediaExtractor]; if unavailable
     * or failing, falls back to [FFprobeKit].
     *
     * @param file Local video file to inspect.
     * @return `true` if the file has an H.265/HEVC video track, `false` otherwise.
     */
    fun isH265File(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false

        // 1. Try standard Android MediaExtractor
        try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val trackCount = extractor.trackCount
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (isHevcMime(mime)) {
                        return true
                    }
                }
            } finally {
                extractor.release()
            }
        } catch (e: Exception) {
            // MediaExtractor can fail on some container types or in headless test environments
        }

        // 2. Fallback to FFprobeKit
        return try {
            val session = FFprobeKit.getMediaInformation(file.absolutePath)
            val info = session.mediaInformation
            info?.streams?.any { stream ->
                stream.type.equals("video", ignoreCase = true) && isHevcCodec(stream.codec)
            } ?: false
        } catch (e: Exception) {
            Sentry.captureException(e)
            false
        }
    }

    /**
     * Inspects a remote streaming manifest (HLS/DASH) at [streamUrl] using [FFprobeKit]
     * to check if the video stream is already encoded in H.265/HEVC.
     *
     * @param streamUrl Network URL of the manifest (e.g. .m3u8).
     * @return `true` if an H.265/HEVC video stream is detected, `false` otherwise.
     */
    fun isH265Stream(streamUrl: String): Boolean {
        if (streamUrl.isBlank()) return false
        return try {
            val session = FFprobeKit.getMediaInformation(streamUrl)
            val info = session.mediaInformation
            val isH265 = info?.streams?.any { stream ->
                stream.type.equals("video", ignoreCase = true) && isHevcCodec(stream.codec)
            } ?: false

            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Probed stream for H.265",
                data = mapOf("url" to streamUrl, "isH265" to isH265)
            )
            isH265
        } catch (e: Exception) {
            Sentry.captureException(e)
            false
        }
    }

    private fun isHevcMime(mime: String): Boolean {
        return mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) ||
            mime.contains("hevc", ignoreCase = true) ||
            mime.contains("h265", ignoreCase = true)
    }

    private fun isHevcCodec(codec: String?): Boolean {
        if (codec == null) return false
        return codec.equals("hevc", ignoreCase = true) ||
            codec.contains("h265", ignoreCase = true) ||
            codec.equals("hvc1", ignoreCase = true) ||
            codec.equals("hev1", ignoreCase = true)
    }
}
