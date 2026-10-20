package com.msahil432.multitool.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [DownloadRepository] covering CRUD operations, status transitions,
 * active download counting, and bulk cleanup operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DownloadRepository
    private var fakeClock = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DownloadRepository(db.downloadDao(), clock = { fakeClock })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `enqueue persists download item with QUEUED status`() = runTest {
        val id = repo.enqueue(
            sourcePageUrl = "https://example.com/page",
            mediaUrl = "https://example.com/video.mp4",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )
        assertTrue(id > 0)

        val item = repo.getById(id)
        assertNotNull(item)
        assertEquals("https://example.com/page", item!!.sourcePageUrl)
        assertEquals("https://example.com/video.mp4", item.mediaUrl)
        assertEquals("video.mp4", item.fileName)
        assertEquals("video/mp4", item.mimeType)
        assertEquals(MediaType.VIDEO, item.mediaType)
        assertEquals(DownloadStatus.QUEUED, item.status)
        assertEquals(0, item.progressPercent)
        assertEquals(-1L, item.totalBytes)
        assertEquals(0L, item.downloadedBytes)
        assertEquals("https://example.com/thumb.jpg", item.thumbnailUrl)
        assertEquals(fakeClock, item.createdAt)
    }

    @Test
    fun `allDownloads returns items newest first`() = runTest {
        fakeClock = 1_000L
        val id1 = repo.enqueue("https://example.com", "https://example.com/1.mp4", "1.mp4", "video/mp4", MediaType.VIDEO)

        fakeClock = 2_000L
        val id2 = repo.enqueue("https://example.com", "https://example.com/2.mp4", "2.mp4", "video/mp4", MediaType.VIDEO)

        val list = repo.allDownloads().first()
        assertEquals(2, list.size)
        assertEquals(id2, list[0].id)
        assertEquals(id1, list[1].id)
    }

    @Test
    fun `activeCount and nextQueued return correct values`() = runTest {
        fakeClock = 1_000L
        val id1 = repo.enqueue("https://example.com", "https://example.com/1.mp4", "1.mp4", "video/mp4", MediaType.VIDEO)

        fakeClock = 2_000L
        val id2 = repo.enqueue("https://example.com", "https://example.com/2.mp4", "2.mp4", "video/mp4", MediaType.VIDEO)

        assertEquals(2, repo.activeCount())
        assertEquals(id1, repo.nextQueued()?.id)

        // Mark first completed
        repo.markCompleted(id1, "/path/1.mp4", 1024L)

        assertEquals(1, repo.activeCount())
        assertEquals(id2, repo.nextQueued()?.id)
    }

    @Test
    fun `updateProgress transitions status to DOWNLOADING and updates byte counts`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/video.mp4", "video.mp4", "video/mp4", MediaType.VIDEO)

        fakeClock = 1_500L
        repo.updateProgress(id, downloadedBytes = 500L, totalBytes = 1000L, progressPercent = 50)

        val item = repo.getById(id)
        assertNotNull(item)
        assertEquals(DownloadStatus.DOWNLOADING, item!!.status)
        assertEquals(500L, item.downloadedBytes)
        assertEquals(1000L, item.totalBytes)
        assertEquals(50, item.progressPercent)
        assertEquals(fakeClock, item.updatedAt)
    }

    @Test
    fun `markCompleted updates status, path, and progress to 100 percent`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/video.mp4", "video.mp4", "video/mp4", MediaType.VIDEO)

        fakeClock = 2_000L
        repo.markCompleted(id, "/storage/downloads/video.mp4", 5000L)

        val item = repo.getById(id)
        assertNotNull(item)
        assertEquals(DownloadStatus.COMPLETED, item!!.status)
        assertEquals("/storage/downloads/video.mp4", item.localFilePath)
        assertEquals(100, item.progressPercent)
        assertEquals(5000L, item.downloadedBytes)
        assertEquals(5000L, item.totalBytes)
    }

    @Test
    fun `markFailed updates status and error message`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/video.mp4", "video.mp4", "video/mp4", MediaType.VIDEO)

        fakeClock = 2_000L
        repo.markFailed(id, "HTTP 404 Not Found")

        val item = repo.getById(id)
        assertNotNull(item)
        assertEquals(DownloadStatus.FAILED, item!!.status)
        assertEquals("HTTP 404 Not Found", item.errorMessage)
    }

    @Test
    fun `retry resets FAILED item to QUEUED with cleared errors`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/video.mp4", "video.mp4", "video/mp4", MediaType.VIDEO)
        repo.markFailed(id, "Network timeout")

        fakeClock = 3_000L
        repo.retry(id)

        val item = repo.getById(id)
        assertNotNull(item)
        assertEquals(DownloadStatus.QUEUED, item!!.status)
        assertNull(item.errorMessage)
        assertEquals(0, item.progressPercent)
        assertEquals(0L, item.downloadedBytes)
        assertEquals(fakeClock, item.updatedAt)
    }

    @Test
    fun `cancel transitions status to CANCELLED`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/video.mp4", "video.mp4", "video/mp4", MediaType.VIDEO)

        repo.cancel(id)

        val item = repo.getById(id)
        assertNotNull(item)
        assertEquals(DownloadStatus.CANCELLED, item!!.status)
    }

    @Test
    fun `deleteDownload removes the record`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/video.mp4", "video.mp4", "video/mp4", MediaType.VIDEO)

        repo.deleteDownload(id)

        assertNull(repo.getById(id))
    }

    @Test
    fun `clearCompleted removes only completed items`() = runTest {
        val id1 = repo.enqueue("https://example.com", "https://example.com/1.mp4", "1.mp4", "video/mp4", MediaType.VIDEO)
        val id2 = repo.enqueue("https://example.com", "https://example.com/2.mp4", "2.mp4", "video/mp4", MediaType.VIDEO)
        repo.markCompleted(id1, "/path/1.mp4", 100L)

        repo.clearCompleted()

        assertNull(repo.getById(id1))
        assertNotNull(repo.getById(id2))
    }

    @Test
    fun `clearAll removes all records`() = runTest {
        repo.enqueue("https://example.com", "https://example.com/1.mp4", "1.mp4", "video/mp4", MediaType.VIDEO)
        repo.enqueue("https://example.com", "https://example.com/2.mp4", "2.mp4", "video/mp4", MediaType.VIDEO)

        repo.clearAll()

        assertEquals(0, repo.allDownloads().first().size)
    }
}
