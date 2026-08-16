package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
  // ── daily stats ──
  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day ORDER BY foregroundMillis DESC")
  fun statsForDay(day: Long): Flow<List<UsageDailyStat>>

  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day ORDER BY foregroundMillis DESC")
  suspend fun getStatsForDaySync(day: Long): List<UsageDailyStat>

  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day AND packageName = :pkg LIMIT 1")
  suspend fun statFor(day: Long, pkg: String): UsageDailyStat?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDailyStat(stat: UsageDailyStat)

  @Query("SELECT SUM(foregroundMillis) FROM usage_daily_stats WHERE dateEpochDay = :day")
  fun totalScreenTime(day: Long): Flow<Long?>

  // ── launches ──
  @Insert
  suspend fun insertLaunch(e: AppLaunchEvent)

  @Query("SELECT COUNT(*) FROM app_launch_events WHERE packageName = :pkg AND timestamp >= :since")
  suspend fun launchCountSince(pkg: String, since: Long): Int

  // ── unlocks ──
  @Insert
  suspend fun insertUnlock(e: UnlockEvent)

  @Query("SELECT COUNT(*) FROM unlock_events WHERE type = 'USER_PRESENT' AND timestamp >= :since")
  fun unlockCountSince(since: Long): Flow<Int>

  // ── timeline ──
  @Insert
  suspend fun insertTimeline(e: TimelineEvent)

  @Query("SELECT * FROM timeline_events WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun timelineSince(since: Long): Flow<List<TimelineEvent>>

  // ── retention ──
  @Query("DELETE FROM app_launch_events WHERE timestamp < :cutoff")
  suspend fun pruneLaunches(cutoff: Long)

  @Query("DELETE FROM unlock_events WHERE timestamp < :cutoff")
  suspend fun pruneUnlocks(cutoff: Long)

  @Query("DELETE FROM timeline_events WHERE timestamp < :cutoff")
  suspend fun pruneTimeline(cutoff: Long)
}
