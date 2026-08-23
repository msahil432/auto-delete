# Refactoring Plan: KDoc Comments — Add Missing Documentation

**Status: Done ✓**

**Guideline violated:** §6 Documentation — "Every public/internal function and class must have a KDoc comment."

## Problem

Most repository classes, DAOs, entities, and several utility/service classes lack KDoc comments on their class declarations and public functions.

## Files to Fix

### Data Layer — Repositories (no class-level or function-level KDoc)

| File | What's missing |
|---|---|
| `data/BlockingRepository.kt` | Class KDoc + all 14 public functions |
| `data/BrowsingRepository.kt` | Class KDoc + all 6 public functions |
| `data/NotificationRepository.kt` | Class KDoc + all 7 public functions |
| `data/GeofenceRepository.kt` | Class KDoc + all 9 public functions + companion methods |
| `data/UsageRepository.kt` | Class KDoc + all 10 public functions |
| `data/SettingsRepository.kt` | Class KDoc (functions are self-documenting getters/setters — OK) |

### Data Layer — DAOs

| File | What's missing |
|---|---|
| `data/BlockingDao.kt` | Interface KDoc + all query functions |
| `data/BrowsingDao.kt` | Interface KDoc + all query functions |
| `data/NotificationDao.kt` | Interface KDoc + all query functions |
| `data/GeofenceDao.kt` | Interface KDoc + all query functions |
| `data/UsageDao.kt` | Interface KDoc + all query functions |

### Data Layer — Entities

| File | What's missing |
|---|---|
| `data/Entities.kt` | Class-level KDoc on `FolderConfig`, `DeletionMode`, `AppDao` |
| `data/BlockingEntities.kt` | Class-level KDoc on all data classes and enums |
| `data/BrowsingEntities.kt` | Class-level KDoc on `BrowsingEvent`, `BrowsingKind` |
| `data/NotificationEntities.kt` | Class-level KDoc on `VaultedNotification` |
| `data/GeofenceEntities.kt` | Class-level KDoc on `GeofenceProfile` |
| `data/UsageEntities.kt` | Class-level KDoc on all data classes and enums |
| `data/Converters.kt` | Class KDoc + function KDoc |
| `data/AppDatabase.kt` | Class KDoc |

### Already well-documented (skip):
- `data/FilterRule.kt` ✓
- `data/StrictModeState.kt` ✓ 
- `data/TimePeriodPreset.kt` ✓

### Services & Workers

| File | What's missing |
|---|---|
| `service/FileMonitorService.kt` | Class KDoc + key functions |
| `service/BootReceiver.kt` | Class KDoc |
| `service/FileActionWorker.kt` | Class KDoc + `doWork()` |
| `service/ActionReceiver.kt` | Class KDoc |
| `service/PromptHelper.kt` | Class KDoc + public functions |
| `service/MoveHelper.kt` | Class KDoc + public functions |
| `service/MultiToolNotificationListener.kt` | Class KDoc |
| `notification/NotificationDigestWorker.kt` | Class KDoc |
| `notification/NotificationDigestScheduler.kt` | Class KDoc |
| `tracking/UsageCollectorWorker.kt` | Class KDoc |
| `tracking/UsageStatsReader.kt` | Class KDoc |
| `tracking/ScreenUnlockReceiver.kt` | Class KDoc |

### Blocking

| File | What's missing |
|---|---|
| `blocking/BlockActivity.kt` | Class KDoc |
| `blocking/BlockEngine.kt` | Class KDoc + public functions |
| `blocking/BlockEnforcementController.kt` | Class KDoc + public functions |
| `blocking/BlockOverlayManager.kt` | Some functions documented, class-level missing |
| `blocking/StrictModeController.kt` | Class KDoc |

### UI / Navigation

| File | What's missing |
|---|---|
| `ui/navigation/AppNavigation.kt` | Function KDoc |
| `ui/navigation/BottomNavScaffold.kt` | Function KDoc |
| `ui/navigation/TopLevelDest.kt` | Enum/class KDoc |
| `ui/screens/UsageViewModel.kt` | Class KDoc + factory KDoc |
| `MainActivity.kt` | Class KDoc |
| `MultiToolApp.kt` | Class KDoc |

### Location

| File | What's missing |
|---|---|
| `location/GeofenceManager.kt` | Class KDoc + public functions |
| `location/GeofenceBroadcastReceiver.kt` | Class KDoc |

### Admin

| File | What's missing |
|---|---|
| `admin/DeviceAdminHelper.kt` | Class KDoc |
| `admin/MultiToolDeviceAdminReceiver.kt` | Class KDoc |

### Utils (mostly OK — only missing)

| File | What's missing |
|---|---|
| `util/BatteryOptimization.kt` | Function KDoc |
| `util/DurationFormat.kt` | Function KDoc |
| `util/NotificationAccess.kt` | Function KDoc |
| `util/OemAutostart.kt` | Class KDoc + public functions |
| `util/UsageAccess.kt` | Function KDoc |
| `util/SecureScreen.kt` | Function KDoc |

## KDoc Format

```kotlin
/** Brief one-line summary. */
fun simple() { ... }

/**
 * Multi-line when needed.
 * Explains *what* and *why*, not *how*.
 *
 * @param days Retention window; records older than this are purged.
 */
suspend fun pruneOlderThanDays(days: Int = 90) { ... }
```

## Verification

Ensure no existing comments are removed. Run `./gradlew testDebugUnitTest`.
