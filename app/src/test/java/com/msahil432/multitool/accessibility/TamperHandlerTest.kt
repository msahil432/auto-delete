package com.msahil432.multitool.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.blocking.BlockOverlayManager
import com.msahil432.multitool.blocking.StrictModeController
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.StrictModeState
import com.msahil432.multitool.data.UnlockMethod
import com.msahil432.multitool.service.TamperAlarm
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TamperHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var settingsRepo: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.root, "test_tamper_settings.preferences_pb") }
        )
        settingsRepo = SettingsRepository(testDataStore)
        TamperAlarm.resetForTesting()
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = false),
            repo = settingsRepo
        )
    }

    @After
    fun teardown() {
        TamperAlarm.resetForTesting()
        StrictModeController.resetForTesting()
    }

    @Test
    fun testTamperSignaturesDetection() {
        assertTrue(TamperSignatures.isSettingsPackage("com.android.settings"))
        assertTrue(TamperSignatures.isSettingsPackage("com.google.android.settings"))
        assertTrue(TamperSignatures.isSettingsPackage("com.samsung.android.settings"))
        assertFalse(TamperSignatures.isSettingsPackage("com.instagram.android"))
        assertFalse(TamperSignatures.isSettingsPackage(null))
    }

    @Test
    fun testSettingsRepositoryTamperAlarmToggle() = runTest {
        assertFalse(settingsRepo.tamperAlarmEnabled.first())
        settingsRepo.setTamperAlarmEnabled(true)
        assertTrue(settingsRepo.tamperAlarmEnabled.first())
        settingsRepo.setTamperAlarmEnabled(false)
        assertFalse(settingsRepo.tamperAlarmEnabled.first())
    }

    @Test
    fun testAlarmDoesNotTriggerWhenStrictModeInactive() = runTest {
        settingsRepo.setTamperAlarmEnabled(true)
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = false),
            repo = settingsRepo
        )

        val handler = TamperHandler(
            settingsRepository = settingsRepo,
            coroutineScope = backgroundScope,
            alarmController = TamperAlarm,
            overlayManager = BlockOverlayManager
        )
        testScheduler.advanceUntilIdle()
        for (i in 1..20) {
            // We expect isTamperAlarmEnabled to be true, but isStrictModeActive to be false
            if (!handler.isStrictModeActive && handler.isTamperAlarmEnabled) break
            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()
        }
        
        assertFalse("Handler should have isStrictModeActive=false", handler.isStrictModeActive)
        assertTrue("Handler should have isTamperAlarmEnabled=true", handler.isTamperAlarmEnabled)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = "com.android.settings"
        event.className = "com.android.settings.applications.InstalledAppDetails"
        event.text.add("Multi Tool")

        handler.onEvent(service, event)
        testScheduler.advanceUntilIdle()

        assertFalse(handler.isTamperTriggered)
        assertFalse(TamperAlarm.isPlaying())
    }

    @Test
    fun testAlarmDoesNotTriggerWhenTamperAlarmDisabled() = runTest {
        settingsRepo.setTamperAlarmEnabled(false)
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = true, unlockMethod = UnlockMethod.TEXT),
            repo = settingsRepo
        )

        val handler = TamperHandler(
            settingsRepository = settingsRepo,
            coroutineScope = backgroundScope,
            alarmController = TamperAlarm,
            overlayManager = BlockOverlayManager
        )
        testScheduler.advanceUntilIdle()
        for (i in 1..20) {
            // We expect isTamperAlarmEnabled to be false, but isStrictModeActive to be true
            if (handler.isStrictModeActive && !handler.isTamperAlarmEnabled) break
            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()
        }
        
        assertTrue("Handler should have isStrictModeActive=true", handler.isStrictModeActive)
        assertFalse("Handler should have isTamperAlarmEnabled=false", handler.isTamperAlarmEnabled)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = "com.android.settings"
        event.className = "com.android.settings.applications.InstalledAppDetails"
        event.text.add("Multi Tool")

        handler.onEvent(service, event)
        testScheduler.advanceUntilIdle()

        assertFalse(handler.isTamperTriggered)
        assertFalse(TamperAlarm.isPlaying())
    }

    @Test
    fun testAlarmTriggersWhenStrictAndTamperAlarmEnabled() = runTest {
        settingsRepo.setTamperAlarmEnabled(true)
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = true, unlockMethod = UnlockMethod.TEXT),
            repo = settingsRepo
        )

        val handler = TamperHandler(
            settingsRepository = settingsRepo,
            coroutineScope = backgroundScope,
            alarmController = TamperAlarm,
            overlayManager = BlockOverlayManager
        )
        testScheduler.advanceUntilIdle()
        for (i in 1..20) {
            if (handler.isStrictModeActive && handler.isTamperAlarmEnabled) break
            testScheduler.advanceTimeBy(100)
            testScheduler.advanceUntilIdle()
        }
        
        assertTrue("Handler should have isStrictModeActive=true", handler.isStrictModeActive)
        assertTrue("Handler should have isTamperAlarmEnabled=true", handler.isTamperAlarmEnabled)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = "com.android.settings"
        event.className = "com.android.settings.applications.InstalledAppDetails"
        event.text.add("Multi Tool")

        handler.onEvent(service, event)
        testScheduler.advanceUntilIdle()

        assertTrue("Tamper triggered flag should be true", handler.isTamperTriggered)
        assertTrue("Tamper alarm should be playing", TamperAlarm.isPlaying())
    }

    @Test
    fun testNavigatingAwayStopsAlarm() = runTest {
        settingsRepo.setTamperAlarmEnabled(true)
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = true, unlockMethod = UnlockMethod.TEXT),
            repo = settingsRepo
        )

        val handler = TamperHandler(
            settingsRepository = settingsRepo,
            coroutineScope = backgroundScope,
            alarmController = TamperAlarm,
            overlayManager = BlockOverlayManager
        )
        testScheduler.advanceUntilIdle()
        for (i in 1..20) {
            if (handler.isStrictModeActive && handler.isTamperAlarmEnabled) break
            testScheduler.advanceTimeBy(100)
            testScheduler.advanceUntilIdle()
        }
        
        assertTrue("Handler should have isStrictModeActive=true", handler.isStrictModeActive)
        assertTrue("Handler should have isTamperAlarmEnabled=true", handler.isTamperAlarmEnabled)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()

        // 1. Tamper screen triggers alarm
        val tamperEvent = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        tamperEvent.packageName = "com.android.settings"
        tamperEvent.className = "com.android.settings.applications.InstalledAppDetails"
        tamperEvent.text.add("Multi Tool")

        handler.onEvent(service, tamperEvent)
        testScheduler.advanceUntilIdle()
        assertTrue("Tamper triggered flag should be true after tamper event", handler.isTamperTriggered)
        assertTrue("Tamper alarm should be playing after tamper event", TamperAlarm.isPlaying())

        // 2. User navigates away to Launcher or another app
        val navAwayEvent = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        navAwayEvent.packageName = "com.google.android.apps.nexuslauncher"

        handler.onEvent(service, navAwayEvent)
        testScheduler.advanceUntilIdle()
        for (i in 1..20) {
            if (!handler.isTamperTriggered) break
            testScheduler.advanceTimeBy(100)
            testScheduler.advanceUntilIdle()
        }
        assertFalse("Tamper triggered flag should be false after navigating away", handler.isTamperTriggered)
        assertFalse("Tamper alarm should be stopped after navigating away", TamperAlarm.isPlaying())
    }
}
