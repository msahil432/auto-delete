package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Repository for managing app usage statistics, screen time, device unlocks, and timeline events.
 */
class UsageRepository(
  private val dao: UsageDao,
  private val clock: () -> Long = System::currentTimeMillis
) {
  /** Returns the current date as an epoch day number. */
  fun epochDayNow(): Long =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  /** Returns the epoch millisecond timestamp of midnight at the start of today. */
  fun startOfDayMillisNow(): Long =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
      .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  /** Returns a [Flow] of [UsageDailyStat] records for today across all tracked packages. */
  fun todayStats(): Flow<List<UsageDailyStat>> = dao.statsForDay(epochDayNow())

  /** Returns a [Flow] representing the total foreground screen time in milliseconds across all apps today. */
  fun totalScreenTimeToday(): Flow<Long?> = dao.totalScreenTime(epochDayNow())

  /** Returns a [Flow] of the total device unlock count since midnight today. */
  fun unlocksToday(): Flow<Int> = dao.unlockCountSince(startOfDayMillisNow())

  /** Returns a [Flow] of [TimelineEvent] items recorded since midnight today. */
  fun timelineToday(): Flow<List<TimelineEvent>> = dao.timelineSince(startOfDayMillisNow())

  /**
   * Calculates the total combined foreground minutes spent today on the specified [packages].
   *
   * @param packages List of package names to sum.
   * @return Combined total time in whole minutes.
   */
  suspend fun getTodayForegroundMinutesFor(packages: List<String>): Long {
    val day = epochDayNow()
    val stats = dao.getStatsForDaySync(day)
    val totalMillis = stats.filter { it.packageName in packages }.sumOf { it.foregroundMillis }
    return totalMillis / 60_000L
  }

  /**
   * Adds foreground usage duration for [pkg] to today's daily aggregate.
   *
   * @param pkg The package name.
   * @param addedMillis Foreground time to append in milliseconds.
   */
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

  /**
   * Records an app launch event and increments today's launch count for [pkg].
   *
   * @param pkg The launched package name.
   */
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

  /**
   * Records a device screen unlock event.
   *
   * @param type The mechanism used to unlock the device.
   */
  suspend fun recordUnlock(type: UnlockType) {
    dao.insertUnlock(UnlockEvent(timestamp = clock(), type = type))
  }

  /**
   * Records a timeline event (e.g. app foreground / background transition).
   *
   * @param pkg Package name involved in the event.
   * @param type The timeline event category.
   * @param durationMillis Optional duration in milliseconds if applicable.
   */
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

  /**
   * Prunes historical launch, unlock, and timeline events older than [days] to keep the database lean.
   *
   * @param days Retention window in days (default: 90).
   */
  suspend fun pruneOlderThanDays(days: Int = 90) {
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
    dao.pruneLaunches(cutoff)
    dao.pruneUnlocks(cutoff)
    dao.pruneTimeline(cutoff)
  }
}

