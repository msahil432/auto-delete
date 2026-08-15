# 25 — Design System & UX Baseline

> **Status:** 🔲 Not Started

**Read this before any spec that builds UI.** It is the single source of visual and
interaction truth. Prerequisites: `00-conventions.md`.

## Goal

Define reusable Compose components, color semantics, typography, spacing, states,
and accessibility rules so every screen looks and behaves consistently.

## Foundation (already present)

- `ui/theme/Theme.kt` → `MyApplicationTheme` (Material 3, dynamic color on 12+,
  dark/light via `isSystemInDarkTheme()`).
- `ui/theme/Color.kt` (Purple/Pink palette), `ui/theme/Type.kt` (`Typography`).
- Keep `MyApplicationTheme` as the app-wide theme wrapper. You MAY rename it to
  `MultiToolTheme` during the rename spec; UI specs should call whatever the
  current theme composable is.

## Color semantics (add as extension vals or MaterialTheme mappings)

| Meaning | Light | Dark | Usage |
|---------|-------|------|-------|
| Blocked / restricted | error container | error container | block overlay, blocked badges |
| Warning / near-limit | tertiary | tertiary | quota ≥80% used |
| Success / allowed | primary | primary | granted permission, goal met |
| Neutral surface | surfaceVariant | surfaceVariant | cards, list rows |

Never hardcode hex in screens; pull from `MaterialTheme.colorScheme`. Add new
semantic colors in `ui/theme/Color.kt` if needed and map them in the scheme.

## Spacing & sizing scale

- Base unit 4dp. Common: 4, 8, 12, 16, 24, 32.
- Screen edge padding: 16dp. Card inner padding: 16dp. List item min height: 56dp.
- **Touch targets ≥ 48dp.** Icon buttons 48dp.
- Corner radius: use `MaterialTheme.shapes` (small/medium/large).

## Typography

Use `MaterialTheme.typography`: `headlineSmall` for screen titles, `titleMedium`
for section headers, `bodyMedium` for content, `labelMedium` for metadata/captions.

## Shared components (create under `ui/components/`)

Create these once; all screens reuse them.

1. `SectionHeader(title, modifier)` — `titleMedium`, 16dp top padding.
2. `SettingRow(title, subtitle?, leadingIcon?, trailing: @Composable)` — 56dp min
   height, clickable, used across Settings/Blocking/Usage.
3. `StatCard(label, value, icon?, progress: Float?)` — for usage numbers; optional
   linear progress for quotas (color: success <80%, warning ≥80%, blocked ≥100%).
4. `PermissionTile(title, subtitle, granted: Boolean, onGrant)` — mirror the visual
   language already in `OnboardingScreen.kt`.
5. `EmptyState(icon, title, message, actionLabel?, onAction?)` — used by every list.
6. `LoadingState()` — centered `CircularProgressIndicator`.
7. `ErrorState(message, onRetry?)` — for failed loads.
8. `ConfirmDialog(title, text, confirmLabel, onConfirm, onDismiss)`.
9. `AppListItem(appLabel, packageName, icon, trailing)` — used anywhere apps are
   listed (blocking selection, usage list).

## Required states for EVERY screen

Each screen must handle and visibly render:
- **Loading** — while Room/UsageStats data resolves (`LoadingState`).
- **Empty** — no data / no rules yet (`EmptyState` with a clear CTA).
- **Error** — permission missing or read failed (`ErrorState`, link to fix).
- **Success/content** — the normal populated state.

## Navigation & layout

- App uses a **bottom navigation hub** (see `02-navigation-hub.md`): Files, Usage,
  Blocking, Settings.
- Sub-screens (rule editor, folder detail, activity log, permissions) are pushed
  onto the nav stack with a top app bar + back arrow.
- Destructive actions (delete rule, disable strict mode) require `ConfirmDialog`.

## Accessibility (mandatory)

- Every icon-only button has `contentDescription`.
- Text contrast: rely on Material 3 on-color pairs; don't place text on custom
  backgrounds without an on-color.
- Support TalkBack: meaningful semantics on cards/rows; group related content.
- Respect system font scaling (use `sp`, don't fix text sizes in `dp`).
- Support dark mode automatically via the theme — never assume a light background.

## Block overlay UX (used by 12 & 20)

- Full-screen, opaque, uses error-container background.
- Large blocked icon, app name, the reason ("Daily limit reached", schedule name),
  and either a single "Close" action or the friction-challenge entry point.
- No easy dismiss (no outside-tap close) — this is intentional friction.

## Acceptance criteria

- `ui/components/` contains the 9 components above, each with a `@Preview`.
- Components read all colors/typography from `MaterialTheme`.
- A sample screen using them renders correctly in both light and dark previews.