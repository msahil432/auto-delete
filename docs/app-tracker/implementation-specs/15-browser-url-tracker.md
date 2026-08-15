# 15 — Browser URL & Search Query Tracker

Prerequisites: `04-usage-repository.md`, `11-accessibility-core.md`.

## Goal

Capture domain/URL from browser address bars and search-engine queries via
accessibility node parsing, storing them locally for the activity timeline.

## Boundaries (must respect)

- Never read fields flagged `TYPE_TEXT_VARIATION_PASSWORD` — the OS masks these;
  skip any node with `isPassword == true`.
- Do not attempt HTTPS/network inspection. This is UI-text reading only.
- Local-only storage; no exfiltration.

## Files to create / modify

- Create `data/BrowsingEntities.kt`:
  ```kotlin
  @Entity(tableName = "browsing_events", indices = [Index("timestamp")])
  data class BrowsingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,      // browser package
    val kind: BrowsingKind,       // URL or SEARCH_QUERY
    val value: String             // domain/url or query text
  )
  enum class BrowsingKind { URL, SEARCH_QUERY }
  ```
  Add to `AppDatabase` (bump version + migration, sequential).
- Create `data/BrowsingDao.kt` + add read/insert methods (`insert`, `recentSince`).
- Create `accessibility/BrowserUrlHandler.kt` implementing `AccessibilityHandler`.
- Create `accessibility/BrowserSignatures.kt` — address-bar view-id map.
- DataStore flag `track_browser_urls` (default false; opt-in).

## Address-bar signatures (verify on-device)

```kotlin
val URL_BAR = mapOf(
  "com.android.chrome" to "com.android.chrome:id/url_bar",
  "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
  "com.brave.browser" to "com.brave.browser:id/url_bar",
)
```

## Handler logic

- On `TYPE_WINDOW_CONTENT_CHANGED` / `TYPE_VIEW_TEXT_CHANGED` for a known browser
  package (and only if `track_browser_urls` is on):
  - Find the address-bar node by view id; read its text.
  - Skip if `node.isPassword`.
  - Normalize: if it looks like a URL, extract the domain; if it's a search box on a
    search-engine page, store as `SEARCH_QUERY`.
  - Debounce: only store when the value stabilizes (e.g., unchanged for 800ms) to
    avoid logging every keystroke.
  - `usageRepo`/`browsingDao` insert a `BrowsingEvent`; also add a timeline entry if
    desired.

## UX

- Settings toggle "Track browser URLs & searches" (`SettingRow` + `Switch`) with a
  clear caption: "Stored only on this device. Passwords are never recorded."
- A read-only list screen (optional) showing recent browsing events, reachable from
  Usage timeline or Settings; use `AppListItem`/list rows, with Empty/Loading states.
- Accessibility: toggle labeled; list rows expose value + time.

## Acceptance criteria

- With the toggle on, visiting a site in a supported browser records a `URL` event
  with the domain; a search records a `SEARCH_QUERY`.
- Password fields are never captured.
- Rapid typing produces one debounced entry, not many.
- With the toggle off, nothing is recorded.

## Out of scope / boundaries

- Unsupported browsers may not expose the address bar id; document as best-effort.
- No message content, no credentials, no network payloads.
