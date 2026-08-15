package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CopyOnWriteArrayList

interface AccessibilityHandler {
  fun onEvent(svc: AccessibilityService, e: AccessibilityEvent)
}

object Dispatcher {
  val handlers = CopyOnWriteArrayList<AccessibilityHandler>()

  fun register(handler: AccessibilityHandler) {
    if (!handlers.contains(handler)) {
      handlers.add(handler)
    }
  }

  fun unregister(handler: AccessibilityHandler) {
    handlers.remove(handler)
  }

  fun dispatch(svc: AccessibilityService, event: AccessibilityEvent) {
    for (handler in handlers) {
      try {
        handler.onEvent(svc, event)
      } catch (_: Exception) {
        // Prevent any handler failure from interrupting event dispatch
      }
    }
  }
}
