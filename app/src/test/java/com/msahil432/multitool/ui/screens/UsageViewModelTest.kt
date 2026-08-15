package com.msahil432.multitool.ui.screens

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UnlockType
import com.msahil432.multitool.data.UsageDao
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageViewModelTest {

  private lateinit var db: AppDatabase
  private lateinit var dao: UsageDao
  private lateinit var repository: UsageRepository
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
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
  fun `viewModel reflects repository flows correctly`() = runTest {
    val viewModel = UsageViewModel(repository, context)

    // Initially 0/empty
    assertEquals(0L, viewModel.totalScreenTimeToday.value)
    assertEquals(0, viewModel.perApp.value.size)
    assertEquals(0, viewModel.unlocksToday.value)
    assertEquals(0, viewModel.timeline.value.size)

    // Add data to repository
    repository.recordForeground("com.example.app", 5000L)
    repository.recordLaunch("com.example.app")
    repository.recordUnlock(UnlockType.USER_PRESENT)
    repository.recordTimeline("com.example.app", TimelineEventType.APP_FOREGROUND)

    advanceUntilIdle()

    val totalTime = viewModel.totalScreenTimeToday.first { it == 5000L }
    assertEquals(5000L, totalTime)

    val perAppList = viewModel.perApp.first { it.isNotEmpty() }
    assertEquals(1, perAppList.size)
    assertEquals("com.example.app", perAppList[0].packageName)
    assertEquals(5000L, perAppList[0].foregroundMillis)
    assertEquals(1, perAppList[0].launchCount)

    val unlocks = viewModel.unlocksToday.first { it == 1 }
    assertEquals(1, unlocks)

    val timelineEvents = viewModel.timeline.first { it.isNotEmpty() }
    assertEquals(1, timelineEvents.size)
    assertEquals("com.example.app", timelineEvents[0].packageName)
  }

  @Test
  fun `viewModel resolves and caches app metadata`() = runTest {
    val viewModel = UsageViewModel(repository, context)

    repository.recordForeground(context.packageName, 3000L)
    advanceUntilIdle()

    val metaMap = viewModel.appMetaCache.first { it.containsKey(context.packageName) }
    assertNotNull(metaMap[context.packageName])
  }
}
