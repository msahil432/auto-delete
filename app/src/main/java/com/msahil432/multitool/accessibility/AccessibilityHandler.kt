package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.sentry.Sentry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface implemented by modular accessibility event processors.
 */
interface AccessibilityHandler {
  /**
   * Called when an accessibility event is intercepted by [MultiToolAccessibilityService].
   *
   * @param svc The hosting accessibility service instance.
   * @param e The intercepted accessibility event.
   */
  fun onEvent(svc: AccessibilityService, e: AccessibilityEvent)
}

/**
 * Thread-safe dispatcher routing accessibility events to registered [AccessibilityHandler] instances.
 */
object Dispatcher {
  val handlers = CopyOnWriteArrayList<AccessibilityHandler>()

  /** Registers a new handler to receive events if not already present. */
  fun register(handler: AccessibilityHandler) {
    if (!handlers.contains(handler)) {
      handlers.add(handler)
    }
  }

  /** Unregisters an existing handler. */
  fun unregister(handler: AccessibilityHandler) {
    handlers.remove(handler)
  }

  /** Dispatches an intercepted event to all registered handlers with error isolation. */
  fun dispatch(svc: AccessibilityService, event: AccessibilityEvent) {
    for (handler in handlers) {
      try {
        handler.onEvent(svc, event)
      } catch (e: Exception) {
        // Prevent any handler failure from interrupting event dispatch
        Log.e("Dispatcher", "Handler ${handler::class.simpleName} threw exception in onEvent", e)
        Sentry.captureException(e)
      }
    }
  }
}

