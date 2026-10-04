package com.msahil432.multitool.blocking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Unit tests for [BlockEnforcementController] testing foreground observation and enforcement loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockEnforcementControllerTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var blockingRepo: BlockingRepository
  private lateinit var usageRepo: UsageRepository
  private lateinit var engine: BlockEngine
  private lateinit var controllerScope: CoroutineScope
  private val foregroundFlow = MutableStateFlow("")
  private var fakeClock = 1_000_000L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    blockingRepo = BlockingRepository(db.blockingDao(), clock = { fakeClock })
    usageRepo = UsageRepository(db.usageDao(), clock = { fakeClock })
    engine = BlockEngine(blockingRepo, usageRepo, clock = { fakeClock })
    controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @After
  fun tearDown() {
    controllerScope.cancel()
    db.close()
  }

  @Test
  fun `start and stop manage monitoring jobs properly`() {
    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )

    controller.start(context)
    controller.stop()
  }

  @Test
  fun `switching foreground app increments launch count for monitored groups`() = runBlocking {
    val groupId = blockingRepo.upsertGroup(
      BlockGroup(name = "Social", packageNames = "com.instagram.android", enabled = true, createdAt = 1000L)
    )

    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )
    controller.start(context)

    // Emit Instagram as foreground
    val todayEpoch = LocalDate.now().toEpochDay()
    foregroundFlow.value = "com.instagram.android"

    withTimeout(5000) {
      while (blockingRepo.getCounterSync(todayEpoch, groupId)?.launchesUsed != 1) {
        delay(50)
      }
    }

    val counter = blockingRepo.getCounterSync(todayEpoch, groupId)
    assertEquals(1, counter?.launchesUsed)

    controller.stop()
  }

  @Test
  fun `switching between apps updates foreground elapsed duration`() = runBlocking {
    val groupId = blockingRepo.upsertGroup(
      BlockGroup(name = "Social", packageNames = "com.instagram.android", enabled = true, createdAt = 1000L)
    )

    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )
    controller.start(context)

    val todayEpoch = LocalDate.now().toEpochDay()

    // Start in Instagram at t = 1,000,000
    foregroundFlow.value = "com.instagram.android"
    withTimeout(5000) {
      while (blockingRepo.getCounterSync(todayEpoch, groupId) == null) {
        delay(50)
      }
    }

    // Advance fake time by 10 seconds (10,000ms)
    fakeClock += 10_000L

    // Switch to another app
    foregroundFlow.value = "com.other.app"
    withTimeout(5000) {
      while (blockingRepo.getCounterSync(todayEpoch, groupId)?.usedForegroundMillis != 10_000L) {
        delay(50)
      }
    }

    val counter = blockingRepo.getCounterSync(todayEpoch, groupId)
    assertEquals(10_000L, counter?.usedForegroundMillis)

    controller.stop()
  }

  @Test
  fun `calculateActiveTimer returns remaining time for daily quota rule`() = runBlocking {
    val groupId = blockingRepo.upsertGroup(
      BlockGroup(name = "Social", packageNames = "com.instagram.android", enabled = true, createdAt = 1000L)
    )
    blockingRepo.upsertRule(
      BlockRule(groupId = groupId, type = BlockRuleType.DAILY_QUOTA, dailyQuotaMinutes = 30)
    )

    // Simulate 10 minutes already used
    val todayEpoch = LocalDate.now().toEpochDay()
    blockingRepo.upsertCounter(
      com.msahil432.multitool.data.BlockCounter(
        dateEpochDay = todayEpoch,
        groupId = groupId,
        usedForegroundMillis = 10 * 60_000L
      )
    )

    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )

    val timerInfo = controller.calculateActiveTimer(context, "com.instagram.android")
    assertNotNull(timerInfo)
    assertEquals(BlockRuleType.DAILY_QUOTA, timerInfo?.ruleType)
    assertEquals(20 * 60_000L, timerInfo?.remainingMillis)
    assertEquals(30 * 60_000L, timerInfo?.totalLimitMillis)
  }

  @Test
  fun `calculateActiveTimer returns remaining time for session limit rule`() = runBlocking {
    val groupId = blockingRepo.upsertGroup(
      BlockGroup(name = "Gaming", packageNames = "com.supercell.clash", enabled = true, createdAt = 1000L)
    )
    blockingRepo.upsertRule(
      BlockRule(groupId = groupId, type = BlockRuleType.SESSION_LIMIT, maxSessionMinutes = 15, cooldownMinutes = 10)
    )

    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )
    controller.start(context)

    // Launch game at t = 1,000,000
    foregroundFlow.value = "com.supercell.clash"
    delay(100)

    // Advance 5 minutes (300,000 ms)
    fakeClock += 5 * 60_000L

    val timerInfo = controller.calculateActiveTimer(context, "com.supercell.clash")
    assertNotNull(timerInfo)
    assertEquals(BlockRuleType.SESSION_LIMIT, timerInfo?.ruleType)
    assertEquals(10 * 60_000L, timerInfo?.remainingMillis)
    assertEquals(15 * 60_000L, timerInfo?.totalLimitMillis)

    controller.stop()
  }

  @Test
  fun `calculateActiveTimer chooses rule with least remaining time when multiple timer rules apply`() = runBlocking {
    val groupId = blockingRepo.upsertGroup(
      BlockGroup(name = "Distracting", packageNames = "com.twitter.android", enabled = true, createdAt = 1000L)
    )
    // 60m daily quota
    blockingRepo.upsertRule(
      BlockRule(groupId = groupId, type = BlockRuleType.DAILY_QUOTA, dailyQuotaMinutes = 60)
    )
    // 15m session limit
    blockingRepo.upsertRule(
      BlockRule(groupId = groupId, type = BlockRuleType.SESSION_LIMIT, maxSessionMinutes = 15, cooldownMinutes = 10)
    )

    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )
    controller.start(context)

    // Launch app at t = 1,000,000
    foregroundFlow.value = "com.twitter.android"
    delay(100)

    // Session has 15m left, Daily Quota has 60m left -> Session limit (15m) has least time remaining
    val timerInfo = controller.calculateActiveTimer(context, "com.twitter.android")
    assertNotNull(timerInfo)
    assertEquals(BlockRuleType.SESSION_LIMIT, timerInfo?.ruleType)
    assertEquals(15 * 60_000L, timerInfo?.remainingMillis)

    controller.stop()
  }

  @Test
  fun `calculateActiveTimer returns null when package has no timer rules`() = runBlocking {
    val controller = BlockEnforcementController(
      scope = controllerScope,
      foregroundState = foregroundFlow,
      engine = engine,
      blockingRepo = blockingRepo,
      usageRepo = usageRepo,
      clock = { fakeClock }
    )

    val timerInfo = controller.calculateActiveTimer(context, "com.untracked.app")
    assertNull(timerInfo)
  }
}
