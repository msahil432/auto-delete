# 23 — Play Store Compliance & Disclosures

> **Status:** ✅ Complete

Prerequisites: `11-accessibility-core.md`, `18-device-admin.md`. This is a
review/checklist spec plus a few concrete artifacts; it produces no major feature
code.

## Goal

Make the app's sensitive-permission usage compliant with Google Play policy through
prominent disclosures, correct manifest declarations, and documentation.

## Deliverables

### 1. Prominent disclosure dialogs (verify each exists)
Before sending the user to the relevant system screen, a disclosure dialog must
explain purpose + that data stays on-device. Confirm these are implemented:
- **Accessibility** (spec 11): "used to detect the current app to enforce focus
  blocks and short-form filters; does not collect screen contents or send data off
  the device." — *Verified in `OnboardingScreen.kt` and `accessibility_service_config.xml`.*
- **Usage access** (spec 05): screen-time/launch measurement, on-device only. — *Implemented in `OnboardingScreen.kt` (`PermissionStep` & `PermissionCheckScreen`) and `UsageHomeScreen.kt`.*
- **Device admin** (spec 18): "solely to prevent uninstalling during an active
  strict-mode session." — *Verified in `AppSettingsScreen.kt` and `device_admin.xml`.*
- **Notification access** (spec 16): silencing/batching restricted-app notifications. — *Implemented in `OnboardingScreen.kt` and verified in `AppSettingsScreen.kt`.*
- **Background location** (spec 17): geofenced focus profiles. — *Verified in `GeofenceProfilesScreen.kt`.*
- **All Files Access**: file monitoring and scheduled cleanup on-device only. — *Implemented in `OnboardingScreen.kt`.*

### 2. Manifest audit
Confirm each declared permission is actually used and justified:
`PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE`, `SYSTEM_ALERT_WINDOW`,
`BIND_DEVICE_ADMIN`, `BIND_NOTIFICATION_LISTENER_SERVICE`,
`ACCESS_FINE/BACKGROUND_LOCATION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE(+type)`, `CAMERA`,
`MANAGE_EXTERNAL_STORAGE` (files feature). Remove any unused permission.
*Audit Result: All declared permissions are actively used and justified for offline digital-wellbeing and file management.*

### 3. Accessibility service description
`res/xml/accessibility_service_config.xml` `android:description` and the settings
string must clearly and honestly describe the digital-wellbeing purpose.
*Verified: `@string/accessibility_service_desc` clearly describes app detection, focus quotas, and on-device privacy.*

### 4. Data safety documentation
Create `docs/PRIVACY.md` stating: all data stored locally, no collection/sharing, no
third-party analytics, data deleted on uninstall. Mirrors `app-tracker.md` §7.
*Implemented: `docs/PRIVACY.md` created with full Google Play Data Safety breakdown and permission justifications.*

### 5. Foreground service types
Ensure each FGS declares an appropriate `foregroundServiceType` and, for
`specialUse`, a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with an accurate description.
*Verified & Implemented: `FileMonitorService` declares `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` and safe API version runtime branching.*

## Acceptance criteria

- [x] Every sensitive access has a prominent disclosure shown before the system prompt.
- [x] No unused permissions remain in the manifest.
- [x] Accessibility description is accurate and policy-compliant.
- [x] `docs/PRIVACY.md` exists and matches the local-only architecture.
- [x] All foreground services declare a valid type/subtype.

## Notes & Design Decisions

- In `OnboardingScreen.kt` and `UsageHomeScreen.kt`, modal disclosure dialogs (`ConfirmDialog`) are triggered prior to redirecting users to system settings for `Usage Access`, `All Files Access`, `Notification Access`, and `Accessibility Service`.
- `device_admin.xml` explicitly declares `<uses-policies />` without invasive policies, ensuring compliance with single-purpose anti-uninstall protection.
- `FileMonitorService.kt` performs runtime OS checks before supplying `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to support Android 14+ gracefully while preserving backward compatibility.
- Apps using `AccessibilityService` for non-accessibility purposes and `MANAGE_EXTERNAL_STORAGE` face heightened Play review; the disclosures and this checklist reduce rejection risk but do not guarantee approval.
- This spec does not add features; it verifies and documents.