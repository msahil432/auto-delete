# 19 — Strict Mode Controller

Prerequisites: `09-blocking-entities.md`, `18-device-admin.md`.

## Goal

Implement asymmetric lock-in: while strict mode is active, focus rules can be made
*stricter* but never weakened, edited-down, or deleted; deactivation requires a
friction challenge (spec 20).

## Files to create / modify

- Create `data/StrictModeState.kt` (DataStore-backed via `SettingsRepository`):
  - `strict_mode_active: Boolean`
  - `strict_mode_started_at: Long`
  - `strict_mode_end_at: Long` (0 = until manually unlocked)
  - `strict_unlock_method: String` (enum name: TEXT, PIN, COOLDOWN, QR)
  - `strict_pending_deactivation_at: Long` (for cooldown method)
- Create `blocking/StrictModeController.kt`.

## Controller API

```kotlin
object StrictModeController {
  val isActive: StateFlow<Boolean>
  fun activate(method: UnlockMethod, endAt: Long, params: UnlockParams)
  // Rule guards — called by the Blocking UI (spec 10):
  fun canDeleteGroup(): Boolean            // false while active
  fun canWeakenRule(old: BlockRule, new: BlockRule): Boolean   // false if new is weaker
  fun canDisableGroup(): Boolean           // false while active
  // Deactivation goes through friction (spec 20):
  fun requestDeactivation(): DeactivationFlow
  fun completeDeactivation()               // called after challenge passes
}
enum class UnlockMethod { TEXT, PIN, COOLDOWN, QR }
```

## "Weaker" definition (canWeakenRule)

A change is weaker (and thus blocked while active) if it:
- disables a rule/group, or
- increases `dailyQuotaMinutes`, `maxLaunchesPerDay`, `maxSessionMinutes`, or
  reduces `cooldownMinutes`, or
- narrows a SCHEDULE window (fewer days or shorter blocked span), or
- lowers `goalRequiredMinutes` / shrinks `goalPackageNames`, or
- removes packages from a group's `packageNames`.
Adding restrictions (more apps, longer windows, lower quotas) is always allowed.

## Integration

- Blocking UI (spec 10) must call `canDeleteGroup`/`canWeakenRule`/`canDisableGroup`
  and disable those controls while active, showing a lock hint.
- Block engine (spec 13) reads `isActive` to decide `allowFriction` on the overlay.
- Device admin (spec 18) is activated on `activate()` and only removed in
  `completeDeactivation()`.

## UX / Screen Design

- **Strict mode screen** (from Settings/Blocking): explains the lock-in, lets user
  pick an unlock method + optional end time, then a big "Activate strict mode"
  button guarded by a `ConfirmDialog` ("You won't be able to weaken rules until you
  pass the unlock challenge").
- **Active banner:** persistent indicator across Blocking screens ("Strict mode
  active — rules are locked"). Disabled/greyed weakening controls with a lock icon.
- **Deactivate button:** launches the friction flow (spec 20).
- Accessibility: lock states announced; buttons labeled. Dark mode: theme-driven.

## Acceptance criteria

- While active, deleting/disabling/weakening a rule is prevented in the UI and at the
  controller level.
- Strengthening rules works while active.
- Device admin becomes active on activation and only removed after successful
  deactivation.
- State survives process death and reboot (DataStore).

## Out of scope

- The challenge implementations themselves (spec 20). Tamper alarm (spec 21).
