package com.msahil432.multitool.downloader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.ui.screens.VideoDownloaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [VideoDownloaderViewModel] covering UI state flows, selection handling,
 * and download delegation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoDownloaderViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DownloadRepository
    private lateinit var viewModel: VideoDownloaderViewModel
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DownloadRepository(db.downloadDao())
        viewModel = VideoDownloaderViewModel(repo, context)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `onUrlChanged updates currentUrl state`() {
        assertEquals("", viewModel.currentUrl.value)
        viewModel.onUrlChanged("https://example.com/watch?v=123")
        assertEquals("https://example.com/watch?v=123", viewModel.currentUrl.value)
    }

    @Test
    fun `selection dialog open and dismiss transitions`() {
        assertFalse(viewModel.showSelectionDialog.value)

        viewModel.showMediaSelection()
        assertTrue(viewModel.showSelectionDialog.value)

        viewModel.dismissMediaSelection()
        assertFalse(viewModel.showSelectionDialog.value)
    }

    @Test
    fun `toggleMediaSelection toggles items in selection set`() {
        val media = DetectedMedia(
            url = "https://example.com/1.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "1.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        viewModel.toggleMediaSelection(media)
        assertTrue(viewModel.selectedMedia.value.contains(media))

        viewModel.toggleMediaSelection(media)
        assertFalse(viewModel.selectedMedia.value.contains(media))
    }

    @Test
    fun `selectAll and deselectAll update selection set`() {
        val media1 = DetectedMedia(
            url = "https://example.com/1.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "1.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        val media2 = DetectedMedia(
            url = "https://example.com/2.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "2.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        viewModel.detector.addDetectedMedia(media1)
        viewModel.detector.addDetectedMedia(media2)

        viewModel.selectAll()
        assertEquals(2, viewModel.selectedMedia.value.size)

        viewModel.deselectAll()
        assertEquals(0, viewModel.selectedMedia.value.size)
    }

    @Test
    fun `downloadSelected enqueues items into repository`() = runTest {
        val media = DetectedMedia(
            url = "https://example.com/video.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "video.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        viewModel.onUrlChanged("https://example.com/page")
        viewModel.toggleMediaSelection(media)
        viewModel.downloadSelected()
        // Drain the Dispatchers.IO coroutine launched inside downloadSelected before asserting.
        advanceUntilIdle()

        val list = repo.allDownloads().first()
        assertEquals(1, list.size)
        assertEquals("https://example.com/page", list[0].sourcePageUrl)
        assertEquals("https://example.com/video.mp4", list[0].mediaUrl)
        assertEquals(DownloadStatus.QUEUED, list[0].status)
    }

    @Test
    fun `clearUrl resets currentUrl and detector state`() {
        val media = DetectedMedia(
            url = "https://example.com/video.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "video.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        viewModel.onUrlChanged("https://example.com/test")
        viewModel.detector.addDetectedMedia(media)

        assertEquals("https://example.com/test", viewModel.currentUrl.value)
        assertEquals(1, viewModel.detector.detectedMedia.value.size)

        viewModel.clearUrl()

        assertEquals("", viewModel.currentUrl.value)
        assertEquals(0, viewModel.detector.detectedMedia.value.size)
        assertEquals(com.msahil432.multitool.downloader.PageLoadState.IDLE, viewModel.detector.pageLoadingState.value)
    }

    @Test
    fun `isMediaUrl accurately recognizes Reddit and RedGIFs streaming assets`() {
        val detector = viewModel.detector

        // Reddit URLs
        assertTrue(detector.isMediaUrl("https://v.redd.it/abc123xyz/HLSPlaylist.m3u8"))
        assertTrue(detector.isMediaUrl("https://v.redd.it/abc123xyz/HLSPlaylist.m3u8?source=fallback"))
        assertTrue(detector.isMediaUrl("https://v.redd.it/abc123xyz/DASH_720.mp4"))
        assertTrue(detector.isMediaUrl("https://v.redd.it/abc123xyz/DASHPlaylist.mpd"))
        assertTrue(detector.isMediaUrl("https://v.redd.it/abc123xyz/DASH_1080"))

        // Audio tracks on Reddit should be skipped to prioritize video/HLS
        assertFalse(detector.isMediaUrl("https://v.redd.it/abc123xyz/DASH_AUDIO_128.mp4"))

        // RedGIFs URLs
        assertTrue(detector.isMediaUrl("https://media.redgifs.com/SampleVideoId.mp4"))
        assertTrue(detector.isMediaUrl("https://media.redgifs.com/SampleVideoId-mobile.mp4"))
        assertTrue(detector.isMediaUrl("https://thumbs2.redgifs.com/SampleVideoId.mp4"))
        assertTrue(detector.isMediaUrl("https://media.redgifs.com/SampleVideoId.m3u8"))

        // Non-media URLs
        assertFalse(detector.isMediaUrl("https://www.reddit.com/r/technology/"))
        assertFalse(detector.isMediaUrl("https://www.redgifs.com/watch/sample"))
        assertFalse(detector.isMediaUrl("https://events.reddit.com/telemetry"))
    }

    @Test
    fun `classifyUrl and extractFileName produce correct metadata for Reddit and RedGIFs`() {
        val detector = viewModel.detector

        val redditHls = "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8"
        assertEquals(MediaType.HLS_STREAM, detector.classifyUrl(redditHls))
        assertEquals("reddit_abc123xyz_hls.mp4", detector.extractFileName(redditHls))

        val redditDash = "https://v.redd.it/abc123xyz/DASH_720.mp4"
        assertEquals(MediaType.VIDEO, detector.classifyUrl(redditDash))
        assertEquals("reddit_abc123xyz_720.mp4", detector.extractFileName(redditDash))

        val redgifsMp4 = "https://media.redgifs.com/DelightfulSample-mobile.mp4"
        assertEquals(MediaType.VIDEO, detector.classifyUrl(redgifsMp4))
        assertEquals("redgifs_DelightfulSample.mp4", detector.extractFileName(redgifsMp4))
    }

    @Test
    fun `toggleMediaSelection matches by url and updates when detector emits new instance`() = runTest {
        val media = DetectedMedia(
            url = "https://example.com/test_video.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "test_video.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
            estimatedSizeBytes = -1L,
            sizeFetchStatus = SizeFetchStatus.FETCHING
        )

        viewModel.detector.addDetectedMedia(media)
        viewModel.toggleMediaSelection(media)
        assertEquals(1, viewModel.selectedMedia.value.size)

        // Select again toggles off
        viewModel.toggleMediaSelection(media)
        assertEquals(0, viewModel.selectedMedia.value.size)
    }
}

