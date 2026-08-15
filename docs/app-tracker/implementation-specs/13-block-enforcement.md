# 13 — Block Enforcement Engine

Prerequisites: `04-usage-repository.md`, `09-blocking-entities.md`,
`11-accessibility-core.md`, `12-block-overlay.md`.

## Goal

Tie everything together: when the foreground app is a blocked target and a rule's
condition is met, show the block overlay and log an interception.

## Files to create / modify

- Create `blocking/BlockEngine.kt` — the evaluator.
- Create `blocking/BlockEnforcementController.kt` — observes `ForegroundAppState` and
  drives the overlay.
- Register the controller from the foreground service (or start on service create).

## Evaluation logic

```kotlin
class BlockEngine(
  private val blockingRepo: BlockingRepository,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  // Returns a BlockDecision for the given foreground package, or Allowed.
  suspend fun evaluate(pkg: String): BlockDecision {
    val groups = blockingRepo.enabledGroupsContaining(pkg)  // package in packageNames
    for (g in groups) {
      val counter = blockingRepo.counterForToday(g.id)      // create if missing
      for (rule in blockingRepo.enabledRules(g.id)) {
        when (rule.type) {
          SCHEDULE      -> if (nowWithinSchedule(rule)) return Blocked(rule, "Blocked by schedule")
          DAILY_QUOTA   -> if (counter.usedForegroundMillis >= rule.dailyQuotaMinutes*60_000L)
                             return Blocked(rule, "Daily limit reached")
          LAUNCH_LIMIT  -> if (counter.launchesUsed >= rule.maxLaunchesPerDay)
                             return Blocked(rule, "Launch limit reached")
          SESSION_LIMIT -> if (clock() < counter.lockedUntil)
                             return Blocked(rule, "Session cooldown")
          GOAL_UNLOCK   -> if (!goalMet(rule)) return Blocked(rule, "Finish your goal first")
        }
      }
    }
    return Allowed
  }
}
sealed interface BlockDecision
data object Allowed : BlockDecision
data class Blocked(val rule: BlockRule, val reason: String) : BlockDecision
```

Helpers:
- `nowWithinSchedule`: check `daysOfWeekMask` for today and minute-of-day in
  [start,end] (handle wrap past midnight).
- `goalMet`: sum today's foreground minutes for `goalPackageNames` (via
  `UsageRepository`) ≥ `goalRequiredMinutes`.

## Controller / counters

```kotlin
class BlockEnforcementController(scope, foregroundState, engine, blockingRepo, usageRepo) {
  // collect ForegroundAppState:
  //  - on new pkg: increment launch counter for any group containing it;
  //    start a session timer.
  //  - periodically (every ~5s while a blocked-group app is foreground) add elapsed
  //    to counter.usedForegroundMillis; when a session exceeds maxSessionMinutes,
  //    set counter.lockedUntil = now + cooldownMinutes and block.
  //  - after each update, call engine.evaluate(pkg); if Blocked ->
  //    BlockOverlayManager.show(...) + blockingRepo.logInterception(...) +
  //    usageRepo.recordTimeline(pkg, BLOCK_INTERCEPT).
  //  - if Allowed and overlay showing for this pkg -> hide().
}
```

- Reset counters at local midnight (compare `dateEpochDay`).
- Respect `allowFriction` from strict mode (spec 19): pass it into the overlay.
- Debounce: don't re-show the overlay every tick; track "currently blocking pkg".

## Acceptance criteria

- Opening a blocked app during a matching schedule shows the overlay immediately.
- Exceeding a daily quota / launch cap / session limit triggers the overlay.
- Goal-unlock stays blocked until the required productive minutes are reached, then
  allows.
- Each block writes a `BlockInterception` row and a `BLOCK_INTERCEPT` timeline event.
- Counters reset at midnight; no double counting.

## Out of scope

- Short-form sub-feature filtering (14). Friction challenge UI (20).
