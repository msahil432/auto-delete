package com.msahil432.multitool.blocking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.BlockGroup
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
}
