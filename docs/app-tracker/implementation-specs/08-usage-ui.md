# 08 — Usage Dashboard UI

> **Status:** ✅ Complete

Prerequisites: `04-usage-repository.md`, `06-usage-stats-collector.md`,
`07-unlock-tracker.md`, `25-design-system.md`.

## Goal

Build the **Usage** tab: total screen time, per-app breakdown, launch counts,
unlock frequency, and a chronological timeline — all read from `UsageRepository`.

## Files to create / modify

- Replace the placeholder `ui/screens/UsageHomeScreen.kt`.
- Create `ui/screens/UsageViewModel.kt` (or use a state holder; the app doesn't use
  Hilt — construct with the repository like other screens pass `appDao`).
- Create `ui/screens/UsageTimelineScreen.kt` (pushed sub-route from the Usage tab).
- Add helper `util/DurationFormat.kt` → `fun Long.toHms(): String` ("2h 15m").

## Data / API

`UsageViewModel` exposes Compose state from repository Flows:
- `totalScreenTimeToday: State<Long>`
- `perApp: State<List<UsageDailyStat>>` (sorted desc by foregroundMillis)
- `unlocksToday: State<Int>`
- `timeline: State<List<TimelineEvent>>`
Resolve app labels + icons from `PackageManager` (cache in a `Map<String, AppMeta>`;
do this off the main thread).

## UX / Screen Design

- **Layout (Usage tab):** vertical scroll.
  1. Header row of `StatCard`s: Total screen time, Unlocks today, Total launches.
  2. `SectionHeader("App usage today")`.
  3. List of `AppListItem`s: icon, app label, `foregroundMillis.toHms()`, launch
     count as a trailing caption; optional linear progress vs. that app's block
     quota if one exists.
  4. A "View timeline" button → `UsageTimelineScreen`.
- **Timeline screen:** reverse-chronological list grouped by hour; each row shows
  time, app label, event type icon (foreground / unlock / block-intercept), and
  duration when present.
- **States:**
  - Loading → `LoadingState`.
  - Permission missing (`!UsageAccess.isGranted`) → `ErrorState` with message
    "Usage access needed to show your stats" and a button that opens the permission
    flow (spec 05).
  - Empty (granted but no data yet) → `EmptyState("No usage yet",
    "Come back after using your phone for a bit.")`.
  - Content → the layout above.
- **Accessibility:** each stat card announces "label, value"; list rows expose app
  name + duration as a single semantics node; icons have descriptions.
- **Dark mode:** all colors from `MaterialTheme`; verify both previews.
- **Sorting/formatting:** durations via `toHms()`; numbers with locale grouping.

## Step-by-step

1. Build `UsageViewModel` reading repository Flows.
2. Implement app label/icon resolution with caching.
3. Build `UsageHomeScreen` with the four states.
4. Build `UsageTimelineScreen`.
5. Wire the "Usage" bottom-nav tab (spec 02) to `UsageHomeScreen` and the timeline
   sub-route.

## Acceptance criteria

- With permission granted and data present, totals and per-app list match stored
  data.
- Missing permission shows the error state with a working grant action.
- Empty and loading states render.
- Timeline shows events newest-first with correct icons/durations.
- Light + dark previews render.

## Out of scope

- Blocking UI (spec 10). Modifying how data is collected (06/07).

## Implementation Decisions

- Created `util/DurationFormat.kt` with `fun Long.toHms(): String` formatting durations cleanly (hours, minutes, seconds) with "0m" fallback for <= 0ms.
- Created `ui/screens/UsageViewModel.kt` reading Flows from `UsageRepository` for `totalScreenTimeToday`, `perApp` (sorted descending by foreground time), `unlocksToday`, and `timelineToday`.
- App icon and label resolution done off the main thread with coroutines and cached in `Map<String, AppMeta>`.
- `UsageHomeScreen.kt` implements all 4 states (Missing permission error state with direct settings grant button, Loading state, Empty state, and populated content layout with StatCards, per-app breakdown, and "View Timeline" button).
- Added `LifecycleEventObserver` listening for `Lifecycle.Event.ON_RESUME` on `UsageHomeScreen` to re-check usage stats permission automatically when the user returns from system settings.
- Created `ui/screens/UsageTimelineScreen.kt` featuring a reverse-chronological event timeline grouped by hour with distinct visual icons for app foreground, background, unlock, and block intercept events.
- Added optional `actionLabel: String = "Retry"` parameter to `ErrorState` component to support custom action button labels like "Grant Permission".
- Wired the Usage tab and the `usage_timeline` pushed sub-route in `BottomNavScaffold.kt`, `AppNavigation.kt`, and `MainActivity.kt`.
- Added unit test suites `DurationFormatTest.kt` and `UsageViewModelTest.kt`.