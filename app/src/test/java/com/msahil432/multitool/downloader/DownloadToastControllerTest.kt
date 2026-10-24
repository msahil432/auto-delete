package com.msahil432.multitool.downloader

import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [DownloadToastController] verifying message throttling,
 * immediate "all completed" emission, and failure event formatting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadToastControllerTest {

    private fun createItem(
        id: Long,
        name: String,
        status: DownloadStatus
    ) = DownloadItem(
        id = id,
        sourcePageUrl = "https://example.com",
        mediaUrl = "https://example.com/$name",
        fileName = name,
        mimeType = "video/mp4",
        mediaType = MediaType.VIDEO,
        status = status,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `first active download update emits immediately`() = runTest {
        val downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
        var currentTime = 1000L
        val controller = DownloadToastController(
            scope = this,
            downloads = downloadsFlow,
            throttleMs = 15_000L,
            clock = { currentTime }
        )

        val emitted = mutableListOf<String>()
        val job = launch {
            controller.toastMessage.collect { emitted.add(it) }
        }

        downloadsFlow.value = listOf(createItem(1, "video1.mp4", DownloadStatus.DOWNLOADING))
        advanceUntilIdle()

        assertEquals(listOf("1 download remaining"), emitted)
        job.cancel()
        controller.close()
    }

    @Test
    fun `second update within 15s is throttled and emitted after throttle window`() = runTest {
        val downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
        var currentTime = 1000L
        val controller = DownloadToastController(
            scope = this,
            downloads = downloadsFlow,
            throttleMs = 15_000L,
            clock = { currentTime }
        )

        val emitted = mutableListOf<String>()
        val job = launch {
            controller.toastMessage.collect { emitted.add(it) }
        }

        // First active download -> emits immediately
        downloadsFlow.value = listOf(createItem(1, "video1.mp4", DownloadStatus.DOWNLOADING))
        advanceUntilIdle()
        assertEquals(1, emitted.size)
        assertEquals("1 download remaining", emitted[0])

        // 5 seconds later: second download starts (within 15s throttle window)
        currentTime += 5000L
        downloadsFlow.value = listOf(
            createItem(1, "video1.mp4", DownloadStatus.DOWNLOADING),
            createItem(2, "video2.mp4", DownloadStatus.QUEUED)
        )
        advanceUntilIdle()
        // No new toast emitted yet due to throttling
        assertEquals(1, emitted.size)

        // Advance time past 15s throttle duration (10s remaining in the window)
        currentTime += 10_000L
        advanceTimeBy(10_000L)
        advanceUntilIdle()

        // Pending message should now be flushed
        assertEquals(2, emitted.size)
        assertEquals("2 downloads remaining", emitted[1])
        job.cancel()
        controller.close()
    }

    @Test
    fun `all completed message emits immediately without throttle`() = runTest {
        val downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
        var currentTime = 1000L
        val controller = DownloadToastController(
            scope = this,
            downloads = downloadsFlow,
            throttleMs = 15_000L,
            clock = { currentTime }
        )

        val emitted = mutableListOf<String>()
        val job = launch {
            controller.toastMessage.collect { emitted.add(it) }
        }

        // Start active download
        downloadsFlow.value = listOf(createItem(1, "video1.mp4", DownloadStatus.DOWNLOADING))
        advanceUntilIdle()
        assertEquals(listOf("1 download remaining"), emitted)

        // Only 2 seconds later (within throttle window), download finishes
        currentTime += 2000L
        downloadsFlow.value = listOf(createItem(1, "video1.mp4", DownloadStatus.COMPLETED))
        advanceUntilIdle()

        // "All downloads completed" must be emitted immediately
        assertEquals(2, emitted.size)
        assertEquals("All downloads completed", emitted[1])
        job.cancel()
        controller.close()
    }

    @Test
    fun `failure message formats correctly when other downloads ongoing`() = runTest {
        val downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
        var currentTime = 1000L
        val controller = DownloadToastController(
            scope = this,
            downloads = downloadsFlow,
            throttleMs = 15_000L,
            clock = { currentTime }
        )

        val emitted = mutableListOf<String>()
        val job = launch {
            controller.toastMessage.collect { emitted.add(it) }
        }

        downloadsFlow.value = listOf(
            createItem(1, "failed_clip.mp4", DownloadStatus.FAILED),
            createItem(2, "active.mp4", DownloadStatus.DOWNLOADING)
        )
        advanceUntilIdle()

        assertEquals(listOf("failed_clip.mp4 failed, 1 download ongoing"), emitted)
        job.cancel()
        controller.close()
    }
}
