package com.msahil432.multitool.downloader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.data.TranscodeStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for H.265 transcoding scheduling logic and repository state tracking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class H265TranscodeWorkerTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DownloadRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DownloadRepository(db.downloadDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `nextPendingTranscode returns earliest completed video with PENDING transcodeStatus`() = runTest {
        // Enqueue 2 items: 1 GIF (NOT_APPLICABLE) and 1 VIDEO (PENDING)
        val gifId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.gif",
            fileName = "test.gif",
            mimeType = "image/gif",
            mediaType = MediaType.GIF
        )
        repo.markCompleted(gifId, "/tmp/test.gif", 500L)

        val videoId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.mp4",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(videoId, "/tmp/test.mp4", 5000L)

        val next = repo.nextPendingTranscode()
        assertEquals(videoId, next?.id)
        assertEquals(TranscodeStatus.PENDING, next?.transcodeStatus)
    }

    @Test
    fun `when item is actively transcoding nextPendingTranscode ignores it and transcode count is positive`() = runTest {
        val videoId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.mp4",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(videoId, "/tmp/test.mp4", 5000L)

        repo.markTranscodeStarted(videoId)
        assertEquals(1, repo.transcodingCount())

        // Since it's TRANSCODING and not PENDING, nextPendingTranscode won't pick it again
        val next = repo.nextPendingTranscode()
        assertNull(next)
    }

    @Test
    fun `failed transcode preserves original path and sets status to FAILED`() = runTest {
        val videoId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.mp4",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(videoId, "/tmp/original.mp4", 5000L)

        repo.markTranscodeStarted(videoId)
        repo.markTranscodeFailed(videoId)

        assertEquals(0, repo.transcodingCount())
        val item = repo.getById(videoId)
        assertEquals(TranscodeStatus.FAILED, item?.transcodeStatus)
        assertEquals("/tmp/original.mp4", item?.localFilePath)
    }
}
