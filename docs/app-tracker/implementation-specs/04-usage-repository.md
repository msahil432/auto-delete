# 04 — Usage Tracking: DAO & Repository

> **Status:** ✅ Complete

Prerequisites: `03-usage-data-entities.md`.

## Goal

Provide DAO queries and a repository for writing collected usage data and reading
aggregates for the UI.

## Files to create / modify

- Modify `data/AppDatabase.kt` `AppDao` (or create a new `UsageDao` interface and
  add `abstract fun usageDao(): UsageDao` to `AppDatabase`). Prefer a separate
  `UsageDao` to keep files small.
- Create `data/UsageDao.kt`.
- Create `data/UsageRepository.kt`.

## UsageDao

```kotlin
@Dao
interface UsageDao {
  // ── daily stats ──
  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day ORDER BY foregroundMillis DESC")
  fun statsForDay(day: Long): Flow<List<UsageDailyStat>>

  @Query("SELECT * FROM usage_daily_stats WHERE dateEpochDay = :day AND packageName = :pkg LIMIT 1")
  suspend fun statFor(day: Long, pkg: String): UsageDailyStat?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDailyStat(stat: UsageDailyStat)

  @Query("SELECT SUM(foregroundMillis) FROM usage_daily_stats WHERE dateEpochDay = :day")
  fun totalScreenTime(day: Long): Flow<Long?>

  // ── launches ──
  @Insert suspend fun insertLaunch(e: AppLaunchEvent)
  @Query("SELECT COUNT(*) FROM app_launch_events WHERE packageName = :pkg AND timestamp >= :since")
  suspend fun launchCountSince(pkg: String, since: Long): Int

  // ── unlocks ──
  @Insert suspend fun insertUnlock(e: UnlockEvent)
  @Query("SELECT COUNT(*) FROM unlock_events WHERE type = 'USER_PRESENT' AND timestamp >= :since")
  fun unlockCountSince(since: Long): Flow<Int>

  // ── timeline ──
  @Insert suspend fun insertTimeline(e: TimelineEvent)
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
```

## UsageRepository

```kotlin
class UsageRepository(private val dao: UsageDao) {
  fun todayStats(): Flow<List<UsageDailyStat>>      // uses LocalDate.now().toEpochDay()
  fun totalScreenTimeToday(): Flow<Long?>
  fun unlocksToday(): Flow<Int>
  fun timelineToday(): Flow<List<TimelineEvent>>

  suspend fun recordForeground(pkg: String, addedMillis: Long)  // upsert add to today's stat
  suspend fun recordLaunch(pkg: String)                          // insert event + bump daily launchCount
  suspend fun recordUnlock(type: UnlockType)
  suspend fun recordTimeline(pkg: String, type: TimelineEventType, durationMillis: Long? = null)
  suspend fun pruneOlderThanDays(days: Int)                      // default retention e.g. 90
}
```

- Compute "today" via `java.time.LocalDate.now().toEpochDay()`; expose a helper
  `fun epochDayNow(): Long`.
- `recordForeground` reads today's `UsageDailyStat` for the pkg, adds millis (or
  inserts a new row), updates `lastUpdated`.
- Retention default: keep raw events 90 days, daily stats indefinitely (small).

## Acceptance criteria

- Repository compiles and all queries run without Room warnings.
- Writing then reading returns consistent aggregates (verify with a small unit or
  instrumented test using an in-memory Room DB — optional but recommended).
- `pruneOlderThanDays` removes only rows older than the cutoff.

## Out of scope

- Where the data comes from (collectors) — specs 06/07. UI — spec 08.

## Implementation Decisions

- Created `data/UsageDao.kt` containing all daily stat, launch event, unlock event, timeline event queries and retention pruning methods.
- Added `abstract fun usageDao(): UsageDao` to `data/AppDatabase.kt`.
- Created `data/UsageRepository.kt` with `epochDayNow()`, `startOfDayMillisNow()`, Flow queries (`todayStats()`, `totalScreenTimeToday()`, `unlocksToday()`, `timelineToday()`), recording methods (`recordForeground`, `recordLaunch`, `recordUnlock`, `recordTimeline`), and `pruneOlderThanDays(days = 90)`.
- Added unit test suite `UsageRepositoryTest.kt` in `app/src/test/java/com/msahil432/multitool/data/UsageRepositoryTest.kt` with Robolectric and in-memory Room database.