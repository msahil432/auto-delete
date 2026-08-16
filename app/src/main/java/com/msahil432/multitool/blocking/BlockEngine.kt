package com.msahil432.multitool.blocking

import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.UsageRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed interface BlockDecision
data object Allowed : BlockDecision
data class Blocked(
    val rule: BlockRule,
    val reason: String,
    val group: BlockGroup? = null,
    val usedSeconds: Long? = null,
    val limitSeconds: Long? = null,
    val endsAtMillis: Long? = null
) : BlockDecision

/**
 * Evaluates whether a foreground application package should be blocked
 * based on active blocking groups and rules.
 */
class BlockEngine(
    private val blockingRepo: BlockingRepository,
    private val usageRepo: UsageRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Evaluates the given package name against enabled blocking groups and rules.
     * Returns [Blocked] with relevant reason and metadata if a blocking condition is met,
     * otherwise returns [Allowed].
     */
    suspend fun evaluate(pkg: String): BlockDecision {
        if (pkg.isBlank()) return Allowed

        val groups = blockingRepo.enabledGroupsContaining(pkg)
        for (group in groups) {
            val counter = blockingRepo.counterForToday(group.id)
            val rules = blockingRepo.enabledRules(group.id)

            for (rule in rules) {
                when (rule.type) {
                    BlockRuleType.SCHEDULE -> {
                        if (nowWithinSchedule(rule)) {
                            val endsAt = calculateScheduleEndMillis(rule)
                            return Blocked(
                                rule = rule,
                                reason = "Blocked by schedule (${group.name})",
                                group = group,
                                endsAtMillis = endsAt
                            )
                        }
                    }
                    BlockRuleType.DAILY_QUOTA -> {
                        val limitMillis = rule.dailyQuotaMinutes * 60_000L
                        if (rule.dailyQuotaMinutes > 0 && counter.usedForegroundMillis >= limitMillis) {
                            return Blocked(
                                rule = rule,
                                reason = "Daily limit reached (${rule.dailyQuotaMinutes}m)",
                                group = group,
                                usedSeconds = counter.usedForegroundMillis / 1000L,
                                limitSeconds = limitMillis / 1000L
                            )
                        }
                    }
                    BlockRuleType.LAUNCH_LIMIT -> {
                        if (rule.maxLaunchesPerDay > 0 && counter.launchesUsed >= rule.maxLaunchesPerDay) {
                            return Blocked(
                                rule = rule,
                                reason = "Launch limit reached (${rule.maxLaunchesPerDay} launches/day)",
                                group = group
                            )
                        }
                    }
                    BlockRuleType.SESSION_LIMIT -> {
                        if (clock() < counter.lockedUntil) {
                            return Blocked(
                                rule = rule,
                                reason = "Session cooldown active",
                                group = group,
                                endsAtMillis = counter.lockedUntil
                            )
                        }
                    }
                    BlockRuleType.GOAL_UNLOCK -> {
                        if (!goalMet(rule)) {
                            return Blocked(
                                rule = rule,
                                reason = "Finish your goal first (${rule.goalRequiredMinutes}m needed)",
                                group = group
                            )
                        }
                    }
                }
            }
        }
        return Allowed
    }

    /**
     * Checks if current time is within rule's day of week mask and start/end minutes.
     */
    fun nowWithinSchedule(rule: BlockRule): Boolean {
        if (rule.daysOfWeekMask != 0) {
            val today = LocalDate.now().dayOfWeek
            val dayBit = 1 shl (today.value - 1) // bit0=Mon .. bit6=Sun
            if ((rule.daysOfWeekMask and dayBit) == 0) {
                return false
            }
        }

        val now = LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute

        return if (rule.startMinuteOfDay <= rule.endMinuteOfDay) {
            nowMinute in rule.startMinuteOfDay..rule.endMinuteOfDay
        } else {
            // Schedule wraps past midnight
            nowMinute >= rule.startMinuteOfDay || nowMinute <= rule.endMinuteOfDay
        }
    }

    private fun calculateScheduleEndMillis(rule: BlockRule): Long? {
        val now = LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute
        val targetDay = if (rule.startMinuteOfDay > rule.endMinuteOfDay && nowMinute >= rule.startMinuteOfDay) {
            LocalDate.now().plusDays(1)
        } else {
            LocalDate.now()
        }
        val endHour = (rule.endMinuteOfDay / 60).coerceIn(0, 23)
        val endMinute = (rule.endMinuteOfDay % 60).coerceIn(0, 59)
        val endDateTime = targetDay.atTime(endHour, endMinute)
        return endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private suspend fun goalMet(rule: BlockRule): Boolean {
        if (rule.goalRequiredMinutes <= 0) return true
        val usageRepo = this.usageRepo ?: return true
        val goalApps = rule.goalPackageNames?.split(";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        if (goalApps.isEmpty()) return true

        val totalMinutes = usageRepo.getTodayForegroundMinutesFor(goalApps)
        return totalMinutes >= rule.goalRequiredMinutes
    }
}
