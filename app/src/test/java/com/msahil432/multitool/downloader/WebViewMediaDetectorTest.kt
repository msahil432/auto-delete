package com.msahil432.multitool.downloader

import com.msahil432.multitool.data.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [WebViewMediaDetector] classification, URL parsing, deduplication,
 * ordering (most recent first), and asynchronous file size fetching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewMediaDetectorTest {

    private lateinit var detector: WebViewMediaDetector

    @Before
    fun setUp() {
        detector = WebViewMediaDetector(sizeFetcher = { -1L })
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
    fun `addDetectedMedia deduplicates items by URL and places most recent on top`() {
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
        // Most recently detected media (media2) is at the top of the list
        assertEquals("https://example.com/video2.mp4", detector.detectedMedia.value[0].url)
        assertEquals("https://example.com/video.mp4", detector.detectedMedia.value[1].url)
    }

    @Test
    fun `addDetectedMedia puts newest detected items consecutively on top`() {
        val v1 = DetectedMedia(url = "https://example.com/v1.mp4", mimeType = "video/mp4", suggestedFileName = "1.mp4", mediaType = MediaType.VIDEO, detectionStrategy = DetectionStrategy.DOM_ELEMENT)
        val v2 = DetectedMedia(url = "https://example.com/v2.mp4", mimeType = "video/mp4", suggestedFileName = "2.mp4", mediaType = MediaType.VIDEO, detectionStrategy = DetectionStrategy.DOM_ELEMENT)
        val v3 = DetectedMedia(url = "https://example.com/v3.mp4", mimeType = "video/mp4", suggestedFileName = "3.mp4", mediaType = MediaType.VIDEO, detectionStrategy = DetectionStrategy.DOM_ELEMENT)

        detector.addDetectedMedia(v1)
        detector.addDetectedMedia(v2)
        detector.addDetectedMedia(v3)

        val list = detector.detectedMedia.value
        assertEquals(3, list.size)
        assertEquals("https://example.com/v3.mp4", list[0].url)
        assertEquals("https://example.com/v2.mp4", list[1].url)
        assertEquals("https://example.com/v1.mp4", list[2].url)
    }

    @Test
    fun `asynchronous size fetch updates size and status upon success`() = runTest {
        val testScope = TestScope(testScheduler)
        val asyncDetector = WebViewMediaDetector(
            coroutineScope = testScope,
            sizeFetcher = { url ->
                if (url.contains("video1")) 15_400_000L else 512_000L
            }
        )

        val media = DetectedMedia(
            url = "https://example.com/video1.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "video1.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        // Item is placed in the list immediately
        asyncDetector.addDetectedMedia(media)
        assertEquals(1, asyncDetector.detectedMedia.value.size)
        assertEquals(SizeFetchStatus.FETCHING, asyncDetector.detectedMedia.value[0].sizeFetchStatus)
        assertEquals("Fetching size...", asyncDetector.detectedMedia.value[0].formattedSize)

        // Drain background size fetch job
        testScheduler.advanceUntilIdle()

        val updated = asyncDetector.detectedMedia.value[0]
        assertEquals(SizeFetchStatus.SUCCESS, updated.sizeFetchStatus)
        assertEquals(15_400_000L, updated.estimatedSizeBytes)
        assertEquals("14.7 MB", updated.formattedSize)
    }

    @Test
    fun `asynchronous size fetch sets FAILED and shows Cannot fetch the size on error`() = runTest {
        val testScope = TestScope(testScheduler)
        val asyncDetector = WebViewMediaDetector(
            coroutineScope = testScope,
            sizeFetcher = { _ ->
                throw IOException("Network unreachable")
            }
        )

        val media = DetectedMedia(
            url = "https://example.com/failing.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "failing.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        asyncDetector.addDetectedMedia(media)
        assertEquals(1, asyncDetector.detectedMedia.value.size)

        testScheduler.advanceUntilIdle()

        val updated = asyncDetector.detectedMedia.value[0]
        assertEquals(SizeFetchStatus.FAILED, updated.sizeFetchStatus)
        assertEquals(-1L, updated.estimatedSizeBytes)
        assertEquals("Cannot fetch the size", updated.formattedSize)
    }

    @Test
    fun `formatFileSize formats sizes and handles errors accurately`() {
        assertEquals("Cannot fetch the size", formatFileSize(-1L))
        assertEquals("Cannot fetch the size", formatFileSize(-500L))
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("500 B", formatFileSize(500L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("14.7 MB", formatFileSize(15_400_000L))
        assertEquals("2.00 GB", formatFileSize(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `reset clears all detected media and cancels active fetches`() = runTest {
        val testScope = TestScope(testScheduler)
        val asyncDetector = WebViewMediaDetector(
            coroutineScope = testScope,
            sizeFetcher = {
                // Return size
                20_000_000L
            }
        )

        asyncDetector.addDetectedMedia(
            DetectedMedia(
                url = "https://example.com/video.mp4",
                mimeType = "video/mp4",
                suggestedFileName = "video.mp4",
                mediaType = MediaType.VIDEO,
                detectionStrategy = DetectionStrategy.DOM_ELEMENT
            )
        )
        assertEquals(1, asyncDetector.detectedMedia.value.size)

        asyncDetector.reset()

        assertEquals(0, asyncDetector.detectedMedia.value.size)
        assertEquals(PageLoadState.IDLE, asyncDetector.pageLoadingState.value)
        assertEquals(0, asyncDetector.canvasCaptures.value.size)
        assertEquals("", asyncDetector.activeUrl.value)
    }

    @Test
    fun `handlePageUrlChange updates activeUrl for valid web URLs`() {
        assertEquals("", detector.activeUrl.value)

        detector.handlePageUrlChange("https://example.com/videos")
        assertEquals("https://example.com/videos", detector.activeUrl.value)

        detector.handlePageUrlChange("http://example.com/another-page?ref=home")
        assertEquals("http://example.com/another-page?ref=home", detector.activeUrl.value)
    }

    @Test
    fun `handlePageUrlChange ignores about blank and non-http URLs`() {
        detector.handlePageUrlChange("https://example.com/valid")
        assertEquals("https://example.com/valid", detector.activeUrl.value)

        detector.handlePageUrlChange("about:blank")
        assertEquals("https://example.com/valid", detector.activeUrl.value)

        detector.handlePageUrlChange("javascript:void(0)")
        assertEquals("https://example.com/valid", detector.activeUrl.value)

        detector.handlePageUrlChange("   ")
        assertEquals("https://example.com/valid", detector.activeUrl.value)

        detector.handlePageUrlChange(null)
        assertEquals("https://example.com/valid", detector.activeUrl.value)
    }
}

