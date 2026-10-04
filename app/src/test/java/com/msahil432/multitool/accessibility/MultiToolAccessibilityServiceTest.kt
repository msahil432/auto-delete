package com.msahil432.multitool.accessibility

import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying accessibility event routing and filtering in [MultiToolAccessibilityService],
 * ensuring internal overlays and system UI transitions do not trigger false foreground app switches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MultiToolAccessibilityServiceTest {

    private lateinit var service: MultiToolAccessibilityService

    @Before
    fun setUp() {
        service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
    }

    @Test
    fun `regular app window updates foreground package`() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.instagram.android"
            className = "com.instagram.mainactivity.MainActivity"
        }

        service.onAccessibilityEvent(event)

        assertEquals("com.instagram.android", ForegroundAppState.currentPackage.value)
    }

    @Test
    fun `system UI transition is ignored and does not update foreground package`() {
        // First establish active package
        ForegroundAppState.update("com.instagram.android")

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.android.systemui"
            className = "com.android.systemui.statusbar.phone.StatusBarWindowView"
        }

        service.onAccessibilityEvent(event)

        assertEquals("com.instagram.android", ForegroundAppState.currentPackage.value)
    }

    @Test
    fun `internal overlay view from MultiTool is ignored and does not update foreground package`() {
        // Target app active
        ForegroundAppState.update("com.instagram.android")

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = service.packageName
            className = "androidx.compose.ui.platform.ComposeView"
        }

        service.onAccessibilityEvent(event)

        // Must remain instagram, not changed to MultiTool
        assertEquals("com.instagram.android", ForegroundAppState.currentPackage.value)
    }

    @Test
    fun `internal MainActivity from MultiTool updates foreground package`() {
        ForegroundAppState.update("com.instagram.android")

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = service.packageName
            className = "com.msahil432.multitool.MainActivity"
        }

        service.onAccessibilityEvent(event)

        assertEquals(service.packageName, ForegroundAppState.currentPackage.value)
    }

    @Test
    fun `internal BlockActivity from MultiTool updates foreground package`() {
        ForegroundAppState.update("com.instagram.android")

        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = service.packageName
            className = "com.msahil432.multitool.blocking.BlockActivity"
        }

        service.onAccessibilityEvent(event)

        assertEquals(service.packageName, ForegroundAppState.currentPackage.value)
    }

    @Test
    fun `null event does not throw and preserves state`() {
        ForegroundAppState.update("com.instagram.android")

        service.onAccessibilityEvent(null)

        assertEquals("com.instagram.android", ForegroundAppState.currentPackage.value)
    }
}
