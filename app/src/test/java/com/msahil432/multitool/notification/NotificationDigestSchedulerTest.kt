package com.msahil432.multitool.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.msahil432.multitool.blocking.BlockEngine
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Unit tests for [NotificationDigestScheduler] covering delay calculation, scheduled requests, and immediate delivery.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationDigestSchedulerTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var blockingRepo: BlockingRepository
  private lateinit var usageRepo: UsageRepository
  private lateinit var blockEngine: BlockEngine

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    try {
      val config = androidx.work.Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .build()
      WorkManager.initialize(context, config)
    } catch (_: Exception) {
      // WorkManager already initialized
    }
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    blockingRepo = BlockingRepository(db.blockingDao())
    usageRepo = UsageRepository(db.usageDao())
    blockEngine = BlockEngine(blockingRepo, usageRepo)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `deliverNow enqueues onetime unique work request`() {
    NotificationDigestScheduler.deliverNow(context)

    val workInfos = WorkManager.getInstance(context)
      .getWorkInfosForUniqueWork(NotificationDigestWorker.WORK_NAME_ONETIME)
      .get()

    assertNotNull(workInfos)
    assertTrue(workInfos.isNotEmpty())
  }

  @Test
  fun `scheduleDigestWithDelay enqueues scheduled unique work request`() {
    NotificationDigestScheduler.scheduleDigestWithDelay(context, 60_000L)

    val workInfos = WorkManager.getInstance(context)
      .getWorkInfosForUniqueWork(NotificationDigestWorker.WORK_NAME_SCHEDULED)
      .get()

    assertNotNull(workInfos)
    assertTrue(workInfos.isNotEmpty())
  }

  @Test
  fun `scheduleDigestIfUpcoming handles empty rules gracefully`() = runTest {
    // When no groups or rules are configured, should complete without throwing
    NotificationDigestScheduler.scheduleDigestIfUpcoming(context, blockingRepo, blockEngine)
  }

  @Test
  fun `scheduleDigestIfUpcoming schedules work when active schedule rule exists`() = runTest {
    val groupId = blockingRepo.upsertGroup(
      BlockGroup(name = "Work", packageNames = "com.slack", enabled = true, createdAt = 1000L)
    )
    val now = LocalTime.now()
    val nowMinute = now.hour * 60 + now.minute
    val startMin = (nowMinute - 30).coerceAtLeast(0)
    val endMin = (nowMinute + 60).coerceAtMost(1439)
    val dayOfWeek = java.time.LocalDate.now().dayOfWeek.value // 1=Mon..7=Sun
    val dayMask = 1 shl (dayOfWeek - 1)

    blockingRepo.upsertRule(
      BlockRule(
        groupId = groupId,
        type = BlockRuleType.SCHEDULE,
        enabled = true,
        daysOfWeekMask = dayMask,
        startMinuteOfDay = startMin,
        endMinuteOfDay = endMin
      )
    )

    NotificationDigestScheduler.scheduleDigestIfUpcoming(context, blockingRepo, blockEngine)

    val workInfos = WorkManager.getInstance(context)
      .getWorkInfosForUniqueWork(NotificationDigestWorker.WORK_NAME_SCHEDULED)
      .get()
    assertNotNull(workInfos)
    assertTrue(workInfos.isNotEmpty())
  }
}
