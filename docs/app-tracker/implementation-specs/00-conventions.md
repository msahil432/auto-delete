# 00 — Conventions & Architecture (READ FIRST)

Every other spec assumes the rules in this file. Do not repeat them elsewhere.

## App identity

- Display name: **Multi Tool**
- Package / `applicationId` / `namespace`: **`com.msahil432.multitool`**
- Source root after rename: `app/src/main/java/com/msahil432/multitool/`
- `minSdk = 36`, `targetSdk = 36`, `compileSdk = 36` — DO NOT CHANGE.

> If the rename (`01-rename-package.md`) has not run yet, the old root is
> `com/msahil432/autodelete`. Always use the new package in new files.

## Tech stack (already in the project)

- Kotlin, Jetpack Compose with **Material 3**, single `MainActivity`.
- **Room** for structured data (`AppDatabase`, `AppDao`). Version currently 5.
- **DataStore Preferences** for simple key/value settings (`SettingsRepository`).
- **WorkManager** for deferred/periodic background work.
- Coroutines + Flow for async and reactive reads.
- Foreground services for always-on monitoring.

Dependencies are declared via the version catalog `gradle/libs.versions.toml` and
referenced as `libs.xxx` in `app/build.gradle.kts`. Add new deps there, never with
hardcoded version strings.

## Privacy rule (non-negotiable)

All usage, blocking, timeline, URL, and notification data is stored **locally
only** (Room / DataStore) inside the app's private storage. No network calls, no
cloud sync, no analytics, no telemetry exfiltration. All data is destroyed on
uninstall (default Android behavior for private storage).

## Package / folder layout

```
com/msahil432/multitool/
  MultiToolApp.kt          # Application (Room + DataStore init)
  MainActivity.kt
  data/                    # Room entities, DAOs, DataStore repos, serializers
  service/                 # Foreground services, receivers, workers, overlays
  tracking/                # Usage/unlock collection logic (new)
  blocking/                # Rule evaluation + enforcement (new)
  accessibility/           # AccessibilityService + node parsing (new)
  ui/
    navigation/
    screens/
    components/            # Shared Compose components (new)
    theme/
```

## Data access pattern

- Get the DB from the Application: `(application as MultiToolApp).database.appDao()`.
- Add new entities to `AppDatabase`'s `entities` array, **bump the version by 1**,
  and add a `Migration(old, new)` object following the existing style in
  `data/AppDatabase.kt`. Register it in `MultiToolApp` via `.addMigrations(...)`.
- Never use `fallbackToDestructiveMigration()`.
- Serialize small lists as delimited strings (see `data/FilterRule.kt` for the
  established `pattern|TYPE;...` pattern) OR use Room `@TypeConverters`. Prefer
  the existing `Converters` for enums.
- Repositories expose `Flow<...>` for reactive reads and `suspend` for writes.

## Settings pattern

Add new preference keys to `data/SettingsRepository.kt` following the existing
`booleanPreferencesKey` / `stringPreferencesKey` + `Flow` + `suspend set...`
pattern.

## Foreground service pattern

Mirror `service/FileMonitorService.kt`: create a notification channel, call
`startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`, use a
`CoroutineScope(SupervisorJob() + Dispatchers.IO)`, return `START_STICKY`, and
cancel the scope in `onDestroy`. Declare the service in the manifest with the
appropriate `foregroundServiceType` and a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.

## Overlay pattern

Mirror `service/PromptHelper.kt` for any "draw over other apps" UI: build a
`ComposeView`, attach a `MyLifecycleOwner` (lifecycle + saved-state), add it via
`WindowManager` with `TYPE_APPLICATION_OVERLAY`. Always guard with
`Settings.canDrawOverlays(context)` and provide a notification fallback.

## Coding standards

- 2-space indentation (match existing files).
- No wildcard business logic in Composables; keep UI stateless where practical and
  hoist state.
- Comments only when they add non-obvious info; one line max.
- Do not add features beyond the spec you are implementing.
- Do not introduce new architectural patterns (no DI framework, no new
  serialization lib) unless a spec explicitly says so.

## Out of scope for the whole product (never implement)

Per `app-tracker.md` §4: HTTPS/TLS packet inspection, reading end-to-end encrypted
message contents, password/credential logging (OS masks
`TYPE_TEXT_VARIATION_PASSWORD`), blocking emergency calls, remote/cloud
surveillance, and circumventing Safe Mode / `adb uninstall`.
