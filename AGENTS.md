# Multi Tool — Agent Coding Guidelines

Package: `com.msahil432.multitool` | minSdk 35 | targetSdk 37 | Kotlin + Jetpack Compose + Material 3

---

## 1. Architecture

- **Single-module Gradle project** (`app`). No DI framework — repos are instantiated manually in `MainActivity`/`Application`.
- **Layers**: `data/` (Room DAOs, entities, repositories, DataStore) → `ui/screens/` (composables + ViewModels) → `service/` (foreground services, workers, receivers).
- **Repositories** wrap DAOs/DataStore. Accept an injectable `clock: () -> Long` for testability (see `UsageRepository`).
- **ViewModels** use `ViewModelProvider.Factory` pattern — no Hilt/Koin.
- **Settings** live in `SettingsRepository` via Jetpack DataStore.

## 2. Error Handling & Sentry

- **Always** wrap risky operations in try/catch and report to Sentry:
  ```kotlin
  try { /* operation */ }
  catch (e: Exception) { Sentry.captureException(e) }
  ```
- Never silently swallow exceptions — at minimum log + report.
- **Mandatory Sentry Breadcrumbs**:
  - Add breadcrumbs across all flows for state transitions, decision branches, service/worker lifecycles, and immediately prior to risky operations.
  - **Category Convention**: Use consistent prefixes/categories:
    - `accessibility` (service connection, window/node detection, tamper & short-form actions)
    - `blocking` (rule evaluation, overlay show/hide, bubble lifecycle, timer transitions)
    - `files` (observer triggers, worker execution, file moves/deletions)
    - `geofence` (geofence registration, transition events, profile activation)
    - `notification` (listener connection, intercepted/vaulted metadata, digest worker)
    - `usage` (worker sync intervals, unlock events, stats aggregation)
    - `challenge` (challenge presentation, attempt result, completion/failure)
    - `admin` (device admin status, receiver callbacks)
    - `navigation` (screen route changes, modal transitions)
    - `system` (boot receiver, battery optimization, OEM autostart)
  - **Format**: Include structured context: `"Category: Action / Event (key=value, key2=value2)"` or configure `Breadcrumb` with explicit `category`, `message`, and structured data.
  - **Ring Buffer Protection & Throttling**: Sentry maintains a default ring buffer of 100 breadcrumbs. **Never spam breadcrumbs in high-frequency loops, per-frame renders, or 1-second tickers**. Record only state transitions, timer start/stop, milestones, and terminal states.
  - **Privacy & Security Constraints (Zero-PII)**:
    - **Never** log passwords, PINs, challenge answers, or master password hashes.
    - **Never** log raw notification message contents, contact names, or chat messages.
    - **Never** log sensitive browser query parameters, auth tokens, or session credentials.
    - **Never** log precise GPS coordinates; log profile IDs and transition types instead.
- Sentry config lives in `MultiToolApp.initSentry()`. Don't re-initialize elsewhere.
- Guard Sentry calls with `BuildConfig.SENTRY_DSN.isNotBlank()` check where appropriate.
- Set `options.isDebug = BuildConfig.DEBUG` only — never enable debug logging in release.

## 3. UI / Material 3 / Dark Mode

- **All** UI uses Jetpack Compose with `MaterialTheme` from `ui/theme/`.
- Use `MaterialTheme.colorScheme.*` tokens — **never hardcode colors** in composables.
- Dynamic color (Material You) is enabled on Android 12+. Fallback schemes are in `Theme.kt`.
- **Always test both light and dark modes.** Add `@Preview` with `uiMode = UI_MODE_NIGHT_YES`.
- Use M3 components: `Surface`, `Card`, `TopAppBar`, `NavigationBar`, etc.
- Follow [M3 spacing/typography](https://m3.material.io): use `MaterialTheme.typography.*` instead of raw `TextStyle`.
- Edge-to-edge is enabled via `enableEdgeToEdge()` — respect insets with `WindowInsets` padding.
- Reusable components go in `ui/components/`. Screen-specific composables go in `ui/screens/`.

## 4. Kotlin & Coroutines

- Use `kotlinx.coroutines` — `Dispatchers.IO` for disk/network, `Dispatchers.Main` for UI.
- Services use `CoroutineScope(SupervisorJob() + Dispatchers.IO)` — cancel in `onDestroy()`.
- ViewModels use `viewModelScope`. Expose state as `StateFlow` with `SharingStarted.WhileSubscribed(5000)`.
- Prefer `Flow` from Room/DataStore; avoid `LiveData`.
- Use `suspend` functions for one-shot operations, `Flow` for reactive streams.

## 5. Data Layer

- **Room**: Entities in `*Entities.kt`, DAOs in `*Dao.kt`, repos in `*Repository.kt`.
- DB schema changes: **add a migration** or document why `fallbackToDestructiveMigration` is acceptable.
- DataStore prefs keys go in `SettingsRepository.Companion`. Follow the existing getter/setter pattern.
- Moshi with codegen (KSP) for JSON serialization — annotate with `@JsonClass(generateAdapter = true)`.
- Whenever a new data is being saved, ask the user if the new data should be part of the backup or not. Accordingly, update the backup_rules.xml file.

## 6. Documentation & Comments

- **Every public/internal function and class** must have a KDoc comment explaining *what* and *why*.
- Non-obvious logic gets inline comments. Don't comment obvious code.
- Format:
  ```kotlin
  /** Prunes usage data older than [days] to keep the DB lean. */
  suspend fun pruneOlderThanDays(days: Int = 90) { ... }
  ```
- Preserve all existing comments unrelated to your changes.

## 7. Testing

- **Unit tests**: JUnit 4 + Robolectric (`@Config(sdk = [35])`), in `app/src/test/`.
- Use `kotlinx-coroutines-test` with `runTest` and `testScheduler` for coroutine tests.
- Use `PreferenceDataStoreFactory.create` with `TemporaryFolder` for isolated DataStore tests.
- **Screenshot tests**: Roborazzi for composable screenshot regression.
- Run tests: `./gradlew testDebugUnitTest`.
- Every new feature or bugfix **must** include tests. Prefer testing via public API over implementation details.

## 8. Services & Background Work

- Use `WorkManager` (`androidx.work`) for deferrable work (see `UsageCollectorWorker`).
- Use foreground `Service` only for real-time monitoring (see `FileMonitorService`).
- Always specify `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` on Android 14+.
- Register/unregister `BroadcastReceiver`s properly — unregister in `onDestroy()`.

## 9. Accessibility

- `AccessibilityHandler` interface for all handlers in `accessibility/` package.
- Handlers are stateless event processors — keep them focused on one concern.
- Signature classes (`*Signatures.kt`) centralize package/class detection logic.
- All content descriptions must be meaningful for screen readers.

## 10. Security

- Never log sensitive data (passwords, tokens, DSNs).
- Use `PasswordSecurity` utilities for hashing — never store plaintext.
- Secrets via env vars / `local.properties` — never commit to git.

## 11. Build & CI

- Version catalog in `gradle/libs.versions.toml` — add dependencies there, not inline.
- ProGuard/R8 keep rules in `src/main/keepRules/rules.keep`.
- CI runs lint + tests on PRs (`.github/workflows/ci.yml`).
- Keep `lint.fatal` rules enabled — fix, don't suppress.

---

## Agent Behavior Rules

1. **Ask, don't assume.** If requirements are ambiguous (e.g., deletion behavior, permission scope, UI layout), ask the user before implementing.
2. **Verify before changing.** Read and understand existing code before modifying. Check for related files/tests that may need updating.
3. **Scope changes tightly.** Don't refactor unrelated code. One PR = one concern.
4. **Explain trade-offs.** When there are multiple valid approaches, list them with pros/cons.
5. **Don't introduce new dependencies** without user approval. Check the version catalog first.
6. **Don't remove existing comments or docs** unless they're factually wrong.
7. **Flag risks.** If a change could break existing behavior (e.g., DB migration, permission change), call it out explicitly.
8. **Match existing patterns.** Copy the style of adjacent code — naming conventions, file organization, test structure.
9. **Keep PRs reviewable.** If a task is large, propose splitting it into smaller steps.
