# 02 — Navigation Hub (Bottom Nav)

> **Status:** 🔲 Not Started

Prerequisites: `01-rename-package.md`, `25-design-system.md`.

## Goal

Restructure the single-flow NavHost into a bottom-navigation hub so the app can host
multiple tools: **Files** (existing auto-delete feature), **Usage**, **Blocking**,
**Settings**.

## Current state

`ui/navigation/AppNavigation.kt` has a flat `NavHost` with routes: `onboarding`,
`settings`, `folder/{id}`, `activity_log`, `permissions`. The existing "Settings"
screen is effectively the app home for the files feature.

## Files to create / modify

- Modify `ui/navigation/AppNavigation.kt`.
- Create `ui/navigation/BottomNavScaffold.kt` — a `Scaffold` with a
  `NavigationBar` hosting the four top-level destinations.
- Create placeholder screens if targets don't exist yet:
  - `ui/screens/UsageHomeScreen.kt` (real content from `08-usage-ui.md`)
  - `ui/screens/BlockingHomeScreen.kt` (real content from `10-blocking-rules-ui.md`)
  Use `EmptyState` from the design system as a placeholder until those specs land.
- The existing files feature: rename the current `SettingsScreen` usage as the
  **Files** tab (folder configs list) and move app-wide settings (deletion mode,
  theme, permissions entry) into the **Settings** tab. Keep `folder/{id}`,
  `activity_log`, `permissions` as pushed sub-routes.

## Navigation structure

Top-level (bottom nav) destinations — each is a nav graph route:
- `files` — folder config list + entry to `folder/{id}`, `activity_log`.
- `usage` — usage dashboard.
- `blocking` — blocking rules list + entry to rule editor.
- `settings` — app settings + `permissions`.

Keep `onboarding` OUTSIDE the bottom nav: if onboarding not complete, show it
full-screen; once complete, show the bottom-nav scaffold. Preserve the existing
`settingsRepository.onboardingComplete` gate.

## Data model / API

```kotlin
enum class TopLevelDest(val route: String, val label: String, val icon: ImageVector) {
  FILES("files", "Files", Icons.Default.Folder),
  USAGE("usage", "Usage", Icons.Default.QueryStats),
  BLOCKING("blocking", "Blocking", Icons.Default.Block),
  SETTINGS("settings", "Settings", Icons.Default.Settings),
}
```

Use a nested `NavHost` inside the scaffold for tab content, or a single NavHost with
`bottomBar` that highlights the current destination via
`navController.currentBackStackEntryAsState()`.

## UX / Screen Design

- **Layout:** `Scaffold { bottomBar = NavigationBar }`. Content fills above the bar.
  Each tab has its own top app bar with the tab title (`headlineSmall`).
- **Bottom bar:** 4 `NavigationBarItem`s, icon + label, selected state tinted with
  `primary`. Preserve selected tab state across config changes.
- **States:** each tab manages its own loading/empty/error via design-system
  components. Placeholder tabs show `EmptyState("Coming soon", ...)`.
- **Navigation:** tapping a tab pops to that tab's start destination; re-tapping the
  active tab scrolls to top (nice-to-have). Sub-screens push with a back arrow.
- **Accessibility:** each `NavigationBarItem` has a `contentDescription`; selected
  state announced by TalkBack.
- **Dark mode:** inherited from theme; verify bar contrast in both previews.

## Step-by-step

1. Create `TopLevelDest` and `BottomNavScaffold`.
2. Split current `SettingsScreen` content into Files tab (folder list) and Settings
   tab (app settings + permissions link).
3. Add Usage and Blocking placeholder screens.
4. Wire onboarding gate to show scaffold only after completion.
5. Preserve existing sub-routes and their args.

## Acceptance criteria

- App launches into onboarding (first run) or the bottom-nav hub (subsequent runs).
- All four tabs are reachable; existing folder detail, activity log, and permissions
  screens still work from Files/Settings.
- No regression in the auto-delete files feature.
- Light and dark previews of the scaffold render correctly.

## Out of scope

- Real Usage/Blocking content (later specs). Placeholders are fine here.