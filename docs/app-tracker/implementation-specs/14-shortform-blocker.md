# 14 — Short-Form Video Blocker (Shorts / Reels)

> **Status:** ✅ Done

Prerequisites: `11-accessibility-core.md`.

## Implementation Decisions & Details

1. **Detection Table (`ShortFormSignatures`):** Isolated signatures into `accessibility/ShortFormSignatures.kt`. Includes view ID signatures (`SHORT_FORM`) as well as known activity/fragment class names (`SHORT_FORM_CLASSES`) for YouTube (`com.google.android.youtube`), Instagram (`com.instagram.android`), and Facebook (`com.facebook.katana`).
2. **Zero-Latency In-Memory State:** `ShortFormHandler` maintains in-memory `@Volatile` boolean flags updated via coroutines from `SettingsRepository`, allowing event evaluation to happen synchronously with zero latency on the accessibility thread.
3. **Debounce & Dismissal Flow:** Implemented a 1000ms cooldown window to prevent rapid event flooding when navigating away. On detection, executes `GLOBAL_ACTION_BACK`, logs `BlockInterception` and `BLOCK_INTERCEPT` timeline event asynchronously, and re-checks the node hierarchy after 500ms, falling back to home navigation if still trapped in the feed.
4. **Settings UI:** Added a dedicated "Short-Form Video Blocker" section in `AppSettingsScreen` with toggle switches for YouTube Shorts, Instagram Reels, and Facebook Reels, captioned with "Main feed and search stay available." and labeled accessibility semantics.
5. **Lifecycle Management:** `MultiToolAccessibilityService` registers `ShortFormHandler` into `Dispatcher` in `onServiceConnected()` and unregisters it in `onDestroy()`.


## Goal

Detect and dismiss short-form video feeds (YouTube Shorts, Instagram/Facebook
Reels) while leaving the rest of those apps usable. Toggleable per app.

## Approach

Register an `AccessibilityHandler` (see spec 11 dispatcher) that inspects
`TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED` events for the target
apps and matches known short-form UI signatures (view-id resource names / class
names). When matched, navigate away (perform `GLOBAL_ACTION_BACK`, or route home if
back doesn't exit the feed).

## Files to create / modify

- Create `accessibility/ShortFormHandler.kt` implementing `AccessibilityHandler`.
- Create `accessibility/ShortFormSignatures.kt` — the detection table (kept separate
  so signatures can be updated without touching logic).
- DataStore flags in `SettingsRepository`: `block_yt_shorts`, `block_ig_reels`,
  `block_fb_reels` (Booleans, default false).
- Register the handler in the dispatcher on service connect.

## Signatures (starting set — verify on-device, apps change often)

```kotlin
// package -> list of node signatures indicating a short-form feed
val SHORT_FORM = mapOf(
  "com.google.android.youtube" to listOf(
     "reel_recycler",              // Shorts feed recycler view id
     "reel_player_page_container"),
  "com.instagram.android" to listOf(
     "clips_viewer_view_pager",    // Reels viewer
     "clips_tab"),
  "com.facebook.katana" to listOf(
     "reels_viewer", "video_home_reels"),
)
```

Matching:
- Use `AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId("<pkg>:id/<sig>")`
  against `rootInActiveWindow`, or check `event.className` for known Shorts/Reels
  activity/fragment classes.
- Only act when the current package is in `SHORT_FORM` AND its toggle is enabled.

## Action on detection

1. Try `performGlobalAction(GLOBAL_ACTION_BACK)`.
2. If still in the feed after a short delay (re-check signature), route to home
   (`ACTION_MAIN`/`CATEGORY_HOME`) or optionally show the block overlay (spec 12)
   with reason "Short-form video is blocked".
3. Log a `BlockInterception` with a synthetic ruleType (reuse `SCHEDULE` or add a
   note) OR just a timeline `BLOCK_INTERCEPT` event.

## UX

- Settings entries (in Blocking tab or Settings): per-app toggles "Block YouTube
  Shorts", "Block Instagram Reels", "Block Facebook Reels" using `SettingRow` +
  `Switch`. Include a caption: "Main feed and search stay available."
- No overlay UX of its own unless step 2 uses the block overlay.
- Accessibility: switches labeled; explain behavior in subtitle.

## Acceptance criteria

- With a toggle on, opening the corresponding short-form feed navigates the user out
  of it within ~1 second.
- Main app (home feed, search, long-form video) remains fully usable.
- With the toggle off, no interference.

## Out of scope / boundaries

- Cannot modify third-party app internals; detection is best-effort via public
  accessibility APIs and may need signature updates when apps change.
- Never read message/password content.