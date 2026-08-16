# 01 — Rename App & Package (Auto Delete → Multi Tool)

> **Status:** ✅ Done

Prerequisites: `00-conventions.md`. The app was never published, so a hard rename
is safe. Do this before adding new features so new code lands in the right package.

## Decisions Taken

- **DB file name**: renamed to `multi_tool_db`.
- **Theme rename**: performed (`Theme.MyApplication` → `Theme.MultiTool`, `MyApplicationTheme` →
  `MultiToolTheme`). The spec marked this optional but recommended; it was done for consistency.
- **Old source tree**: the original `com/msahil432/autodelete/` folder has been **left in place**
  (not deleted). Android Studio will stop compiling it once the namespace / applicationId no longer
  match, but it is safe to delete manually or via `git rm` after confirming the build succeeds.

## Goal

Rename display name "Auto Delete" → "Multi Tool" and package
`com.msahil432.autodelete` → `com.msahil432.multitool` everywhere.

## Files to modify

### Gradle / project config
- `app/build.gradle.kts`
  - `namespace = "com.msahil432.multitool"`
  - `defaultConfig { applicationId = "com.msahil432.multitool" }`
- `settings.gradle.kts`
  - `rootProject.name = "Multi Tool"`
- `metadata.json`
  - `"name": "Multi Tool"`, update `"description"` to reflect the multi-purpose app.
  - (Firebase capability is handled in `24-remove-firebase-ai.md`.)

### Source folder move
- Move `app/src/main/java/com/msahil432/autodelete/**` →
  `app/src/main/java/com/msahil432/multitool/**` (keep sub-folders).
- Do the same for `app/src/test/java/...` and `app/src/androidTest/java/...` if any
  package-scoped test files exist.

### Package declarations & imports
- In every `.kt` file, replace `package com.msahil432.autodelete` →
  `package com.msahil432.multitool` and every
  `import com.msahil432.autodelete...` → `import com.msahil432.multitool...`.
- Rename the Application class `AutoDeleteApp` → `MultiToolApp`
  (file `AutoDeleteApp.kt` → `MultiToolApp.kt`) and update:
  - the class name and all `application as AutoDeleteApp` casts,
  - `AndroidManifest.xml` `android:name=".MultiToolApp"`.

### Manifest
- `app/src/main/AndroidManifest.xml`: the `android:name=".AutoDeleteApp"` →
  `.MultiToolApp`. Service/receiver relative names (`.service.X`) stay valid after
  the folder move. Do not change permission entries here.

### Resources / strings
- `app/src/main/res/values/strings.xml`: `app_name` → `Multi Tool`.
- Notification copy in `service/FileMonitorService.kt` currently says
  "Auto Delete" / "Monitoring folders for new files" — update the file-monitor
  notification title to "Multi Tool" (keep the descriptive text about file
  monitoring for that specific service).
- Theme name `Theme.MyApplication` in `styles`/`themes.xml` and `MyApplicationTheme`
  in `ui/theme/Theme.kt` MAY be renamed to `Theme.MultiTool` / `MultiToolTheme`.
  If you rename, update all references (manifest `android:theme`, `MainActivity`,
  `PromptHelper`). Renaming is optional but recommended.

### Room database
- The DB file name was updated to `multi_tool_db`.

## Step-by-step

1. Update `build.gradle.kts`, `settings.gradle.kts`, `metadata.json`.
2. Move the source folders to the new package path.
3. Global find/replace `com.msahil432.autodelete` → `com.msahil432.multitool`
   across `.kt`, `.xml`, and gradle files.
4. Rename `AutoDeleteApp` class/file → `MultiToolApp`; fix manifest + casts.
5. Update `strings.xml` `app_name` and the file-monitor notification title.
6. (Optional) Rename theme; fix references.
7. Sync Gradle and build.

## Acceptance criteria

- Project builds with 0 references to `com.msahil432.autodelete` remaining
  (search the repo to confirm; `build/` output may be ignored/cleaned).
- App installs and launches; launcher label reads "Multi Tool".
- `FileMonitorService` still runs and its notification shows "Multi Tool".
- No runtime crash from a missing/renamed Application class.

## Out of scope

- Any new feature code. This spec is rename-only.