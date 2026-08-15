# 06 — Usage Stats Collector

Prerequisites: `04-usage-repository.md`, `05-usage-permission.md`.

## Goal

Periodically read `UsageStatsManager` / `UsageEvents` and persist foreground time,
launch counts, and foreground/background timeline segments into `UsageRepository`.

## Approach

Use a **WorkManager periodic worker** (every 15 min, the WM minimum) plus an
immediate one-shot run when the app opens. Query `UsageEvents` since the last
processed timestamp; do not double-count. Store the last processed timestamp in
DataStore.

> UsageStats is aggregate and slightly delayed; that is acceptable for reporting.
> Real-time foreground detection for blocking uses the AccessibilityService instead
> (spec 11), not this collector.

## Files to create / modify

- Create `tracking/UsageCollectorWorker.kt` (a `CoroutineWorker`).
- Create `tracking/UsageStatsReader.kt` — wraps `UsageStatsManager`.
- Add DataStore key `usage_last_processed_ts` (Long) in `SettingsRepository`.
- Schedule the periodic worker from `MultiToolApp.onCreate()` using
  `WorkManager.getInstance(this).enqueueUniquePeriodicWork(...)` with
  `ExistingPeriodicWorkPolicy.KEEP`.

## UsageStatsReader

```kotlin
class UsageStatsReader(private val context: Context) {
  // Returns events since [sinceMillis] up to now.
  fun queryEvents(sinceMillis: Long, nowMillis: Long): List<UsageEvents.Event> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val events = usm.queryEvents(sinceMillis, nowMillis)
    val out = ArrayList<UsageEvents.Event>()
    val e = UsageEvents.Event()
    while (events.hasNextEvent()) { val ev = UsageEvents.Event(); events.getNextEvent(ev); out += ev }
    return out
  }
}
```

## Worker logic

```kotlin
class UsageCollectorWorker(ctx, params) : CoroutineWorker(ctx, params) {
  override suspend fun doWork(): Result {
    if (!UsageAccess.isGranted(applicationContext)) return Result.success() // nothing to do
    val now = System.currentTimeMillis()
    val since = settings.usageLastProcessedTs.first().let { if (it == 0L) now - 24*3600_000 else it }
    val events = reader.queryEvents(since, now)
    // Track per-package foreground start times to compute durations.
    // ACTIVITY_RESUMED  -> record launch + open a foreground segment + timeline APP_FOREGROUND
    // ACTIVITY_PAUSED/STOPPED -> close segment, addedMillis = pausedTs - resumedTs
    //   -> repo.recordForeground(pkg, addedMillis); repo.recordTimeline(pkg, APP_BACKGROUND, addedMillis)
    // Save now as usage_last_processed_ts.
    repo.pruneOlderThanDays(90)
    return Result.success()
  }
}
```

Details:
- Use `UsageEvents.Event.ACTIVITY_RESUMED` for launches (`repo.recordLaunch`).
- Maintain a `MutableMap<String, Long>` of pkg → resume timestamp within the batch;
  on pause/stop, compute duration and call `repo.recordForeground`.
- Ignore this app's own package and the launcher/home package if desired.
- Persist `usageLastProcessedTs = now` at the end.

## Acceptance criteria

- After the worker runs, `usage_daily_stats` reflects real foreground time for used
  apps and `app_launch_events` has launch rows.
- Re-running does not double-count (respects last-processed timestamp).
- Worker is a no-op (returns success) when permission is not granted.
- Old raw events are pruned beyond 90 days.

## Out of scope

- Unlock counting (spec 07). UI (spec 08). Blocking (specs 11-13).
