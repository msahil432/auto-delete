package com.msahil432.multitool.blocking

import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.DeactivationFlow
import com.msahil432.multitool.data.StrictModeState
import com.msahil432.multitool.data.UnlockMethod
import com.msahil432.multitool.data.UnlockParams
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StrictModeControllerTest {

    private var currentClockTime = 1_000_000L

    @Before
    fun setUp() {
        currentClockTime = 1_000_000L
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = false),
            clockProvider = { currentClockTime }
        )
    }

    @Test
    fun testGuardsWhenInactive() {
        assertFalse(StrictModeController.isActive.value)
        assertTrue(StrictModeController.canDeleteGroup())
        assertTrue(StrictModeController.canDisableGroup())

        val groupA = BlockGroup(id = 1, name = "G", packageNames = "pkg1;pkg2", enabled = true, createdAt = 100L)
        val groupB = BlockGroup(id = 1, name = "G", packageNames = "pkg1", enabled = false, createdAt = 100L)
        assertTrue(StrictModeController.canWeakenGroup(groupA, groupB))

        val ruleA = BlockRule(id = 1, groupId = 1, type = BlockRuleType.DAILY_QUOTA, dailyQuotaMinutes = 30)
        val ruleB = BlockRule(id = 1, groupId = 1, type = BlockRuleType.DAILY_QUOTA, dailyQuotaMinutes = 60, enabled = false)
        assertTrue(StrictModeController.canWeakenRule(ruleA, ruleB))
    }

    @Test
    fun testGroupGuardsWhenActive() {
        StrictModeController.resetForTesting(
            state = StrictModeState(isActive = true),
            clockProvider = { currentClockTime }
        )
        assertTrue(StrictModeController.isActive.value)
        assertFalse(StrictModeController.canDeleteGroup())
        assertFalse(StrictModeController.canDisableGroup())

        val oldGroup = BlockGroup(id = 1, name = "Work", packageNames = "app1;app2;app3", enabled = true, createdAt = 100L)

        // Disabling group -> disallowed
        val disabledGroup = oldGroup.copy(enabled = false)
        assertFalse(StrictModeController.canWeakenGroup(oldGroup, disabledGroup))

        // Removing apps -> disallowed
        val reducedAppsGroup = oldGroup.copy(packageNames = "app1;app2")
        assertFalse(StrictModeController.canWeakenGroup(oldGroup, reducedAppsGroup))

        // Adding apps -> allowed (strengthening)
        val moreAppsGroup = oldGroup.copy(packageNames = "app1;app2;app3;app4")
        assertTrue(StrictModeController.canWeakenGroup(oldGroup, moreAppsGroup))

        // Identical group -> allowed
        assertTrue(StrictModeController.canWeakenGroup(oldGroup, oldGroup))
    }

    @Test
    fun testDailyQuotaRuleWeakening() {
        StrictModeController.resetForTesting(state = StrictModeState(isActive = true))
        val oldRule = BlockRule(id = 1, groupId = 1, type = BlockRuleType.DAILY_QUOTA, dailyQuotaMinutes = 30)

        // Increasing quota (more time allowed) -> weaker (disallowed)
        val weakerRule = oldRule.copy(dailyQuotaMinutes = 45)
        assertFalse(StrictModeController.canWeakenRule(oldRule, weakerRule))

        // Decreasing quota (less time allowed) -> stronger (allowed)
        val strongerRule = oldRule.copy(dailyQuotaMinutes = 20)
        assertTrue(StrictModeController.canWeakenRule(oldRule, strongerRule))

        // Disabling rule -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(enabled = false)))
    }

    @Test
    fun testLaunchLimitRuleWeakening() {
        StrictModeController.resetForTesting(state = StrictModeState(isActive = true))
        val oldRule = BlockRule(id = 1, groupId = 1, type = BlockRuleType.LAUNCH_LIMIT, maxLaunchesPerDay = 10)

        // Increasing max launches -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(maxLaunchesPerDay = 15)))

        // Decreasing max launches -> stronger (allowed)
        assertTrue(StrictModeController.canWeakenRule(oldRule, oldRule.copy(maxLaunchesPerDay = 5)))
    }

    @Test
    fun testSessionLimitRuleWeakening() {
        StrictModeController.resetForTesting(state = StrictModeState(isActive = true))
        val oldRule = BlockRule(
            id = 1,
            groupId = 1,
            type = BlockRuleType.SESSION_LIMIT,
            maxSessionMinutes = 15,
            cooldownMinutes = 15
        )

        // Increasing session time -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(maxSessionMinutes = 20)))

        // Reducing cooldown -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(cooldownMinutes = 10)))

        // Decreasing session time AND increasing cooldown -> stronger (allowed)
        val stronger = oldRule.copy(maxSessionMinutes = 10, cooldownMinutes = 20)
        assertTrue(StrictModeController.canWeakenRule(oldRule, stronger))
    }

    @Test
    fun testScheduleRuleWeakening() {
        StrictModeController.resetForTesting(state = StrictModeState(isActive = true))
        val oldRule = BlockRule(
            id = 1,
            groupId = 1,
            type = BlockRuleType.SCHEDULE,
            daysOfWeekMask = 0x1F, // Mon-Fri (5 days)
            startMinuteOfDay = 540, // 09:00
            endMinuteOfDay = 1020 // 17:00 (span = 480 mins)
        )

        // 1. Removing a day (e.g., only Mon-Thu 0x0F) -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(daysOfWeekMask = 0x0F)))

        // 2. Adding a day (e.g., Mon-Sat 0x3F) -> stronger (allowed)
        assertTrue(StrictModeController.canWeakenRule(oldRule, oldRule.copy(daysOfWeekMask = 0x3F)))

        // 3. Shortening time window span (e.g. 10:00 to 16:00, span = 360 mins) -> weaker (disallowed)
        assertFalse(
            StrictModeController.canWeakenRule(
                oldRule,
                oldRule.copy(startMinuteOfDay = 600, endMinuteOfDay = 960)
            )
        )

        // 4. Extending time window span (e.g. 08:00 to 18:00, span = 600 mins) -> stronger (allowed)
        assertTrue(
            StrictModeController.canWeakenRule(
                oldRule,
                oldRule.copy(startMinuteOfDay = 480, endMinuteOfDay = 1080)
            )
        )
    }

    @Test
    fun testGoalUnlockRuleWeakening() {
        StrictModeController.resetForTesting(state = StrictModeState(isActive = true))
        val oldRule = BlockRule(
            id = 1,
            groupId = 1,
            type = BlockRuleType.GOAL_UNLOCK,
            goalRequiredMinutes = 30,
            goalPackageNames = "com.duolingo;com.kindle"
        )

        // Lowering required goal minutes -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(goalRequiredMinutes = 15)))

        // Increasing required goal minutes -> stronger (allowed)
        assertTrue(StrictModeController.canWeakenRule(oldRule, oldRule.copy(goalRequiredMinutes = 45)))

        // Removing a required productive app -> weaker (disallowed)
        assertFalse(StrictModeController.canWeakenRule(oldRule, oldRule.copy(goalPackageNames = "com.duolingo")))

        // Adding more productive apps -> allowed
        assertTrue(
            StrictModeController.canWeakenRule(
                oldRule,
                oldRule.copy(goalPackageNames = "com.duolingo;com.kindle;com.coursera")
            )
        )
    }

    @Test
    fun testDeactivationFlows() {
        // 1. Not active
        StrictModeController.resetForTesting(state = StrictModeState(isActive = false))
        assertEquals(DeactivationFlow.NotActive, StrictModeController.requestDeactivation())

        // 2. Timed strict mode expired
        StrictModeController.resetForTesting(
            state = StrictModeState(
                isActive = true,
                startedAt = 1_000L,
                endAt = 2_000L,
                unlockMethod = UnlockMethod.TEXT
            ),
            clockProvider = { 3_000L }
        )
        val expiredFlow = StrictModeController.requestDeactivation()
        assertEquals(DeactivationFlow.TimeExpired, expiredFlow)
        assertFalse(StrictModeController.isActive.value)

        // 3. Text challenge required
        StrictModeController.resetForTesting(
            state = StrictModeState(
                isActive = true,
                startedAt = 1_000L,
                endAt = 0L,
                unlockMethod = UnlockMethod.TEXT
            ),
            clockProvider = { 2_000L }
        )
        val textFlow = StrictModeController.requestDeactivation()
        assertTrue(textFlow is DeactivationFlow.ChallengeRequired)
        assertEquals(UnlockMethod.TEXT, (textFlow as DeactivationFlow.ChallengeRequired).method)

        // 4. Cooldown flow
        StrictModeController.resetForTesting(
            state = StrictModeState(
                isActive = true,
                startedAt = 1_000L,
                endAt = 0L,
                unlockMethod = UnlockMethod.COOLDOWN,
                pendingDeactivationAt = 0L
            ),
            clockProvider = { 5_000L }
        )
        val cooldownFlow = StrictModeController.requestDeactivation()
        assertTrue(cooldownFlow is DeactivationFlow.ChallengeRequired)
        val cooldownReq = cooldownFlow as DeactivationFlow.ChallengeRequired
        assertEquals(UnlockMethod.COOLDOWN, cooldownReq.method)
        assertEquals(5_000L + (15 * 60_000L), cooldownReq.pendingDeactivationAt)
    }

    @Test
    fun testCompleteDeactivation() {
        StrictModeController.resetForTesting(
            state = StrictModeState(
                isActive = true,
                startedAt = 1000L,
                endAt = 5000L,
                unlockMethod = UnlockMethod.PIN
            )
        )
        assertTrue(StrictModeController.isActive.value)

        StrictModeController.completeDeactivation()
        assertFalse(StrictModeController.isActive.value)
        assertEquals(0L, StrictModeController.state.value.startedAt)
        assertEquals(0L, StrictModeController.state.value.endAt)
    }

    @Test
    fun testCancelPendingDeactivation() {
        StrictModeController.resetForTesting(
            state = StrictModeState(
                isActive = true,
                startedAt = 1000L,
                endAt = 0L,
                unlockMethod = UnlockMethod.COOLDOWN,
                pendingDeactivationAt = 60_000L
            )
        )
        assertEquals(60_000L, StrictModeController.state.value.pendingDeactivationAt)

        StrictModeController.cancelPendingDeactivation()
        assertEquals(0L, StrictModeController.state.value.pendingDeactivationAt)
        assertTrue(StrictModeController.isActive.value)
    }

    @Test
    fun testCustomCooldownMinutes() {
        StrictModeController.resetForTesting(
            state = StrictModeState(
                isActive = true,
                startedAt = 1_000L,
                endAt = 0L,
                unlockMethod = UnlockMethod.COOLDOWN,
                pendingDeactivationAt = 0L
            ),
            clockProvider = { 10_000L }
        )
        val flow = StrictModeController.requestDeactivation(cooldownMinutes = 30)
        assertTrue(flow is DeactivationFlow.ChallengeRequired)
        assertEquals(10_000L + (30 * 60_000L), (flow as DeactivationFlow.ChallengeRequired).pendingDeactivationAt)
    }
}
