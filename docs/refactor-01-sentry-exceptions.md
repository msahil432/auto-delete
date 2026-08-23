# Refactoring Plan: Sentry — Add Missing Exception Reporting

**Status:** Done ✓  
**Guideline:** §2 Error Handling & Sentry — "Never silently swallow exceptions — at minimum log + report."

## Problem

Many `catch` blocks use `catch (_: Exception) {}` (discard + no body) or `catch (_: Exception) { /* fallback */ }` without calling `Sentry.captureException()`. This makes production failures invisible.

## Files to Fix

Each file below has one or more `catch` blocks that **silently swallow** exceptions. Add `Sentry.captureException(e)` (rename `_` → `e`) in each.

| File | Lines (approx) | Notes |
|---|---|---|
| `accessibility/MultiToolAccessibilityService.kt` | 50 | `onServiceConnected` init — critical failure silenced |
| `accessibility/ShortFormHandler.kt` | 94, 172 | Blocking & home-navigation errors silenced |
| `blocking/BlockOverlayManager.kt` | 177, 197 | Overlay remove + home navigation |
| `service/TamperAlarm.kt` | 91 | MediaPlayer release failure |
| `service/BootReceiver.kt` | 35 | Geofence re-registration failure |
| `util/OemAutostart.kt` | 117 | Intent resolution |
| `notification/NotificationDigestWorker.kt` | 52, 70 | App label resolution (low-risk, log-only OK) |
| `ui/screens/OnboardingScreen.kt` | 167, 794, 815, 973, 1402, 1423, 1480 | Multiple intent launches |
| `ui/screens/NotificationVaultScreen.kt` | 74, 80 | App info resolution |
| `ui/screens/FilesHomeScreen.kt` | 167 | Intent launch |
| `ui/screens/BlockGroupEditScreen.kt` | 267, 935 | State parsing |
| `tracking/UsageCollectorWorker.kt` | 113 | Stats collection |
| `ui/components/AppPicker.kt` | 333, 348, 360, 366, 381, 397, 402, 411, 429 | Package resolution (bulk — use `Log.w` + `Sentry` selectively) |
| `data/SettingsRepository.kt` | 57 | Enum parsing fallback |

### Not included (already have Sentry):
- `MainActivity.kt:46`, `FileMonitorService.kt:117`, `FileActionWorker.kt:67,126`, `MoveHelper.kt:167`, `QrChallenge.kt:246`

### Deliberate exceptions (document, don't change):
- `PasswordSecurity.kt:61,75` — crypto fallback, controlled; consider adding a `Log.w`.
- `UsageViewModel.kt:105,123` — expected PackageManager miss; OK as-is.

## Pattern to Apply

```kotlin
// Before
} catch (_: Exception) {}

// After
} catch (e: Exception) {
    Sentry.captureException(e)
}
```

For low-risk/expected failures (app label lookup, intent resolution), use:
```kotlin
} catch (e: Exception) {
    Log.w(TAG, "Short description", e)
}
```

## Second task: Add `Sentry.addBreadcrumb()` calls

**Zero** calls to `Sentry.addBreadcrumb()` exist in the codebase. Add breadcrumbs before critical operations:

| Location | Breadcrumb |
|---|---|
| `FileMonitorService.restartObservers()` | `"Restarting ${configs.size} file observers"` |
| `FileActionWorker.doWork()` | `"Executing file action: $actionType for $filePath"` |
| `BlockEnforcementController.start()` | `"Block enforcement started"` |
| `GeofenceManager.addGeofence()` | `"Adding geofence: $profileId"` |
| `MultiToolAccessibilityService.onServiceConnected()` | `"Accessibility service connected"` |
| `StrictModeController.activate()` | `"Strict mode activated"` |

## Verification

Run `./gradlew testDebugUnitTest` — no tests should break (only adding logging).
Grep for remaining `catch (_:` to confirm none are left unaddressed.
