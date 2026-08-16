package com.msahil432.multitool.tracking

import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.dataStore
import com.msahil432.multitool.util.UsageAccess
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class UsageCollectorWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!UsageAccess.isGranted(applicationContext)) return Result.success() // nothing to do

        val settings = SettingsRepository(appContext.dataStore)
        val app = appContext.applicationContext as? MultiToolApp
        val repo = if (app != null) UsageRepository(app.database.usageDao()) else return Result.success()
        val reader = UsageStatsReader(appContext)

        val now = System.currentTimeMillis()
        val lastTs = settings.usageLastProcessedTs.first()
        val since = if (lastTs == 0L) now - 24 * 3600_000L else lastTs
        if (since >= now) {
            return Result.success()
        }

        val events = reader.queryEvents(since, now)

        val ownPackage = appContext.packageName
        val homePackage = getHomePackage(appContext)

        // Track per-package foreground start times to compute durations.
        val resumeMap = mutableMapOf<String, Long>()

        for (event in events) {
            val pkg = event.packageName ?: continue
            if (pkg == ownPackage || pkg == homePackage) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    repo.recordLaunch(pkg)
                    repo.recordTimeline(pkg, TimelineEventType.APP_FOREGROUND)
                    resumeMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val startTs = resumeMap.remove(pkg)
                    if (startTs != null && event.timeStamp >= startTs) {
                        val duration = event.timeStamp - startTs
                        if (duration > 0) {
                            repo.recordForeground(pkg, duration)
                            repo.recordTimeline(pkg, TimelineEventType.APP_BACKGROUND, duration)
                        }
                    }
                }
            }
        }

        settings.setUsageLastProcessedTs(now)
        repo.pruneOlderThanDays(90)

        return Result.success()
    }

    private fun getHomePackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName
    }

    companion object {
        const val PERIODIC_WORK_NAME = "usage_collector_periodic"
        const val ONE_TIME_WORK_NAME = "usage_collector_one_time"

        fun schedule(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<UsageCollectorWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )

            val oneTimeRequest = OneTimeWorkRequestBuilder<UsageCollectorWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
