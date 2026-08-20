package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.BrowsingEvent
import com.msahil432.multitool.data.BrowsingKind
import com.msahil432.multitool.data.BrowsingRepository
import com.msahil432.multitool.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class BrowserUrlHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var browsingRepo: BrowsingRepository
    private lateinit var settingsRepo: SettingsRepository
    private var currentTime = 1724150000000L
    private val testClock = { currentTime }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Use a unique database name per test to avoid any chance of leakage
        val dbName = "test_db_${System.nanoTime()}"
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        Dispatcher.handlers.clear()
        
        browsingRepo = BrowsingRepository(db.browsingDao(), clock = testClock)

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.root, "test_settings.preferences_pb") }
        )
        settingsRepo = SettingsRepository(testDataStore)
        currentTime = 1000000L
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testBrowserSignaturesMapping() {
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_CHROME))
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_FIREFOX))
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_BRAVE))
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_EDGE))
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_DUCKDUCKGO))
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_OPERA))
        assertTrue(BrowserSignatures.isSupportedBrowser(BrowserSignatures.PKG_SAMSUNG))
        assertFalse(BrowserSignatures.isSupportedBrowser("com.random.unsupported"))
        assertFalse(BrowserSignatures.isSupportedBrowser(null))

        assertEquals("com.android.chrome:id/url_bar", BrowserSignatures.getUrlBarViewId(BrowserSignatures.PKG_CHROME))
        assertEquals("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", BrowserSignatures.getUrlBarViewId(BrowserSignatures.PKG_FIREFOX))
        assertEquals("com.brave.browser:id/url_bar", BrowserSignatures.getUrlBarViewId(BrowserSignatures.PKG_BRAVE))
    }

    @Test
    fun testSettingsRepositoryTrackBrowserUrlsToggle() = runTest {
        assertFalse(settingsRepo.trackBrowserUrls.first())

        settingsRepo.setTrackBrowserUrls(true)
        assertTrue(settingsRepo.trackBrowserUrls.first())

        settingsRepo.setTrackBrowserUrls(false)
        assertFalse(settingsRepo.trackBrowserUrls.first())
    }

    @Test
    fun testNormalizeBrowsingTextUrlExtraction() {
        val parsed1 = BrowserUrlHandler.normalizeBrowsingText("https://github.com/torvalds/linux")
        assertNotNull(parsed1)
        assertEquals(BrowsingKind.URL, parsed1!!.first)
        assertEquals("github.com", parsed1.second)

        val parsed2 = BrowserUrlHandler.normalizeBrowsingText("http://www.wikipedia.org/wiki/Kotlin")
        assertNotNull(parsed2)
        assertEquals(BrowsingKind.URL, parsed2!!.first)
        assertEquals("wikipedia.org", parsed2.second)

        val parsed3 = BrowserUrlHandler.normalizeBrowsingText("news.ycombinator.com")
        assertNotNull(parsed3)
        assertEquals(BrowsingKind.URL, parsed3!!.first)
        assertEquals("news.ycombinator.com", parsed3.second)
    }

    @Test
    fun testNormalizeBrowsingTextSearchQueryExtraction() {
        val parsed1 = BrowserUrlHandler.normalizeBrowsingText("kotlin room migrations")
        assertNotNull(parsed1)
        assertEquals(BrowsingKind.SEARCH_QUERY, parsed1!!.first)
        assertEquals("kotlin room migrations", parsed1.second)

        val parsed2 = BrowserUrlHandler.normalizeBrowsingText("https://www.google.com/search?q=android+accessibility+service&hl=en")
        assertNotNull(parsed2)
        assertEquals(BrowsingKind.SEARCH_QUERY, parsed2!!.first)
        assertEquals("android accessibility service", parsed2.second)

        val parsed3 = BrowserUrlHandler.normalizeBrowsingText("https://duckduckgo.com/?q=jetpack+compose+testing")
        assertNotNull(parsed3)
        assertEquals(BrowsingKind.SEARCH_QUERY, parsed3!!.first)
        assertEquals("jetpack compose testing", parsed3.second)

        val parsed4 = BrowserUrlHandler.normalizeBrowsingText("https://www.bing.com/search?q=kotlin+coroutines")
        assertNotNull(parsed4)
        assertEquals(BrowsingKind.SEARCH_QUERY, parsed4!!.first)
        assertEquals("kotlin coroutines", parsed4.second)
    }

    @Test
    fun testNormalizeBrowsingTextFiltersPlaceholdersAndBlanks() {
        assertNull(BrowserUrlHandler.normalizeBrowsingText(""))
        assertNull(BrowserUrlHandler.normalizeBrowsingText("   "))
        assertNull(BrowserUrlHandler.normalizeBrowsingText("Search or type URL"))
        assertNull(BrowserUrlHandler.normalizeBrowsingText("search or enter address"))
        assertNull(BrowserUrlHandler.normalizeBrowsingText("Search"))
    }

    @Test
    fun testHandlerIgnoresEventsWhenToggleDisabled() = runTest {
        val handler = BrowserUrlHandler(
            settingsRepository = settingsRepo,
            browsingRepository = browsingRepo,
            coroutineScope = backgroundScope,
            debounceDelayMs = 0L
        )
        testScheduler.advanceUntilIdle()

        // Toggle is false by default
        assertFalse(handler.trackBrowserUrls)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = BrowserSignatures.PKG_CHROME

        handler.onEvent(service, event)
        testScheduler.advanceUntilIdle()

        val events = browsingRepo.allRecent().first()
        assertTrue(events.isEmpty())
    }

    @Test
    fun testHandlerIgnoresUnsupportedBrowser() = runTest {
        settingsRepo.setTrackBrowserUrls(true)

        val handler = BrowserUrlHandler(
            settingsRepository = settingsRepo,
            browsingRepository = browsingRepo,
            coroutineScope = backgroundScope,
            debounceDelayMs = 0L,
            clock = testClock
        )
        // Wait for flow to collect
        testScheduler.advanceUntilIdle()
        for (i in 1..10) {
            if (handler.trackBrowserUrls) break
            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()
        }

        assertTrue(handler.trackBrowserUrls)

        val service = Robolectric.buildService(MultiToolAccessibilityService::class.java).create().get()
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = "com.unsupported.browser"

        handler.onEvent(service, event)
        testScheduler.advanceUntilIdle()

        val events = browsingRepo.allRecent().first()
        assertTrue("Expected empty events but found: ${events.map { it.packageName }}", events.isEmpty())
        event.recycle()
    }

    @Test
    fun testBrowsingRepositoryOperations() = runTest {
        val eventId = browsingRepo.recordBrowsing(
            packageName = BrowserSignatures.PKG_CHROME,
            kind = BrowsingKind.URL,
            value = "reddit.com",
            timestamp = 1000L
        )
        assertTrue(eventId > 0)

        val searchId = browsingRepo.recordBrowsing(
            packageName = BrowserSignatures.PKG_FIREFOX,
            kind = BrowsingKind.SEARCH_QUERY,
            value = "android room database",
            timestamp = 2000L
        )
        assertTrue(searchId > 0)

        val allEvents = browsingRepo.allRecent().first()
        assertEquals(2, allEvents.size)
        assertEquals("android room database", allEvents[0].value)
        assertEquals(BrowsingKind.SEARCH_QUERY, allEvents[0].kind)
        assertEquals("reddit.com", allEvents[1].value)
        assertEquals(BrowsingKind.URL, allEvents[1].kind)

        val recentEvents = browsingRepo.recentSince(1500L).first()
        assertEquals(1, recentEvents.size)
        assertEquals("android room database", recentEvents[0].value)

        // Test pruning
        browsingRepo.pruneOlderThanDays(0)
    }
}
