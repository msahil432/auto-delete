package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
@Config(sdk = [36])
class ShortFormHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var blockingRepo: BlockingRepository
    private lateinit var usageRepo: UsageRepository
    private lateinit var settingsRepo: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        blockingRepo = BlockingRepository(db.blockingDao())
        usageRepo = UsageRepository(db.usageDao())

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.root, "test_settings.preferences_pb") }
        )
        settingsRepo = SettingsRepository(testDataStore)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testShortFormSignaturesTable() {
        assertTrue(ShortFormSignatures.isSupportedPackage(ShortFormSignatures.PKG_YOUTUBE))
        assertTrue(ShortFormSignatures.isSupportedPackage(ShortFormSignatures.PKG_INSTAGRAM))
        assertTrue(ShortFormSignatures.isSupportedPackage(ShortFormSignatures.PKG_FACEBOOK))
        assertFalse(ShortFormSignatures.isSupportedPackage("com.random.app"))
        assertFalse(ShortFormSignatures.isSupportedPackage(null))

        val ytSigs = ShortFormSignatures.SHORT_FORM[ShortFormSignatures.PKG_YOUTUBE]
        assertTrue(ytSigs?.contains("reel_recycler") == true)

        val igSigs = ShortFormSignatures.SHORT_FORM[ShortFormSignatures.PKG_INSTAGRAM]
        assertTrue(igSigs?.contains("clips_viewer_view_pager") == true)

        val fbSigs = ShortFormSignatures.SHORT_FORM[ShortFormSignatures.PKG_FACEBOOK]
        assertTrue(fbSigs?.contains("reels_viewer") == true)
    }

    @Test
    fun testSettingsRepositoryShortFormToggles() = runTest {
        assertFalse(settingsRepo.blockYtShorts.first())
        assertFalse(settingsRepo.blockIgReels.first())
        assertFalse(settingsRepo.blockFbReels.first())

        settingsRepo.setBlockYtShorts(true)
        assertTrue(settingsRepo.blockYtShorts.first())

        settingsRepo.setBlockIgReels(true)
        assertTrue(settingsRepo.blockIgReels.first())

        settingsRepo.setBlockFbReels(true)
        assertTrue(settingsRepo.blockFbReels.first())

        settingsRepo.setBlockYtShorts(false)
        assertFalse(settingsRepo.blockYtShorts.first())
    }

    @Test
    fun testHandlerIgnoresUnsupportedPackage() = runTest {
        val handler = ShortFormHandler(
            settingsRepository = settingsRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo,
            coroutineScope = backgroundScope
        )

        settingsRepo.setBlockYtShorts(true)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = "com.unrelated.app"

        handler.onEvent(service, event)

        val timelineEvents = usageRepo.timelineToday().first()
        assertTrue(timelineEvents.isEmpty())
    }

    @Test
    fun testHandlerIgnoresWhenToggleDisabled() = runTest {
        val handler = ShortFormHandler(
            settingsRepository = settingsRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo,
            coroutineScope = backgroundScope
        )

        // All toggles are false by default
        assertFalse(handler.isBlockingEnabledForPackage(ShortFormSignatures.PKG_YOUTUBE))

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = ShortFormSignatures.PKG_YOUTUBE
        event.className = "com.google.android.apps.youtube.app.extensions.reel.watch.activity.ReelWatchActivity"

        handler.onEvent(service, event)

        val timelineEvents = usageRepo.timelineToday().first()
        assertTrue(timelineEvents.isEmpty())
    }

    @Test
    fun testHandlerDetectsAndLogsWhenToggleEnabled() = runTest {
        settingsRepo.setBlockYtShorts(true)

        val handler = ShortFormHandler(
            settingsRepository = settingsRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo,
            coroutineScope = backgroundScope
        )

        assertTrue(handler.isBlockingEnabledForPackage(ShortFormSignatures.PKG_YOUTUBE))

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = ShortFormSignatures.PKG_YOUTUBE
        event.className = "com.google.android.apps.youtube.app.extensions.reel.watch.activity.ReelWatchActivity"

        handler.onEvent(service, event)

        val timelineEvents = usageRepo.timelineToday().first()
        assertEquals(1, timelineEvents.size)
        assertEquals(ShortFormSignatures.PKG_YOUTUBE, timelineEvents[0].packageName)
        assertEquals(TimelineEventType.BLOCK_INTERCEPT, timelineEvents[0].eventType)
    }

    @Test
    fun testCooldownPreventsRapidActionSpam() = runTest {
        settingsRepo.setBlockIgReels(true)

        var currentTime = 1000L
        val handler = ShortFormHandler(
            settingsRepository = settingsRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo,
            coroutineScope = backgroundScope,
            cooldownMs = 1000L,
            clock = { currentTime }
        )

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = ShortFormSignatures.PKG_INSTAGRAM
        event.className = "com.instagram.clips.viewer.ClipsViewerActivity"

        // First trigger
        handler.onEvent(service, event)
        val timeline1 = usageRepo.timelineToday().first()
        assertEquals(1, timeline1.size)

        // Immediate second event (within cooldown)
        currentTime = 1200L
        handler.onEvent(service, event)
        val timeline2 = usageRepo.timelineToday().first()
        assertEquals(1, timeline2.size) // No new log

        // Third event after cooldown
        currentTime = 2500L
        handler.onEvent(service, event)
        val timeline3 = usageRepo.timelineToday().first()
        assertEquals(2, timeline3.size)
    }
}
