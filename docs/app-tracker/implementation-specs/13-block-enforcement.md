# 13 — Block Enforcement Engine

> **Status:** ✅ Done

Prerequisites: `04-usage-repository.md`, `09-blocking-entities.md`,
`11-accessibility-core.md`, `12-block-overlay.md`.

## Goal

Tie everything together: when the foreground app is a blocked target and a rule's
condition is met, show the block overlay and log an interception.

## Files to create / modify

- Create `blocking/BlockEngine.kt` — the evaluator.
- Create `blocking/BlockEnforcementController.kt` — observes `ForegroundAppState` and
  drives the overlay.
- Register the controller from the foreground service (`FileMonitorService.kt`).
- Update `BlockingDao.kt`, `BlockingRepository.kt`, `UsageDao.kt`, and `UsageRepository.kt` with required helpers.
- Create unit tests in `app/src/test/java/com/msahil432/multitool/blocking/BlockEngineTest.kt`.

## Evaluation logic

```kotlin
class BlockEngine(
  private val blockingRepo: BlockingRepository,
  private val usageRepo: UsageRepository? = null,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  // Returns a BlockDecision for the given foreground package, or Allowed.
  suspend fun evaluate(pkg: String): BlockDecision {
    val groups = blockingRepo.enabledGroupsContaining(pkg)  // package in packageNames
    for (g in groups) {
      val counter = blockingRepo.counterForToday(g.id)      // create if missing
      for (rule in blockingRepo.enabledRules(g.id)) {
        when (rule.type) {
          SCHEDULE      -> if (nowWithinSchedule(rule)) return Blocked(rule, "Blocked by schedule", g)
          DAILY_QUOTA   -> if (counter.usedForegroundMillis >= rule.dailyQuotaMinutes*60_000L)
                             return Blocked(rule, "Daily limit reached", g)
          LAUNCH_LIMIT  -> if (counter.launchesUsed >= rule.maxLaunchesPerDay)
                             return Blocked(rule, "Launch limit reached", g)
          SESSION_LIMIT -> if (clock() < counter.lockedUntil)
                             return Blocked(rule, "Session cooldown", g)
          GOAL_UNLOCK   -> if (!goalMet(rule)) return Blocked(rule, "Finish your goal first", g)
        }
      }
    }
    return Allowed
  }
}
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
```

## Implementation Decisions & Details

1. **Rule Evaluation Matrix:**
   - `SCHEDULE`: bitwise day-of-week check (`daysOfWeekMask`) + start/end minute-of-day handling with midnight wrap.
   - `DAILY_QUOTA`: verifies group's cumulative `usedForegroundMillis` against `dailyQuotaMinutes * 60_000L`.
   - `LAUNCH_LIMIT`: checks group's `launchesUsed` against `maxLaunchesPerDay`.
   - `SESSION_LIMIT`: checks `lockedUntil` timestamp cooldown lockout.
   - `GOAL_UNLOCK`: aggregates today's foreground minutes from `UsageRepository` across `goalPackageNames` and checks against `goalRequiredMinutes`.
2. **Controller & Real-Time Tracking:**
   - `BlockEnforcementController` listens to `ForegroundAppState.currentPackage` and runs periodic 5-second ticks.
   - Updates `BlockCounter` launches and foreground duration incrementally.
   - Triggers `BlockOverlayManager.show(...)` debounced, inserts `BlockInterception`, and writes `BLOCK_INTERCEPT` timeline event.
   - Dismisses overlay automatically when foreground app transitions to an `Allowed` state.
3. **Service Integration:**
   - Started and stopped directly in `FileMonitorService`.

## Acceptance criteria

- Opening a blocked app during a matching schedule shows the overlay immediately.
- Exceeding a daily quota / launch cap / session limit triggers the overlay.
- Goal-unlock stays blocked until the required productive minutes are reached, then
  allows.
- Each block writes a `BlockInterception` row and a `BLOCK_INTERCEPT` timeline event.
- Counters reset at midnight; no double counting.

## Out of scope

- Short-form sub-feature filtering (14). Friction challenge UI (20).