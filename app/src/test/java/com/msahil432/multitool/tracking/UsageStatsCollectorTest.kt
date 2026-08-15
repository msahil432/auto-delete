package com.msahil432.multitool.tracking

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageDao
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageStatsCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dao: UsageDao
    private lateinit var repository: UsageRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.usageDao()
        repository = UsageRepository(dao)

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.root, "test_settings.preferences_pb") }
        )
        settingsRepository = SettingsRepository(testDataStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `usageLastProcessedTs default is 0 and can be updated`() = runTest {
        val initial = settingsRepository.usageLastProcessedTs.first()
        assertEquals(0L, initial)

        val testTimestamp = 1700000000000L
        settingsRepository.setUsageLastProcessedTs(testTimestamp)

        val updated = settingsRepository.usageLastProcessedTs.first()
        assertEquals(testTimestamp, updated)
    }

    @Test
    fun `event processing simulation calculates foreground time and launch count`() = runTest {
        val pkg = "com.example.testapp"
        val resumeTime = 1000L
        val pauseTime = 6000L

        // Simulate ACTIVITY_RESUMED
        repository.recordLaunch(pkg)
        repository.recordTimeline(pkg, TimelineEventType.APP_FOREGROUND)

        // Simulate ACTIVITY_PAUSED
        val duration = pauseTime - resumeTime
        repository.recordForeground(pkg, duration)
        repository.recordTimeline(pkg, TimelineEventType.APP_BACKGROUND, duration)

        val stats = repository.todayStats().first()
        assertEquals(1, stats.size)
        assertEquals(pkg, stats[0].packageName)
        assertEquals(5000L, stats[0].foregroundMillis)
        assertEquals(1, stats[0].launchCount)

        val timeline = repository.timelineToday().first()
        assertEquals(2, timeline.size)
        assertEquals(TimelineEventType.APP_BACKGROUND, timeline[0].eventType)
        assertEquals(5000L, timeline[0].durationMillis)
        assertEquals(TimelineEventType.APP_FOREGROUND, timeline[1].eventType)
    }

    @Test
    fun `schedule worker runs without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        UsageCollectorWorker.schedule(context)
    }
}
