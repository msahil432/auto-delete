package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.dataStore

class MultiToolAccessibilityService : AccessibilityService() {

  private var shortFormHandler: ShortFormHandler? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    try {
      val app = application as MultiToolApp
      val db = app.database
      val settingsRepo = SettingsRepository(applicationContext.dataStore)
      val blockingRepo = BlockingRepository(db.blockingDao())
      val usageRepo = UsageRepository(db.usageDao())

      val handler = ShortFormHandler(
        settingsRepository = settingsRepo,
        blockingRepository = blockingRepo,
        usageRepository = usageRepo
      )
      shortFormHandler = handler
      Dispatcher.register(handler)
    } catch (_: Exception) {}
  }

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

  override fun onDestroy() {
    super.onDestroy()
    shortFormHandler?.let { handler ->
      Dispatcher.unregister(handler)
    }
    shortFormHandler = null
  }
}

