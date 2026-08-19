package com.msahil432.multitool.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BootReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        try {
            val config = androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {
            // Already initialized in test environment
        }
    }

    @Test
    fun testOnReceiveBootCompletedStartsServiceAndSchedulesWork() {
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        // Verify FileMonitorService was requested to start
        val shadowApp = shadowOf(context as android.app.Application)
        val nextService = shadowApp.nextStartedService
        assertNotNull(nextService)
        assertEquals(FileMonitorService::class.java.name, nextService.component?.className)

        // Verify WorkManager scheduled periodic work
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("usage_collector_periodic")
            .get()
        assertNotNull(workInfos)
    }

    @Test
    fun testOnReceiveOtherActionDoesNothing() {
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)

        receiver.onReceive(context, intent)

        val shadowApp = shadowOf(context as android.app.Application)
        val nextService = shadowApp.nextStartedService
        org.junit.Assert.assertNull(nextService)
    }
}
