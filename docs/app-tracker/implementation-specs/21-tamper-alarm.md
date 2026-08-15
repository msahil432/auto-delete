# 21 — Tamper Alarm

> **Status:** ✅ Complete

Prerequisites: `11-accessibility-core.md`, `18-device-admin.md`.

## Goal

Trigger an audible siren if the user opens protected system settings (app info,
accessibility settings, device-admin settings, force-stop) for Multi Tool while a
strict-mode block is active — a deterrent against tampering.

## Approach

Use the AccessibilityService (spec 11) to detect when a protected system screen for
this app's package appears in the foreground, then play a loud alarm until the user
navigates away or passes a challenge.

## Files created / modified

- Created `accessibility/TamperSignatures.kt` containing known settings packages and danger keywords/classes.
- Created `accessibility/TamperHandler.kt` implementing `AccessibilityHandler` to detect tampering when strict mode and tamper alarm are active.
- Created `service/TamperAlarm.kt` — plays/stops the siren on `AudioAttributes.USAGE_ALARM` with safety auto-stop.
- Updated `accessibility/MultiToolAccessibilityService.kt` — registered `TamperHandler`.
- Updated `data/SettingsRepository.kt` — added `tamper_alarm_enabled` key and flow.
- Updated `ui/screens/AppSettingsScreen.kt` & `ui/screens/StrictModeScreen.kt` — added Tamper Alarm switch toggles and descriptions.
- Created `accessibility/TamperHandlerTest.kt` & `service/TamperAlarmTest.kt` — automated test suites.

## Implementation Decisions & Key Design Details

1. **Audio Output & Stream**: `TamperAlarm` uses `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)` configured with `AudioAttributes.USAGE_ALARM` / `CONTENT_TYPE_SONIFICATION` for high-priority audible alerting.
2. **Runaway Noise Safety Cutoff**: `TamperAlarm` automatically stops playback after a maximum duration safety timeout (30 seconds) via a Looper handler to avoid indefinite runaway noise.
3. **Detection Heuristic**: `TamperHandler` detects foreground system settings (`com.android.settings` and common OEM settings packages) and inspects `AccessibilityNodeInfo` hierarchy and event classes for app references (`com.msahil432.multitool` or app title) paired with protected controls (App Info / Force Stop / Uninstall / Accessibility Service toggle / Device Admin deactivation).
4. **Arming Preconditions**: Alarm only triggers when both `strict_mode_active` and `tamper_alarm_enabled` are true. Navigating away immediately stops playback and dismisses the block overlay.

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