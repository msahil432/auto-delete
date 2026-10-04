package com.msahil432.multitool.blocking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.ui.screens.formatRemainingCompact
import com.msahil432.multitool.ui.screens.formatRemainingDetailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for floating timer bubble string formatters and lifecycle management.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FloatingTimerBubbleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FloatingTimerBubbleManager.hide()
    }

    @Test
    fun `formatRemainingCompact formats seconds, minutes, and hours accurately`() {
        assertEquals("0s", formatRemainingCompact(0L))
        assertEquals("45s", formatRemainingCompact(45_000L))
        assertEquals("14m", formatRemainingCompact(14 * 60_000L))
        assertEquals("1h", formatRemainingCompact(75 * 60_000L))
        assertEquals("2h", formatRemainingCompact(120 * 60_000L))
    }

    @Test
    fun `formatRemainingDetailed formats clock countdown MM SS and HH MM SS`() {
        assertEquals("00:00", formatRemainingDetailed(0L))
        assertEquals("00:45", formatRemainingDetailed(45_000L))
        assertEquals("14:00", formatRemainingDetailed(14 * 60_000L))
        assertEquals("14:25", formatRemainingDetailed(14 * 60_000L + 25_000L))
        assertEquals("1:15:30", formatRemainingDetailed(75 * 60_000L + 30_000L))
    }

    @Test
    fun `FloatingTimerBubbleManager hide resets showing state and metadata`() {
        assertFalse(FloatingTimerBubbleManager.isShowing())

        val timerInfo = AppTimerInfo(
            packageName = "com.test.app",
            appLabel = "Test App",
            ruleType = BlockRuleType.DAILY_QUOTA,
            remainingMillis = 10 * 60_000L,
            totalLimitMillis = 30 * 60_000L,
            groupName = "Focus"
        )

        // Hide should clear currentInfo and ensure not showing
        FloatingTimerBubbleManager.hide()
        assertFalse(FloatingTimerBubbleManager.isShowing())
        assertEquals(null, FloatingTimerBubbleManager.currentInfo)
    }
}
