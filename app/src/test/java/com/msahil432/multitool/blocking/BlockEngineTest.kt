package com.msahil432.multitool.blocking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var blockingRepo: BlockingRepository
    private lateinit var usageRepo: UsageRepository
    private lateinit var engine: BlockEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        blockingRepo = BlockingRepository(db.blockingDao())
        usageRepo = UsageRepository(db.usageDao())
        engine = BlockEngine(blockingRepo, usageRepo)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testAllowedWhenNoGroupsOrRulesMatch() = runBlocking {
        val decision = engine.evaluate("com.random.app")
        assertTrue(decision is Allowed)
    }

    @Test
    fun testDailyQuotaBlocked() = runBlocking {
        val groupId = blockingRepo.upsertGroup(
            BlockGroup(
                name = "Social",
                packageNames = "com.social.app",
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )

        blockingRepo.upsertRule(
            BlockRule(
                groupId = groupId,
                type = BlockRuleType.DAILY_QUOTA,
                dailyQuotaMinutes = 30,
                enabled = true
            )
        )

        // Under quota: 20 min (1_200_000 ms)
        val today = LocalDate.now().toEpochDay()
        blockingRepo.upsertCounter(
            BlockCounter(
                dateEpochDay = today,
                groupId = groupId,
                usedForegroundMillis = 20 * 60_000L
            )
        )
        val decisionUnder = engine.evaluate("com.social.app")
        assertTrue(decisionUnder is Allowed)

        // Exceeded quota: 35 min (2_100_000 ms)
        blockingRepo.upsertCounter(
            BlockCounter(
                dateEpochDay = today,
                groupId = groupId,
                usedForegroundMillis = 35 * 60_000L
            )
        )
        val decisionExceeded = engine.evaluate("com.social.app")
        assertTrue(decisionExceeded is Blocked)
        assertEquals("Daily limit reached (30m)", (decisionExceeded as Blocked).reason)
    }

    @Test
    fun testLaunchLimitBlocked() = runBlocking {
        val groupId = blockingRepo.upsertGroup(
            BlockGroup(
                name = "Games",
                packageNames = "com.game.app",
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )

        blockingRepo.upsertRule(
            BlockRule(
                groupId = groupId,
                type = BlockRuleType.LAUNCH_LIMIT,
                maxLaunchesPerDay = 5,
                enabled = true
            )
        )

        val today = LocalDate.now().toEpochDay()
        blockingRepo.upsertCounter(
            BlockCounter(
                dateEpochDay = today,
                groupId = groupId,
                launchesUsed = 4
            )
        )
        assertTrue(engine.evaluate("com.game.app") is Allowed)

        blockingRepo.upsertCounter(
            BlockCounter(
                dateEpochDay = today,
                groupId = groupId,
                launchesUsed = 5
            )
        )
        val decision = engine.evaluate("com.game.app")
        assertTrue(decision is Blocked)
        assertEquals("Launch limit reached (5 launches/day)", (decision as Blocked).reason)
    }

    @Test
    fun testSessionLimitCooldown() = runBlocking {
        val now = 100_000_000L
        val customEngine = BlockEngine(blockingRepo, usageRepo, clock = { now })

        val groupId = blockingRepo.upsertGroup(
            BlockGroup(
                name = "Video",
                packageNames = "com.video.app",
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )

        blockingRepo.upsertRule(
            BlockRule(
                groupId = groupId,
                type = BlockRuleType.SESSION_LIMIT,
                maxSessionMinutes = 15,
                cooldownMinutes = 10,
                enabled = true
            )
        )

        val today = LocalDate.now().toEpochDay()
        // Lockout active until now + 50_000
        blockingRepo.upsertCounter(
            BlockCounter(
                dateEpochDay = today,
                groupId = groupId,
                lockedUntil = now + 50_000L
            )
        )
        val decision = customEngine.evaluate("com.video.app")
        assertTrue(decision is Blocked)
        assertEquals("Session cooldown active", (decision as Blocked).reason)

        // Lockout expired
        blockingRepo.upsertCounter(
            BlockCounter(
                dateEpochDay = today,
                groupId = groupId,
                lockedUntil = now - 1000L
            )
        )
        assertTrue(customEngine.evaluate("com.video.app") is Allowed)
    }

    @Test
    fun testGoalUnlockBlockedUntilGoalMet() = runBlocking {
        val groupId = blockingRepo.upsertGroup(
            BlockGroup(
                name = "Distractions",
                packageNames = "com.distract.app",
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )

        blockingRepo.upsertRule(
            BlockRule(
                groupId = groupId,
                type = BlockRuleType.GOAL_UNLOCK,
                goalPackageNames = "com.study.app",
                goalRequiredMinutes = 30,
                enabled = true
            )
        )

        // No study time recorded yet
        val decisionBlocked = engine.evaluate("com.distract.app")
        assertTrue(decisionBlocked is Blocked)
        assertEquals("Finish your goal first (30m needed)", (decisionBlocked as Blocked).reason)

        // Record 35 minutes on study app
        usageRepo.recordForeground("com.study.app", 35 * 60_000L)
        val decisionAllowed = engine.evaluate("com.distract.app")
        assertTrue(decisionAllowed is Allowed)
    }

    @Test
    fun testScheduleRuleEvaluation() = runBlocking {
        val nowMinute = LocalTime.now().hour * 60 + LocalTime.now().minute
        val todayMask = 1 shl (LocalDate.now().dayOfWeek.value - 1)

        val ruleActive = BlockRule(
            groupId = 1,
            type = BlockRuleType.SCHEDULE,
            daysOfWeekMask = todayMask,
            startMinuteOfDay = (nowMinute - 10).coerceAtLeast(0),
            endMinuteOfDay = (nowMinute + 10).coerceAtMost(1439),
            enabled = true
        )
        assertTrue(engine.nowWithinSchedule(ruleActive))

        val ruleInactiveTime = BlockRule(
            groupId = 1,
            type = BlockRuleType.SCHEDULE,
            daysOfWeekMask = todayMask,
            startMinuteOfDay = (nowMinute + 30) % 1440,
            endMinuteOfDay = (nowMinute + 60) % 1440,
            enabled = true
        )
        assertTrue(!engine.nowWithinSchedule(ruleInactiveTime))
    }
}
