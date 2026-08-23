# Refactoring Plan: Accessibility — Replace `contentDescription = null`

**Guideline violated:** §9 Accessibility — "All content descriptions must be meaningful for screen readers."  
**Status:** **Done ✓**

## Problem

85+ instances of `contentDescription = null` across UI screens and components. Screen readers cannot announce these icons/images, making the app unusable for visually impaired users.

## Highest-priority files (most violations)

| File | Count | Notes |
|---|---|---|
| `ui/screens/FolderDetailScreen.kt` | ~20 | Navigation, action, and status icons |
| `ui/screens/OnboardingScreen.kt` | ~15 | Step icons, action buttons, permission icons |
| `ui/screens/GeofenceEditScreen.kt` | ~3 | Map markers, action icons |
| `ui/screens/GeofenceProfilesScreen.kt` | ~2 | FAB and list icons |
| `ui/screens/StrictModeScreen.kt` | ~5 | Lock/unlock, timer icons |
| `ui/screens/NotificationVaultScreen.kt` | ~6 | Back, delete, app icons |
| `ui/screens/SettingsScreen.kt` | ~3 | Settings row icons |
| `ui/screens/BlockOverlayContent.kt` | varies | Timer and action icons |
| `ui/screens/BlockGroupEditScreen.kt` | varies | Edit and status icons |
| `ui/screens/UsageHomeScreen.kt` | ~1 | Chart icon |
| `ui/screens/challenge/TextMatchChallenge.kt` | ~1 | Status icon |
| `ui/components/AppPicker.kt` | varies | Search and app icons |

## Pattern to Apply

```kotlin
// Before
Icon(Icons.Default.Lock, contentDescription = null)

// After — use a descriptive string
Icon(Icons.Default.Lock, contentDescription = "Lock icon")

// For decorative icons that add no meaning, use semantics modifier:
Icon(
    Icons.Default.Check,
    contentDescription = null,
    modifier = Modifier.semantics { this.contentDescription = "" }
)
```

### Guidelines for choosing descriptions

1. **Action icons** (buttons, FABs): Describe the action — `"Navigate back"`, `"Delete all"`, `"Add profile"`
2. **Status icons** (lock, check, error): Describe the state — `"Locked"`, `"Completed"`, `"Error"`
3. **Purely decorative icons** (inside a labeled button): Can remain `null` IF the parent composable already has a text label. Add a comment: `// Decorative; label provides context`
4. **Use string resources** for localization when practical; direct strings are acceptable for this initial pass.

## Verification

- Run `./gradlew testDebugUnitTest`
- Ideally verify with TalkBack on a device/emulator
