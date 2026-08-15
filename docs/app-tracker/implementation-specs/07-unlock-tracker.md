# 07 — Device Unlock Tracker

> **Status:** 🔲 Not Started

Prerequisites: `04-usage-repository.md`.

## Goal

Record screen-on and device-unlock (user present) events with timestamps so the
Usage dashboard can show unlock frequency.

## Approach

`ACTION_SCREEN_ON` and `ACTION_USER_PRESENT` are **not** deliverable via manifest-
declared receivers (they require a runtime-registered receiver). Register a
`BroadcastReceiver` from the already-running foreground service
(`FileMonitorService`, or a dedicated monitor service) in `onCreate`, unregister in
`onDestroy`.

## Files to create / modify

- Create `tracking/ScreenUnlockReceiver.kt`:
  ```kotlin
  class ScreenUnlockReceiver(private val onEvent: (UnlockType) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        Intent.ACTION_SCREEN_ON    -> onEvent(UnlockType.SCREEN_ON)
        Intent.ACTION_USER_PRESENT -> onEvent(UnlockType.USER_PRESENT)
      }
    }
  }
  ```
- Modify `service/FileMonitorService.kt` (or a new `tracking/MonitorService.kt` if
  you prefer separation): register the receiver dynamically:
  ```kotlin
  private val unlockReceiver = ScreenUnlockReceiver { type ->
    scope.launch { usageRepo.recordUnlock(type)
      if (type == UnlockType.USER_PRESENT) usageRepo.recordTimeline("", TimelineEventType.UNLOCK) }
  }
  // in onCreate:
  registerReceiver(unlockReceiver, IntentFilter().apply {
    addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) })
  // in onDestroy: unregisterReceiver(unlockReceiver)
  ```
- Provide the service access to a `UsageRepository` instance (build from
  `(application as MultiToolApp).database`).

## Notes

- No new manifest permission is required for these system broadcasts.
- Guard `unregisterReceiver` in a try/catch to avoid "receiver not registered"
  crashes on edge cases.

## Acceptance criteria

- Turning the screen on inserts a `SCREEN_ON` unlock event.
- Unlocking the device (past keyguard) inserts a `USER_PRESENT` event and a
  `UNLOCK` timeline event.
- Events stop when the service is destroyed and resume when it restarts.
- `unlocksToday()` in the repository reflects the count.

## Out of scope

- Displaying unlock stats (spec 08).