package com.msahil432.multitool.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.msahil432.multitool.blocking.BlockEngine
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object NotificationDigestScheduler {

    /**
     * Schedules a digest delivery at the end time of the active schedule, if any.
     */
    suspend fun scheduleDigestIfUpcoming(
        context: Context,
        blockingRepo: BlockingRepository,
        blockEngine: BlockEngine
    ) {
        val allGroups = blockingRepo.allGroups.first()
        var nearestEndMillis: Long? = null

        for (group in allGroups) {
            if (!group.enabled) continue
            val rules = blockingRepo.enabledRules(group.id)
            for (rule in rules) {
                if (rule.type == BlockRuleType.SCHEDULE && blockEngine.nowWithinSchedule(rule)) {
                    val endMillis = calculateScheduleEndMillis(rule)
                    if (endMillis != null) {
                        if (nearestEndMillis == null || endMillis < nearestEndMillis) {
                            nearestEndMillis = endMillis
                        }
                    }
                }
            }
        }

        if (nearestEndMillis != null) {
            val delayMillis = (nearestEndMillis - System.currentTimeMillis()).coerceAtLeast(0)
            scheduleDigestWithDelay(context, delayMillis)
        }
    }

    fun scheduleDigestWithDelay(context: Context, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<NotificationDigestWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            NotificationDigestWorker.WORK_NAME_SCHEDULED,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun deliverNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NotificationDigestWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NotificationDigestWorker.WORK_NAME_ONETIME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun calculateScheduleEndMillis(rule: com.msahil432.multitool.data.BlockRule): Long? {
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
}
