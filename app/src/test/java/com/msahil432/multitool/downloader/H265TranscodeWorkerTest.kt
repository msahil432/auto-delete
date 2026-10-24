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

    @Test
    fun `markTranscodeSkippedAlreadyH265 updates status to ALREADY_H265`() = runTest {
        val videoId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.mp4",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(videoId, "/tmp/test.mp4", 4000L)

        repo.markTranscodeSkippedAlreadyH265(videoId, 4000L)

        val item = repo.getById(videoId)
        assertEquals(TranscodeStatus.ALREADY_H265, item?.transcodeStatus)
        assertEquals(4000L, item?.originalSizeBytes)
        assertEquals(4000L, item?.compressedSizeBytes)
        assertEquals("/tmp/test.mp4", item?.localFilePath)
    }

    @Test
    fun `markTranscodeSkippedLarger sets SKIPPED_LARGER and preserves original path and updates sizes`() = runTest {
        val videoId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.mp4",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(videoId, "/tmp/original.mp4", 5000L)

        repo.markTranscodeSkippedLarger(videoId, originalBytes = 5000L, compressedBytes = 6500L)

        val item = repo.getById(videoId)
        assertEquals(TranscodeStatus.SKIPPED_LARGER, item?.transcodeStatus)
        assertEquals(5000L, item?.originalSizeBytes)
        assertEquals(6500L, item?.compressedSizeBytes)
        assertEquals("/tmp/original.mp4", item?.localFilePath)
    }

    @Test
    fun `markTranscodeFailed stores error message and sets status FAILED`() = runTest {
        val videoId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/test.mp4",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )
        repo.markCompleted(videoId, "/tmp/original.mp4", 5000L)

        repo.markTranscodeFailed(videoId, "FFmpeg H265 failed with return code 1")

        val item = repo.getById(videoId)
        assertEquals(TranscodeStatus.FAILED, item?.transcodeStatus)
        assertEquals("FFmpeg H265 failed with return code 1", item?.errorMessage)
        assertEquals("/tmp/original.mp4", item?.localFilePath)
    }

    @Test
    fun `resetOrphanedTranscodes resets stuck TRANSCODING back to PENDING`() = runTest {
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

        val resetCount = repo.resetOrphanedTranscodes()
        assertEquals(1, resetCount)
        assertEquals(0, repo.transcodingCount())

        val item = repo.getById(videoId)
        assertEquals(TranscodeStatus.PENDING, item?.transcodeStatus)

        // Now nextPendingTranscode picks it up again
        val next = repo.nextPendingTranscode()
        assertEquals(videoId, next?.id)
    }

    @Test
    fun `markHlsCompleted sets transcodeStatus and compressedSizeBytes`() = runTest {
        val hlsId = repo.enqueue(
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/stream.m3u8",
            fileName = "stream.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.HLS_STREAM
        )

        repo.markHlsCompleted(
            id = hlsId,
            localFilePath = "/storage/downloads/stream.mp4",
            totalBytes = 8000L,
            transcodeStatus = TranscodeStatus.COMPLETED
        )

        val item = repo.getById(hlsId)
        assertEquals(TranscodeStatus.COMPLETED, item?.transcodeStatus)
        assertEquals(8000L, item?.totalBytes)
        assertEquals(8000L, item?.compressedSizeBytes)
        assertEquals("/storage/downloads/stream.mp4", item?.localFilePath)
    }

    @Test
    fun `resetFailedAndOrphanedTranscodes resets both FAILED and TRANSCODING back to PENDING`() = runTest {
        val id1 = repo.enqueue("https://example.com", "https://example.com/1.mp4", "1.mp4", "video/mp4", MediaType.VIDEO)
        repo.markCompleted(id1, "/tmp/1.mp4", 5000L)
        repo.markTranscodeStarted(id1)
        repo.markTranscodeFailed(id1, "Encoder error")

        val id2 = repo.enqueue("https://example.com", "https://example.com/2.mp4", "2.mp4", "video/mp4", MediaType.VIDEO)
        repo.markCompleted(id2, "/tmp/2.mp4", 6000L)
        repo.markTranscodeStarted(id2)

        val id3 = repo.enqueue("https://example.com", "https://example.com/3.mp4", "3.mp4", "video/mp4", MediaType.VIDEO)
        repo.markCompleted(id3, "/tmp/3.mp4", 7000L)
        repo.markTranscodeCompleted(id3, 7000L, 3500L, "/tmp/3_h265.mp4")

        val resetCount = repo.resetFailedAndOrphanedTranscodes()
        assertEquals(2, resetCount)

        val item1 = repo.getById(id1)
        assertEquals(TranscodeStatus.PENDING, item1?.transcodeStatus)
        assertNull(item1?.errorMessage)

        val item2 = repo.getById(id2)
        assertEquals(TranscodeStatus.PENDING, item2?.transcodeStatus)

        val item3 = repo.getById(id3)
        assertEquals(TranscodeStatus.COMPLETED, item3?.transcodeStatus)
    }

    @Test
    fun `retryTranscode resets specific video transcode back to PENDING and clears errorMessage`() = runTest {
        val id = repo.enqueue("https://example.com", "https://example.com/test.mp4", "test.mp4", "video/mp4", MediaType.VIDEO)
        repo.markCompleted(id, "/tmp/test.mp4", 5000L)
        repo.markTranscodeFailed(id, "FFmpeg failed")

        repo.retryTranscode(id)

        val item = repo.getById(id)
        assertEquals(TranscodeStatus.PENDING, item?.transcodeStatus)
        assertNull(item?.errorMessage)
    }

    @Test
    fun `calculateTargetBitrate returns fallback 2000k for dummy file`() {
        val bitrate = H265TranscodeWorker.calculateTargetBitrate(java.io.File("/dummy/dummy.mp4"))
        assertEquals("2000k", bitrate)
    }
}
