# 11 — Accessibility Service Core

> **Status:** ✅ Done

Prerequisites: `09-blocking-entities.md`, `25-design-system.md`.


## Goal

Add an `AccessibilityService` that detects the current foreground app in real time
and exposes it to the blocking engine. This is the backbone for blocking (13),
short-form filtering (14), URL tracking (15), and tamper detection (21).

## Files to create / modify

- Create `accessibility/MultiToolAccessibilityService.kt`.
- Create `accessibility/ForegroundAppState.kt` — a singleton `StateFlow<String>` of
  the current foreground package.
- `res/xml/accessibility_service_config.xml` — service config.
- `AndroidManifest.xml`: declare the service with
  `BIND_ACCESSIBILITY_SERVICE` and the config meta-data.
- Add a prominent-disclosure dialog before the user enables the service.

## Manifest + config

```xml
<service android:name=".accessibility.MultiToolAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
  <intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter>
  <meta-data android:name="android.accessibilityservice"
      android:resource="@xml/accessibility_service_config"/>
</service>
```

`accessibility_service_config.xml`:
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_desc"/>
```

## Service logic

```kotlin
class MultiToolAccessibilityService : AccessibilityService() {
  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val pkg = event.packageName?.toString() ?: return
      if (pkg.isBlank()) return
      ForegroundAppState.update(pkg)   // downstream consumers react
    }
    // content/text events forwarded to registered handlers (specs 14/15/21)
    Dispatcher.dispatch(this, event)   // simple registry of AccessibilityHandler
  }
  override fun onInterrupt() {}
}
```

- `ForegroundAppState`: `MutableStateFlow<String>` + `fun update(pkg)`; expose
  read-only `StateFlow`.
- Provide a lightweight `AccessibilityHandler` interface and a `Dispatcher` registry
  so later specs (14/15/21) can plug in without editing the service each time:
  ```kotlin
  interface AccessibilityHandler { fun onEvent(svc: AccessibilityService, e: AccessibilityEvent) }
  object Dispatcher { val handlers = CopyOnWriteArrayList<AccessibilityHandler>() ... }
  ```
- Helper `AccessibilityUtil.isEnabled(context): Boolean` reading
  `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
- Helper to open `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

## UX / Prominent disclosure (Play policy — required)

Before redirecting to Accessibility settings, show a dialog (use `ConfirmDialog`)
stating clearly: "Multi Tool uses Accessibility to detect which app is open so it can
enforce your focus blocks and short-form video filters. It does not collect the
contents of your screen or send data off your device." Only on confirm, open
settings. Add the disclosure to onboarding/permissions using `PermissionTile`.

## Acceptance criteria

- Enabling the service updates `ForegroundAppState` as you switch apps (log/verify).
- Disclosure dialog appears before the system settings screen.
- Registered handlers receive events via the dispatcher.
- Service is resilient: no crash on null package / rapid events.

## Out of scope

- Actually blocking (13), Shorts/Reels logic (14), URL capture (15), tamper (21).
- Never read password fields (`TYPE_TEXT_VARIATION_PASSWORD` is OS-masked).