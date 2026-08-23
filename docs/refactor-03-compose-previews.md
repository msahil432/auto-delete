# Refactoring Plan: Add Missing Compose Previews (Light + Dark)

**Guideline violated:** §3 UI / Material 3 / Dark Mode — "Always test both light and dark modes. Add `@Preview` with `uiMode = UI_MODE_NIGHT_YES`."

## Problem

Several screens have no `@Preview` composables at all, and others have light previews but are missing the dark variant.

## Screens with NO previews

These screens need both Light and Dark `@Preview` functions added:

| File | Size | Complexity |
|---|---|---|
| `ui/screens/OnboardingScreen.kt` | 68KB | High — extract a previewable content composable |
| `ui/screens/FolderDetailScreen.kt` | 60KB | High — extract inner content composable |
| `ui/screens/AppSettingsScreen.kt` | 21KB | Medium |
| `ui/screens/FilesHomeScreen.kt` | 10KB | Medium |
| `ui/screens/SettingsScreen.kt` | 6KB | Low |
| `ui/screens/ActivityLogScreen.kt` | 5KB | Low |
| `ui/screens/challenge/ChallengeHost.kt` | 3KB | Low |
| `ui/screens/challenge/TextMatchChallenge.kt` | 8KB | Medium |
| `ui/screens/challenge/PinChallenge.kt` | 9KB | Medium |
| `ui/screens/challenge/QrChallenge.kt` | 15KB | Medium |
| `ui/screens/challenge/CooldownChallenge.kt` | 8KB | Medium |

## Screens with previews but MISSING dark variant

These already have light `@Preview` but need a matching dark one:

| File | Existing previews |
|---|---|
| `ui/screens/StrictModeScreen.kt` | "Strict Mode Inactive", "Strict Mode Active" — no dark |
| `ui/screens/BrowsingHistoryScreen.kt` | "BrowsingHistoryScreen Preview" — no dark |

## Component missing previews

| File | Notes |
|---|---|
| `ui/components/ModuleActivationCard.kt` | No `@Preview` at all (9KB, complex component) |

## Preview Template

```kotlin
@Preview(showBackground = true, name = "ScreenName Light")
@Composable
private fun ScreenNamePreviewLight() {
    MultiToolTheme {
        ScreenNameContent(/* sample data */)
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "ScreenName Dark"
)
@Composable
private fun ScreenNamePreviewDark() {
    MultiToolTheme {
        ScreenNameContent(/* sample data */)
    }
}
```

## Notes

- Large screens (OnboardingScreen, FolderDetailScreen) likely need a **content extraction** refactor: split the screen into a stateful wrapper + a stateless `*Content()` composable that can accept sample data in previews.
- Follow existing patterns — e.g., `UsageHomeScreen` has `UsageHomeScreenContent` composable.
- All previews must wrap content in `MultiToolTheme { }`.

## Verification

Run `./gradlew testDebugUnitTest` — previews don't affect runtime but validate compilation.
