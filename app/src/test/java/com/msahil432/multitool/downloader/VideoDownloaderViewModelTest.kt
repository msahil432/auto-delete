package com.msahil432.multitool.downloader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.ui.screens.VideoDownloaderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoDownloaderViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DownloadRepository
    private lateinit var viewModel: VideoDownloaderViewModel
    private lateinit var context: Context

    @Before
    fun setUp() {
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

        val list = repo.allDownloads().first()
        assertEquals(1, list.size)
        assertEquals("https://example.com/page", list[0].sourcePageUrl)
        assertEquals("https://example.com/video.mp4", list[0].mediaUrl)
        assertEquals(DownloadStatus.QUEUED, list[0].status)
    }
}
