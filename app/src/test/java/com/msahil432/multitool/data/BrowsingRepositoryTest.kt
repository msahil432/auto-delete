package com.msahil432.multitool.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [BrowsingRepository] covering logging, querying, and retention pruning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowsingRepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var repo: BrowsingRepository
  private var fakeClock = 1_700_000_000_000L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = BrowsingRepository(db.browsingDao(), clock = { fakeClock })
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `recordBrowsing persists URL and Search query events`() = runTest {
    val id1 = repo.recordBrowsing(
      packageName = "com.android.chrome",
      kind = BrowsingKind.URL,
      value = "https://kotlinlang.org",
      timestamp = fakeClock
    )
    assertTrue(id1 > 0)

    val id2 = repo.recordBrowsing(
      packageName = "org.mozilla.firefox",
      kind = BrowsingKind.SEARCH_QUERY,
      value = "android jetpack compose",
      timestamp = fakeClock + 1000L
    )
    assertTrue(id2 > 0)

    val all = repo.allRecent().first()
    assertEquals(2, all.size)
    assertEquals(BrowsingKind.SEARCH_QUERY, all[0].kind)
    assertEquals("android jetpack compose", all[0].value)
    assertEquals(BrowsingKind.URL, all[1].kind)
    assertEquals("https://kotlinlang.org", all[1].value)
  }

  @Test
  fun `recentSince returns only events on or after threshold`() = runTest {
    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://old.com", timestamp = 1000L)
    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://mid.com", timestamp = 2000L)
    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://new.com", timestamp = 3000L)

    val filtered = repo.recentSince(2000L).first()
    assertEquals(2, filtered.size)
    assertEquals("https://new.com", filtered[0].value)
    assertEquals("https://mid.com", filtered[1].value)
  }

  @Test
  fun `recentToday returns events recorded today`() = runTest {
    val midnight = repo.startOfDayMillisNow()
    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://yesterday.com", timestamp = midnight - 1000L)
    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://today.com", timestamp = midnight + 5000L)

    val todayEvents = repo.recentToday().first()
    assertEquals(1, todayEvents.size)
    assertEquals("https://today.com", todayEvents[0].value)
  }

  @Test
  fun `pruneOlderThanDays deletes only expired browsing records`() = runTest {
    val oldTime = fakeClock - TimeUnit.DAYS.toMillis(100)
    val recentTime = fakeClock - TimeUnit.DAYS.toMillis(10)

    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://old.com", timestamp = oldTime)
    repo.recordBrowsing("com.chrome", BrowsingKind.URL, "https://recent.com", timestamp = recentTime)

    repo.pruneOlderThanDays(90)

    val remaining = repo.allRecent().first()
    assertEquals(1, remaining.size)
    assertEquals("https://recent.com", remaining[0].value)
  }
}
