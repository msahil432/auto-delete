package com.msahil432.multitool.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageRepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var dao: UsageDao
  private lateinit var repository: UsageRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    dao = db.usageDao()
    repository = UsageRepository(dao)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `recordForeground creates and updates daily stats correctly`() = runTest {
    val pkg = "com.example.app"
    repository.recordForeground(pkg, 5000L)

    val stats = repository.todayStats().first()
    assertEquals(1, stats.size)
    assertEquals(pkg, stats[0].packageName)
    assertEquals(5000L, stats[0].foregroundMillis)
    assertEquals(0, stats[0].launchCount)

    // Accumulate more foreground time
    repository.recordForeground(pkg, 3000L)
    val updatedStats = repository.todayStats().first()
    assertEquals(1, updatedStats.size)
    assertEquals(8000L, updatedStats[0].foregroundMillis)

    val totalTime = repository.totalScreenTimeToday().first()
    assertEquals(8000L, totalTime)
  }

  @Test
  fun `recordLaunch creates and updates daily launch count and events`() = runTest {
    val pkg = "com.example.app"
    repository.recordLaunch(pkg)
    repository.recordLaunch(pkg)

    val stats = repository.todayStats().first()
    assertEquals(1, stats.size)
    assertEquals(2, stats[0].launchCount)

    val countSinceBeginning = dao.launchCountSince(pkg, 0L)
    assertEquals(2, countSinceBeginning)
  }

  @Test
  fun `recordUnlock and unlock count works`() = runTest {
    repository.recordUnlock(UnlockType.SCREEN_ON)
    repository.recordUnlock(UnlockType.USER_PRESENT)
    repository.recordUnlock(UnlockType.USER_PRESENT)

    val unlocksToday = repository.unlocksToday().first()
    assertEquals(2, unlocksToday)
  }

  @Test
  fun `recordTimeline and timelineToday works`() = runTest {
    val pkg = "com.example.app"
    repository.recordTimeline(pkg, TimelineEventType.APP_FOREGROUND)
    repository.recordTimeline(pkg, TimelineEventType.APP_BACKGROUND, durationMillis = 4200L)

    val timeline = repository.timelineToday().first()
    assertEquals(2, timeline.size)
    assertEquals(TimelineEventType.APP_BACKGROUND, timeline[0].eventType)
    assertEquals(4200L, timeline[0].durationMillis)
    assertEquals(TimelineEventType.APP_FOREGROUND, timeline[1].eventType)
  }

  @Test
  fun `pruneOlderThanDays removes only events older than cutoff`() = runTest {
    val oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100)
    val recentTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)

    dao.insertLaunch(AppLaunchEvent(packageName = "com.old.app", timestamp = oldTimestamp))
    dao.insertLaunch(AppLaunchEvent(packageName = "com.new.app", timestamp = recentTimestamp))

    dao.insertUnlock(UnlockEvent(timestamp = oldTimestamp, type = UnlockType.USER_PRESENT))
    dao.insertUnlock(UnlockEvent(timestamp = recentTimestamp, type = UnlockType.USER_PRESENT))

    dao.insertTimeline(TimelineEvent(timestamp = oldTimestamp, packageName = "com.old.app", eventType = TimelineEventType.UNLOCK))
    dao.insertTimeline(TimelineEvent(timestamp = recentTimestamp, packageName = "com.new.app", eventType = TimelineEventType.UNLOCK))

    repository.pruneOlderThanDays(90)

    assertEquals(0, dao.launchCountSince("com.old.app", 0L))
    assertEquals(1, dao.launchCountSince("com.new.app", 0L))

    val unlocks = dao.unlockCountSince(0L).first()
    assertEquals(1, unlocks)

    val timeline = dao.timelineSince(0L).first()
    assertEquals(1, timeline.size)
    assertEquals("com.new.app", timeline[0].packageName)
  }
}
