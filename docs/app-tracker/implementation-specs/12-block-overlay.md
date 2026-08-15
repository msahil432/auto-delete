# 12 — Block Overlay Screen

> **Status:** ✅ Done

Prerequisites: `11-accessibility-core.md`, `25-design-system.md`.

## Goal

Show a full-screen blocking overlay over a restricted app, explaining why it is
blocked and offering only the allowed exits.

## Approach

Reuse the overlay technique from `service/PromptHelper.kt` (ComposeView +
`BlockLifecycleOwner` + `WindowManager` with `TYPE_APPLICATION_OVERLAY`). Prefer an
overlay over launching an Activity so it can appear instantly on top of the target
app. Guard with `Settings.canDrawOverlays`.

## Files to create / modify

- Create `blocking/BlockOverlayManager.kt` — show/hide the overlay.
- Create `ui/screens/BlockOverlayContent.kt` — the Compose UI.
- Create `blocking/BlockActivity.kt` — fallback Activity when overlay permission is missing.
- Register `BlockActivity` in `AndroidManifest.xml`.
- Create unit tests in `app/src/test/java/com/msahil432/multitool/blocking/BlockOverlayTest.kt`.

## API

```kotlin
object BlockOverlayManager {
  data class BlockInfo(
    val packageName: String,
    val appLabel: String,
    val reason: String,             // "Daily limit reached", schedule name, etc.
    val allowFriction: Boolean = false, // if strict mode offers a challenge
    val usedSeconds: Long? = null,
    val limitSeconds: Long? = null,
    val endsAtMillis: Long? = null,
  )
  fun show(context: Context, info: BlockInfo,
           onClose: () -> Unit, onFriction: (() -> Unit)? = null)
  fun hide()
  fun isShowing(): Boolean
}
```

- Keep a reference to the added view; `hide()` removes it via `WindowManager`.
- If overlay permission is missing, fall back to launching a full-screen blocking
  Activity (`FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK`).

## Implementation Decisions & Details

1. **`BlockInfo` extensions:** added optional fields `usedSeconds`, `limitSeconds`, and `endsAtMillis` with default null values to power the quota progress bar and countdown card specified in the UX design while maintaining full backwards compatibility.
2. **Fallback Activity:** Created `BlockActivity` (`.blocking.BlockActivity`) registered in `AndroidManifest.xml` with `singleTop` launch mode and `Theme.MultiTool` theme.
3. **Accessibility:** Added live region (`LiveRegionMode.Assertive`) to announce block reasons dynamically to TalkBack users.
4. **Key interception:** Overlaid `ComposeView` intercepts hardware/system back key on `ACTION_UP` to invoke `onClose()`, navigate to home (`Intent.ACTION_MAIN` + `CATEGORY_HOME`), and dismiss the overlay.

## UX / Screen Design

- **Layout:** full-screen, opaque, background = `errorContainer`.
  - Centered large blocked icon (`Icons.Default.Block`), `contentDescription`.
  - App label (`headlineSmall`) + reason (`bodyLarge`).
  - Optional countdown if the block ends soon (e.g., schedule end time).
  - Primary action button: "Go back" → calls `onClose` and navigates home
    (`Intent.ACTION_MAIN` + `CATEGORY_HOME`).
  - If `allowFriction`, a secondary "Unlock anyway" → `onFriction` (leads to spec 20
    challenge). Otherwise hide it.
- **No easy dismiss:** ignore outside taps / back should also route home (intentional
  friction). Do not add a close "X" in the corner.
- **States:** single content state; if reason is a quota, show usage vs. limit with a
  `StatCard`-style progress.
- **Accessibility:** announce the block reason on appear (live region / focus);
  buttons labeled; large touch targets.
- **Dark mode:** error-container pairs handle both; verify contrast in previews.

## Acceptance criteria

- `show()` displays the overlay above the current app; `hide()` removes it.
- "Go back" returns the user to the home screen and hides the overlay.
- Friction button appears only when `allowFriction` is true.
- Overlay-permission-missing path falls back to the blocking Activity.
- Light + dark previews render.

## Out of scope

- Deciding *when* to block (spec 13). The friction challenge itself (spec 20).