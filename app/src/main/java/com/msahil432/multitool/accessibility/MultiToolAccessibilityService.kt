package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MultiToolAccessibilityService : AccessibilityService() {
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return

    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val pkg = event.packageName?.toString()
      if (!pkg.isNullOrBlank()) {
        ForegroundAppState.update(pkg)
      }
    }

    Dispatcher.dispatch(this, event)
  }

  override fun onInterrupt() {}
}
