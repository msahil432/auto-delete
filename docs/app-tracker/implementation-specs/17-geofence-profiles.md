# 17 — Geofenced Location Profiles

> **Status:** ✅ Completed

## Implementation Decisions & Notes
- **Staged Permission Flow:** Foreground location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) is requested first. When granted on Android 10+ (API 29+), a prominent disclosure dialog explains why background location is needed before requesting `ACCESS_BACKGROUND_LOCATION`.
- **Transition Behavior:** Entering a geofence enables `onEnterGroupIds` and disables `onExitGroupIds`; exiting enables `onExitGroupIds` and disables `onEnterGroupIds`.
- **Timeline Logging:** Geofence transition events are recorded with `TimelineEventType.GEOFENCE_ENTER` and `TimelineEventType.GEOFENCE_EXIT`.
- **Boot Persistence:** `BootReceiver` calls `GeofenceManager.reRegisterAll` upon `BOOT_COMPLETED`.
- **Room Migration:** `MIGRATION_9_10` created to add `geofence_profiles` table, bumping database version from 9 to 10.

Prerequisites: `09-blocking-entities.md`, `25-design-system.md`.

## Goal

Activate/deactivate blocking profiles based on entering/exiting circular geofences
(e.g., enable work-focus when arriving at the office).

## Dependencies

- Uncomment/enable `play-services-location` in `app/build.gradle.kts`
  (`implementation(libs.play.services.location)`) — the catalog entry already exists.

## Files to create / modify

- `AndroidManifest.xml`: add
  `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`.
- Create `data/GeofenceEntities.kt`:
  ```kotlin
  @Entity(tableName = "geofence_profiles")
  data class GeofenceProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double, val longitude: Double, val radiusMeters: Float,
    val onEnterGroupIds: String,   // delimited BlockGroup ids to enable on ENTER
    val onExitGroupIds: String,    // groups to enable on EXIT (or clear)
    val enabled: Boolean = true
  )
  ```
  Add to `AppDatabase` (bump version + migration, sequential).
- Create `data/GeofenceDao.kt` + repository.
- Create `location/GeofenceManager.kt` — register/unregister with
  `GeofencingClient`.
- Create `location/GeofenceBroadcastReceiver.kt` — handles transitions.
- Create `ui/screens/GeofenceProfilesScreen.kt` + `GeofenceEditScreen.kt`.

## GeofenceManager

- Build `Geofence` objects (id = profile id, `setCircularRegion`,
  `INITIAL_TRIGGER_ENTER|EXIT`, transition types ENTER+EXIT).
- Register via `geofencingClient.addGeofences(request, pendingIntent)` to the
  broadcast receiver. Requires background location granted.
- Re-register on boot (coordinate with `22-boot-persistence.md`).

## Receiver logic

- Parse `GeofencingEvent.fromIntent(intent)`; for each triggering geofence id, load
  the profile and enable/disable the referenced block groups
  (`BlockingRepository.setGroupsEnabled(ids, true/false)`).
- Log a timeline event for the transition.

## Permission flow (staged — required on modern Android)

1. Request FINE/COARSE (runtime).
2. Then request BACKGROUND location separately (system requires "Allow all the
   time"), with a prominent disclosure explaining geofencing use.

## UX / Screen Design

- **Profiles list:** cards (name, "r=150m", enabled toggle). FAB "New profile".
  States: Loading / Empty / Content.
- **Edit screen:** name; latitude/longitude (map picker if feasible, else numeric +
  "use current location" button); radius slider; multi-select of block groups for
  ENTER and EXIT. Save/discard; delete via `ConfirmDialog`.
- **Permission tiles** for location + background location with disclosure.
- Accessibility: all inputs labeled; slider announces value. Dark mode: theme-driven.

## Acceptance criteria

- Creating a profile registers a geofence; entering the region enables the ENTER
  groups; exiting applies the EXIT behavior.
- Background location prompt is requested separately with disclosure.
- Geofences survive reboot (with 22).
- All list/edit states render in light + dark.

## Out of scope / boundaries

- No continuous location tracking or storing location history beyond transition
  timestamps. Local-only.