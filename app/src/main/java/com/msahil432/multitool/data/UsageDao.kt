package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for app usage metrics, daily summaries, launch events, screen unlocks, and timeline history.
 */
@Dao
interface UsageDao {
  // ── daily stats ──

  /** Returns a [Flow] of daily usage statistics for the given epoch [day], ordered by foreground time descending. */
  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day ORDER BY foregroundMillis DESC")
  fun statsForDay(day: Long): Flow<List<UsageDailyStat>>

  /** Synchronously retrieves daily usage statistics for the given epoch [day]. */
  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day ORDER BY foregroundMillis DESC")
  suspend fun getStatsForDaySync(day: Long): List<UsageDailyStat>

  /** Synchronously retrieves the daily usage statistic for a specific package [pkg] on epoch [day]. */
  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day AND packageName = :pkg LIMIT 1")
  suspend fun statFor(day: Long, pkg: String): UsageDailyStat?

  /** Inserts or updates a daily usage statistic entry. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDailyStat(stat: UsageDailyStat)

  /** Returns a [Flow] with the sum of foreground milliseconds across all apps on epoch [day]. */
  @Query("SELECT SUM(foregroundMillis) FROM usage_daily_stats WHERE dateEpochDay = :day")
  fun totalScreenTime(day: Long): Flow<Long?>

  // ── launches ──

  /** Inserts a new app launch event record. */
  @Insert
  suspend fun insertLaunch(e: AppLaunchEvent)

  /** Counts the number of launch events for [pkg] recorded on or after [since]. */
  @Query("SELECT COUNT(*) FROM app_launch_events WHERE packageName = :pkg AND timestamp >= :since")
  suspend fun launchCountSince(pkg: String, since: Long): Int

  // ── unlocks ──

  /** Inserts a new device unlock event record. */
  @Insert
  suspend fun insertUnlock(e: UnlockEvent)

  /** Returns a [Flow] count of user unlock events occurring on or after [since]. */
  @Query("SELECT COUNT(*) FROM unlock_events WHERE type = 'USER_PRESENT' AND timestamp >= :since")
  fun unlockCountSince(since: Long): Flow<Int>

  // ── timeline ──

  /** Inserts a new timeline event record. */
  @Insert
  suspend fun insertTimeline(e: TimelineEvent)

  /** Returns a [Flow] of timeline events recorded on or after [since], ordered newest first. */
  @Query("SELECT * FROM timeline_events WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun timelineSince(since: Long): Flow<List<TimelineEvent>>

  // ── retention ──

  /** Prunes app launch records older than [cutoff]. */
  @Query("DELETE FROM app_launch_events WHERE timestamp < :cutoff")
  suspend fun pruneLaunches(cutoff: Long)

  /** Prunes unlock event records older than [cutoff]. */
  @Query("DELETE FROM unlock_events WHERE timestamp < :cutoff")
  suspend fun pruneUnlocks(cutoff: Long)

  /** Prunes timeline event records older than [cutoff]. */
  @Query("DELETE FROM timeline_events WHERE timestamp < :cutoff")
  suspend fun pruneTimeline(cutoff: Long)
}

