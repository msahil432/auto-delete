package com.msahil432.multitool.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating JavaScript generation and snippet contents in [MediaDetectionJs].
 */
class MediaDetectionJsTest {

    @Test
    fun `DOM_SCAN_SCRIPT contains necessary detection routines`() {
        val script = MediaDetectionJs.DOM_SCAN_SCRIPT
        assertTrue(script.contains("querySelectorAll('video')"))
        assertTrue(script.contains("querySelectorAll('source')"))
        assertTrue(script.contains("querySelectorAll('img')"))
        assertTrue(script.contains("MultiToolBridge.onMediaDetected"))
    }

    @Test
    fun `CANVAS_DETECT_SCRIPT contains candidate search logic`() {
        val script = MediaDetectionJs.CANVAS_DETECT_SCRIPT
        assertTrue(script.contains("querySelectorAll('canvas')"))
        assertTrue(script.contains("backgroundImage"))
        assertTrue(script.contains("data-video-src"))
        assertTrue(script.contains("MultiToolBridge.onCanvasCandidates"))
    }

    @Test
    fun `canvasCaptureScript generates executable recorder script`() {
        val script = MediaDetectionJs.canvasCaptureScript("#player > canvas", 3500L)
        assertTrue(script.contains("document.querySelector('#player > canvas')"))
        assertTrue(script.contains("canvas.captureStream"))
        assertTrue(script.contains("new MediaRecorder"))
        assertTrue(script.contains("MultiToolBridge.onCanvasCaptured"))
        assertTrue(script.contains("3500"))
    }

    @Test
    fun `canvasCaptureScript escapes single quotes in selector`() {
        val script = MediaDetectionJs.canvasCaptureScript("canvas[data-id='main']", 2000L)
        assertTrue(script.contains("canvas[data-id=\\'main\\']"))
        assertFalse(script.contains("canvas[data-id='main']"))
    }

    @Test
    fun `HOOKS_AND_OBSERVER_SCRIPT includes history and navigation hooks for URL sync`() {
        val script = MediaDetectionJs.HOOKS_AND_OBSERVER_SCRIPT
        assertTrue(script.contains("MultiToolBridge.onUrlChanged"))
        assertTrue(script.contains("history.pushState"))
        assertTrue(script.contains("history.replaceState"))
        assertTrue(script.contains("popstate"))
        assertTrue(script.contains("hashchange"))
    }
}
