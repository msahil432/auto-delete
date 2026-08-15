# 05 — Usage Access Permission Flow

> **Status:** 🔲 Not Started

Prerequisites: `02-navigation-hub.md`, `25-design-system.md`.

## Goal

Let the user grant the special "Usage access" (`PACKAGE_USAGE_STATS`) permission,
detect whether it is granted, and surface it in onboarding + the permissions screen.

## Background

`PACKAGE_USAGE_STATS` is a **special app access**, not a runtime permission. It
cannot be requested with `requestPermissions`. You must:
- Declare it in the manifest with `tools:ignore`.
- Send the user to `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- Check grant state via `AppOpsManager` `OPSTR_GET_USAGE_STATS`.

## Files to modify / create

- `app/src/main/AndroidManifest.xml`: add
  ```xml
  <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
      tools:ignore="ProtectedPermissions" />
  ```
- Create `util/UsageAccess.kt`:
  ```kotlin
  object UsageAccess {
    fun isGranted(context: Context): Boolean {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
      val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(), context.packageName)
      return mode == AppOpsManager.MODE_ALLOWED
    }
    fun openSettings(context: Context) {
      context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
  ```
- Extend the permission list used by `OnboardingScreen.kt` / permissions screen
  (`buildPermissionList()` and the `AppPermission` model already exist there): add a
  "Usage access" entry using `UsageAccess.isGranted` and `UsageAccess.openSettings`.

## UX / Screen Design

- **Component:** reuse `PermissionTile` (design system) / the existing onboarding
  permission row style.
- **Copy:** Title "Usage access". Subtitle: "Lets Multi Tool measure your screen
  time, app launches, and build your activity timeline. Data stays on your device."
- **Prominent disclosure:** show this explanation *before* sending the user to
  system settings (Play policy for sensitive access). A short dialog or inline text
  is fine.
- **States:** granted → check icon + success tint; not granted → action button
  "Grant". On return from settings, re-check on `ON_RESUME` and update the tile.
- **Accessibility:** button has `contentDescription`; state announced.
- **Dark mode:** inherited.

## Step-by-step

1. Add manifest permission.
2. Create `UsageAccess.kt`.
3. Add the usage-access `AppPermission` entry (not required to complete onboarding,
   but strongly recommended — mark `isRequired = false`).
4. Re-check grant state on resume.

## Acceptance criteria

- Tapping "Grant" opens the system Usage-access screen.
- After granting and returning, the tile shows granted without an app restart.
- `UsageAccess.isGranted` returns correct values.

## Out of scope

- Actually reading usage stats — that is `06-usage-stats-collector.md`.