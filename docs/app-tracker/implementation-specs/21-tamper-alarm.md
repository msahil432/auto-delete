# 21 — Tamper Alarm

> **Status:** 🔲 Not Started

Prerequisites: `11-accessibility-core.md`, `18-device-admin.md`.

## Goal

Trigger an audible siren if the user opens protected system settings (app info,
accessibility settings, device-admin settings, force-stop) for Multi Tool while a
strict-mode block is active — a deterrent against tampering.

## Approach

Use the AccessibilityService (spec 11) to detect when a protected system screen for
this app's package appears in the foreground, then play a loud alarm until the user
navigates away or passes a challenge.

## Files to create / modify

- Create `accessibility/TamperHandler.kt` implementing `AccessibilityHandler`.
- Create `service/TamperAlarm.kt` — plays/stops the siren.
- Add a raw sound resource `res/raw/siren.ogg` (or use
  `RingtoneManager.TYPE_ALARM`).
- Settings/DataStore: `tamper_alarm_enabled` (default false), only meaningful while
  strict mode active.

## Detection signatures

Watch `TYPE_WINDOW_STATE_CHANGED` for these foreground packages/screens:
- `com.android.settings` while the visible content references this app's package
  (find nodes containing `context.packageName`), especially:
  - App info / App details (force-stop, uninstall buttons).
  - Accessibility settings (to disable the service).
  - Device admin settings (to deactivate admin).
Heuristic: when settings shows this app + a known danger control, treat as tamper.

## Alarm

```kotlin
object TamperAlarm {
  fun start(context: Context) { /* MediaPlayer on TYPE_ALARM stream, looping, max vol
     respecting audio focus; or Ringtone */ }
  fun stop() { /* release */ }
}
```

- Only arm when `strict_mode_active && tamper_alarm_enabled`.
- Stop when the foreground leaves the protected screen, or after a challenge
  (spec 20) passes, or after a max duration (e.g., 30s) to avoid runaway noise.
- Also raise the block overlay (spec 12) with a "Tamper detected" message.

## UX

- Settings toggle "Tamper alarm" (`SettingRow` + `Switch`) with caption explaining it
  sounds an alarm if protected settings are opened during strict mode.
- When triggered: overlay + siren; the only quiet exit is leaving the settings screen
  or passing the unlock challenge.
- Accessibility: overlay text announced; toggle labeled.

## Acceptance criteria

- With strict mode active + alarm enabled, opening this app's App-info /
  accessibility / device-admin settings starts the siren + shows the overlay.
- Navigating away stops the alarm.
- Alarm never fires when strict mode is inactive or the toggle is off.
- Max-duration safety stop works.

## Out of scope / boundaries

- Cannot block Safe Mode or `adb` removal (documented boundary). This is a deterrent,
  not a hard prevention.