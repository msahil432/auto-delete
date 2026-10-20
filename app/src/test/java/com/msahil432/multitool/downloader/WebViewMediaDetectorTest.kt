package com.msahil432.multitool.downloader

import com.msahil432.multitool.data.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WebViewMediaDetector] classification, URL parsing, and deduplication logic.
 */
class WebViewMediaDetectorTest {

    private lateinit var detector: WebViewMediaDetector

    @Before
    fun setUp() {
        detector = WebViewMediaDetector()
    }

    @Test
    fun `isMediaUrl matches supported video and gif formats`() {
        assertTrue(detector.isMediaUrl("https://example.com/video.mp4"))
        assertTrue(detector.isMediaUrl("https://example.com/video.webm"))
        assertTrue(detector.isMediaUrl("https://example.com/video.mkv"))
        assertTrue(detector.isMediaUrl("https://example.com/live/stream.m3u8"))
        assertTrue(detector.isMediaUrl("https://example.com/segment.ts?token=123"))
        assertTrue(detector.isMediaUrl("https://example.com/animated.gif"))
        assertTrue(detector.isMediaUrl("https://example.com/image.webp"))
        assertTrue(detector.isMediaUrl("https://example.com/stream?mime=video/mp4"))
    }

    @Test
    fun `isMediaUrl rejects non-media web resources`() {
        assertFalse(detector.isMediaUrl("https://example.com/index.html"))
        assertFalse(detector.isMediaUrl("https://example.com/style.css"))
        assertFalse(detector.isMediaUrl("https://example.com/script.js"))
        assertFalse(detector.isMediaUrl("https://example.com/image.png"))
        assertFalse(detector.isMediaUrl("https://example.com/photo.jpg"))
    }

    @Test
    fun `classifyUrl categorizes URLs accurately`() {
        assertEquals(MediaType.VIDEO, detector.classifyUrl("https://example.com/clip.mp4"))
        assertEquals(MediaType.VIDEO, detector.classifyUrl("https://example.com/clip.webm"))
        assertEquals(MediaType.HLS_STREAM, detector.classifyUrl("https://example.com/playlist.m3u8?auth=abc"))
        assertEquals(MediaType.HLS_STREAM, detector.classifyUrl("https://example.com/live/mpegurl/stream"))
        assertEquals(MediaType.DASH_STREAM, detector.classifyUrl("https://example.com/manifest.mpd"))
        assertEquals(MediaType.GIF, detector.classifyUrl("https://example.com/reaction.gif?size=large"))
        assertEquals(MediaType.ANIMATED_WEBP, detector.classifyUrl("https://example.com/sticker.webp"))
    }

    @Test
    fun `guessMimeType resolves correct types`() {
        assertEquals("video/mp4", detector.guessMimeType("https://example.com/video.mp4?foo=bar"))
        assertEquals("video/webm", detector.guessMimeType("https://example.com/video.webm"))
        assertEquals("video/x-matroska", detector.guessMimeType("https://example.com/movie.mkv"))
        assertEquals("application/vnd.apple.mpegurl", detector.guessMimeType("https://example.com/index.m3u8"))
        assertEquals("video/mp2t", detector.guessMimeType("https://example.com/seg.ts"))
        assertEquals("application/dash+xml", detector.guessMimeType("https://example.com/dash.mpd"))
        assertEquals("image/gif", detector.guessMimeType("https://example.com/anim.gif"))
        assertEquals("image/webp", detector.guessMimeType("https://example.com/anim.webp"))
        assertNull(detector.guessMimeType("https://example.com/unknown"))
    }

    @Test
    fun `extractFileName handles clean names, query parameters and fallbacks`() {
        assertEquals("cool_video.mp4", detector.extractFileName("https://example.com/files/cool_video.mp4"))
        assertEquals("cool_video.mp4", detector.extractFileName("https://example.com/files/cool_video.mp4?token=xyz#chapter1"))
        
        val fallback = detector.extractFileName("https://example.com/stream/")
        assertTrue(fallback.startsWith("media_"))
        assertTrue(fallback.endsWith(".mp4"))
    }

    @Test
    fun `addDetectedMedia deduplicates items by URL`() {
        val media1 = DetectedMedia(
            url = "https://example.com/video.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "video.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        val media1Duplicate = DetectedMedia(
            url = "https://example.com/video.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "video.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.NETWORK_INTERCEPT
        )
        val media2 = DetectedMedia(
            url = "https://example.com/video2.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "video2.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        detector.addDetectedMedia(media1)
        detector.addDetectedMedia(media1Duplicate)
        detector.addDetectedMedia(media2)

        assertEquals(2, detector.detectedMedia.value.size)
        assertEquals("https://example.com/video.mp4", detector.detectedMedia.value[0].url)
        assertEquals("https://example.com/video2.mp4", detector.detectedMedia.value[1].url)
    }

    @Test
    fun `reset clears all detected media and state`() {
        detector.addDetectedMedia(
            DetectedMedia(
                url = "https://example.com/video.mp4",
                mimeType = "video/mp4",
                suggestedFileName = "video.mp4",
                mediaType = MediaType.VIDEO,
                detectionStrategy = DetectionStrategy.DOM_ELEMENT
            )
        )
        assertEquals(1, detector.detectedMedia.value.size)

        detector.reset()

        assertEquals(0, detector.detectedMedia.value.size)
        assertEquals(PageLoadState.IDLE, detector.pageLoadingState.value)
        assertEquals(0, detector.canvasCaptures.value.size)
    }
}
