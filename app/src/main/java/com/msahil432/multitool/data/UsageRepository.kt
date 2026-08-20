package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class UsageRepository(
  private val dao: UsageDao,
  private val clock: () -> Long = System::currentTimeMillis
) {
  fun epochDayNow(): Long =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  fun startOfDayMillisNow(): Long =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
      .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  fun todayStats(): Flow<List<UsageDailyStat>> = dao.statsForDay(epochDayNow())

  fun totalScreenTimeToday(): Flow<Long?> = dao.totalScreenTime(epochDayNow())

  fun unlocksToday(): Flow<Int> = dao.unlockCountSince(startOfDayMillisNow())

  fun timelineToday(): Flow<List<TimelineEvent>> = dao.timelineSince(startOfDayMillisNow())

  suspend fun getTodayForegroundMinutesFor(packages: List<String>): Long {
    val day = epochDayNow()
    val stats = dao.getStatsForDaySync(day)
    val totalMillis = stats.filter { it.packageName in packages }.sumOf { it.foregroundMillis }
    return totalMillis / 60_000L
  }

  suspend fun recordForeground(pkg: String, addedMillis: Long) {
    val day = epochDayNow()
    val now = clock()
    val existing = dao.statFor(day, pkg)
    val updated = if (existing != null) {
      existing.copy(
        foregroundMillis = existing.foregroundMillis + addedMillis,
        lastUpdated = now
      )
    } else {
      UsageDailyStat(
        dateEpochDay = day,
        packageName = pkg,
        foregroundMillis = addedMillis,
        launchCount = 0,
        lastUpdated = now
      )
    }
    dao.upsertDailyStat(updated)
  }

  suspend fun recordLaunch(pkg: String) {
    val day = epochDayNow()
    val now = clock()
    dao.insertLaunch(AppLaunchEvent(packageName = pkg, timestamp = now))
    val existing = dao.statFor(day, pkg)
    val updated = if (existing != null) {
      existing.copy(
        launchCount = existing.launchCount + 1,
        lastUpdated = now
      )
    } else {
      UsageDailyStat(
        dateEpochDay = day,
        packageName = pkg,
        foregroundMillis = 0,
        launchCount = 1,
        lastUpdated = now
      )
    }
    dao.upsertDailyStat(updated)
  }

  suspend fun recordUnlock(type: UnlockType) {
    dao.insertUnlock(UnlockEvent(timestamp = clock(), type = type))
  }

  suspend fun recordTimeline(pkg: String, type: TimelineEventType, durationMillis: Long? = null) {
    dao.insertTimeline(
      TimelineEvent(
        timestamp = clock(),
        packageName = pkg,
        eventType = type,
        durationMillis = durationMillis
      )
    )
  }

  suspend fun pruneOlderThanDays(days: Int = 90) {
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
    dao.pruneLaunches(cutoff)
    dao.pruneUnlocks(cutoff)
    dao.pruneTimeline(cutoff)
  }
}
