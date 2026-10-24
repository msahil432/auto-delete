package com.msahil432.multitool.downloader

import android.content.Context
import java.io.File
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.data.TranscodeStatus
import com.msahil432.multitool.downloader.MediaListEntry
import com.msahil432.multitool.ui.screens.SizeFilter
import com.msahil432.multitool.ui.screens.SizeUnit
import com.msahil432.multitool.ui.screens.VideoDownloaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        viewModel = VideoDownloaderViewModel(repo, context, testDispatcher)
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

    @Test
    fun `page url changes update viewModel currentUrl to stay in sync`() = runTest {
        assertEquals("", viewModel.currentUrl.value)

        viewModel.detector.handlePageUrlChange("https://example.com/initial")
        // Navigating to another page updates currentUrl automatically
        viewModel.detector.handlePageUrlChange("https://example.com/videos/trending?page=2")
        testScheduler.advanceUntilIdle()
        assertEquals("https://example.com/videos/trending?page=2", viewModel.currentUrl.value)
    }

    @Test
    fun `clearAll removes records from database but preserves files on disk`() = runTest {
        val tempFile = File.createTempFile("test_clear_all", ".mp4", context.cacheDir)
        tempFile.writeText("sample data")
        assertTrue(tempFile.exists())

        val id = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/video.mp4",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(id, tempFile.absolutePath, tempFile.length())
        testScheduler.advanceUntilIdle()

        viewModel.clearAll()
        testScheduler.advanceUntilIdle()

        assertTrue(repo.allDownloads().first().isEmpty())
        assertTrue(tempFile.exists())
        tempFile.delete()
    }

    @Test
    fun `deleteAll removes records from database and deletes files from disk`() = runTest {
        val tempFile = File.createTempFile("test_delete_all", ".mp4", context.cacheDir)
        tempFile.writeText("sample data")
        assertTrue(tempFile.exists())

        val id = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/video.mp4",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(id, tempFile.absolutePath, tempFile.length())
        testScheduler.advanceUntilIdle()

        viewModel.deleteAll()
        testScheduler.advanceUntilIdle()

        assertTrue(repo.allDownloads().first().isEmpty())
        assertFalse(tempFile.exists())
    }

    @Test
    fun `filteredMedia returns all items when filters are empty`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredMedia.collect()
        }

        val media1 = DetectedMedia(
            url = "https://example.com/1.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "1.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
            estimatedSizeBytes = 5_000_000L
        )
        val media2 = DetectedMedia(
            url = "https://example.com/2.gif",
            mimeType = "image/gif",
            suggestedFileName = "2.gif",
            mediaType = MediaType.GIF,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
            estimatedSizeBytes = 1_000_000L
        )

        viewModel.detector.addDetectedMedia(media1)
        viewModel.detector.addDetectedMedia(media2)
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.filteredMedia.value.size)
    }

    @Test
    fun `filteredMedia filters by MediaType`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredMedia.collect()
        }

        val media1 = DetectedMedia(
            url = "https://example.com/1.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "1.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        val media2 = DetectedMedia(
            url = "https://example.com/2.gif",
            mimeType = "image/gif",
            suggestedFileName = "2.gif",
            mediaType = MediaType.GIF,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        viewModel.detector.addDetectedMedia(media1)
        viewModel.detector.addDetectedMedia(media2)
        testScheduler.advanceUntilIdle()

        viewModel.toggleTypeFilter(MediaType.VIDEO)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.filteredMedia.value.size)
        assertEquals("1.mp4", viewModel.filteredMedia.value[0].suggestedFileName)

        viewModel.clearFilters()
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.filteredMedia.value.size)
    }

    @Test
    fun `filteredMedia filters by minimum size`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredMedia.collect()
        }

        val smallMedia = DetectedMedia(
            url = "https://example.com/small.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "small.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
            estimatedSizeBytes = 500 * 1024L // 500 KB
        )
        val largeMedia = DetectedMedia(
            url = "https://example.com/large.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "large.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
            estimatedSizeBytes = 5 * 1024 * 1024L // 5 MB
        )
        val unknownSizeMedia = DetectedMedia(
            url = "https://example.com/unknown.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "unknown.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
            estimatedSizeBytes = -1L
        )

        viewModel.detector.addDetectedMedia(smallMedia)
        viewModel.detector.addDetectedMedia(largeMedia)
        viewModel.detector.addDetectedMedia(unknownSizeMedia)
        testScheduler.advanceUntilIdle()

        // Filter for >= 2 MB (largeMedia and unknownSizeMedia pass)
        viewModel.updateSizeFilter("2", SizeUnit.MB)
        testScheduler.advanceUntilIdle()

        val filtered = viewModel.filteredMedia.value
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.suggestedFileName == "large.mp4" })
        assertTrue(filtered.any { it.suggestedFileName == "unknown.mp4" })
    }

    @Test
    fun `groupedFilteredMedia groups duplicates by filename`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.groupedFilteredMedia.collect()
        }

        val item1 = DetectedMedia(
            url = "https://example.com/v1.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "shared.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        val item2 = DetectedMedia(
            url = "https://example.com/v2.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "shared.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        val item3 = DetectedMedia(
            url = "https://example.com/v3.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "unique.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        viewModel.detector.addDetectedMedia(item3)
        viewModel.detector.addDetectedMedia(item2)
        viewModel.detector.addDetectedMedia(item1)
        testScheduler.advanceUntilIdle()

        val entries = viewModel.groupedFilteredMedia.value
        // Should have: 1 GroupHeader ("shared.mp4", count=2), 2 Items (isGroupChild=true), 1 Item (isGroupChild=false)
        assertEquals(4, entries.size)

        val header = entries[0] as MediaListEntry.GroupHeader
        assertEquals("shared.mp4", header.fileName)
        assertEquals(2, header.count)

        val child1 = entries[1] as MediaListEntry.Item
        assertTrue(child1.isGroupChild)
        val child2 = entries[2] as MediaListEntry.Item
        assertTrue(child2.isGroupChild)

        val uniqueItem = entries[3] as MediaListEntry.Item
        assertFalse(uniqueItem.isGroupChild)
        assertEquals("unique.mp4", uniqueItem.media.suggestedFileName)
    }

    @Test
    fun `selectAllExceptExisting selects only candidates not already downloaded`() = runTest {
        val downloadedMedia = DetectedMedia(
            url = "https://example.com/existing.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "existing.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )
        val newMedia = DetectedMedia(
            url = "https://example.com/new.mp4",
            mimeType = "video/mp4",
            suggestedFileName = "new.mp4",
            mediaType = MediaType.VIDEO,
            detectionStrategy = DetectionStrategy.DOM_ELEMENT
        )

        val id = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = downloadedMedia.url,
            fileName = downloadedMedia.suggestedFileName,
            mimeType = downloadedMedia.mimeType,
            mediaType = downloadedMedia.mediaType
        )
        repo.markCompleted(id, "/tmp/existing.mp4", 1024L)
        testScheduler.advanceUntilIdle()

        viewModel.detector.addDetectedMedia(downloadedMedia)
        viewModel.detector.addDetectedMedia(newMedia)
        testScheduler.advanceUntilIdle()

        viewModel.showMediaSelection()
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.existingDownloads.value.size)
        assertTrue(viewModel.existingDownloads.value.containsKey(downloadedMedia.url))

        viewModel.selectAllExceptExisting()
        testScheduler.advanceUntilIdle()

        val selected = viewModel.selectedMedia.value
        assertEquals(1, selected.size)
        assertEquals("new.mp4", selected.first().suggestedFileName)
    }

    @Test
    fun `transcoding repository operations update status and query pending correctly`() = runTest {
        val id = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/video.mp4",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(id, "/tmp/video.mp4", 10_000_000L)

        val pending = repo.nextPendingTranscode()
        assertTrue(pending != null)
        assertEquals(id, pending?.id)
        assertEquals(TranscodeStatus.PENDING, pending?.transcodeStatus)

        repo.markTranscodeStarted(id)
        assertEquals(1, repo.transcodingCount())

        repo.markTranscodeCompleted(id, 10_000_000L, 4_500_000L, "/tmp/video_h265.mp4")
        assertEquals(0, repo.transcodingCount())

        val updated = repo.getById(id)
        assertEquals(TranscodeStatus.COMPLETED, updated?.transcodeStatus)
        assertEquals(10_000_000L, updated?.originalSizeBytes)
        assertEquals(4_500_000L, updated?.compressedSizeBytes)
        assertEquals("/tmp/video_h265.mp4", updated?.localFilePath)
    }
}


