package com.msahil432.multitool.tracking

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UnlockType
import com.msahil432.multitool.data.UsageDao
import com.msahil432.multitool.data.UsageRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenUnlockReceiverTest {

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
    fun `receiver delivers SCREEN_ON on ACTION_SCREEN_ON intent`() {
        val received = mutableListOf<UnlockType>()
        val receiver = ScreenUnlockReceiver { received.add(it) }
        val context = ApplicationProvider.getApplicationContext<Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        assertEquals(1, received.size)
        assertEquals(UnlockType.SCREEN_ON, received[0])
    }

    @Test
    fun `receiver delivers USER_PRESENT on ACTION_USER_PRESENT intent`() {
        val received = mutableListOf<UnlockType>()
        val receiver = ScreenUnlockReceiver { received.add(it) }
        val context = ApplicationProvider.getApplicationContext<Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        assertEquals(1, received.size)
        assertEquals(UnlockType.USER_PRESENT, received[0])
    }

    @Test
    fun `receiver ignores unrecognized intent actions`() {
        val received = mutableListOf<UnlockType>()
        val receiver = ScreenUnlockReceiver { received.add(it) }
        val context = ApplicationProvider.getApplicationContext<Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_BATTERY_LOW))

        assertTrue(received.isEmpty())
    }

    @Test
    fun `receiver actions correctly write unlock and timeline events to repository`() = runTest {
        val receiver = ScreenUnlockReceiver { type ->
            runTest {
                repository.recordUnlock(type)
                if (type == UnlockType.USER_PRESENT) {
                    repository.recordTimeline("", TimelineEventType.UNLOCK)
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        receiver.onReceive(context, Intent(Intent.ACTION_USER_PRESENT))

        val unlockCount = repository.unlocksToday().first()
        assertEquals(2, unlockCount)

        val timeline = repository.timelineToday().first()
        assertEquals(1, timeline.size)
        assertEquals(TimelineEventType.UNLOCK, timeline[0].eventType)
    }
}
