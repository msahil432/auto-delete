package com.msahil432.multitool.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TamperAlarmTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        TamperAlarm.resetForTesting()
    }

    @After
    fun teardown() {
        TamperAlarm.resetForTesting()
    }

    @Test
    fun testAlarmStartAndStop() {
        assertFalse(TamperAlarm.isPlaying())

        TamperAlarm.start(context, maxDurationMs = 10_000L)
        assertTrue(TamperAlarm.isPlaying())

        TamperAlarm.stop()
        assertFalse(TamperAlarm.isPlaying())
    }

    @Test
    fun testAlarmMaxDurationSafetyCutoff() {
        assertFalse(TamperAlarm.isPlaying())

        var autoStopped = false
        TamperAlarm.start(context, maxDurationMs = 5000L) {
            autoStopped = true
        }
        assertTrue(TamperAlarm.isPlaying())

        // Fast-forward past max duration
        ShadowLooper.idleMainLooper(5001, TimeUnit.MILLISECONDS)

        assertFalse(TamperAlarm.isPlaying())
        assertTrue(autoStopped)
    }

    @Test
    fun testDuplicateStartIgnored() {
        TamperAlarm.start(context, maxDurationMs = 10_000L)
        assertTrue(TamperAlarm.isPlaying())

        // Second start call while active is safely ignored
        TamperAlarm.start(context, maxDurationMs = 10_000L)
        assertTrue(TamperAlarm.isPlaying())

        TamperAlarm.stop()
        assertFalse(TamperAlarm.isPlaying())
    }
}
