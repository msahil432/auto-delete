# 23 — Play Store Compliance & Disclosures

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
  the device."
- **Usage access** (spec 05): screen-time/launch measurement, on-device only.
- **Device admin** (spec 18): "solely to prevent uninstalling during an active
  strict-mode session."
- **Notification access** (spec 16): silencing/batching restricted-app notifications.
- **Background location** (spec 17): geofenced focus profiles.

### 2. Manifest audit
Confirm each declared permission is actually used and justified:
`PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE`, `SYSTEM_ALERT_WINDOW`,
`BIND_DEVICE_ADMIN`, `BIND_NOTIFICATION_LISTENER_SERVICE`,
`ACCESS_FINE/BACKGROUND_LOCATION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE(+type)`, `CAMERA`,
`MANAGE_EXTERNAL_STORAGE` (files feature). Remove any unused permission.

### 3. Accessibility service description
`res/xml/accessibility_service_config.xml` `android:description` and the settings
string must clearly and honestly describe the digital-wellbeing purpose.

### 4. Data safety documentation
Create `docs/PRIVACY.md` stating: all data stored locally, no collection/sharing, no
third-party analytics, data deleted on uninstall. Mirrors `app-tracker.md` §7.

### 5. Foreground service types
Ensure each FGS declares an appropriate `foregroundServiceType` and, for
`specialUse`, a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with an accurate description.

## Acceptance criteria

- Every sensitive access has a prominent disclosure shown before the system prompt.
- No unused permissions remain in the manifest.
- Accessibility description is accurate and policy-compliant.
- `docs/PRIVACY.md` exists and matches the local-only architecture.
- All foreground services declare a valid type/subtype.

## Notes

- Apps using `AccessibilityService` for non-accessibility purposes and
  `MANAGE_EXTERNAL_STORAGE` face heightened Play review; the disclosures and this
  checklist reduce rejection risk but do not guarantee approval.
- This spec does not add features; it verifies and documents.
