package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.BrowsingRepository
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.dataStore

class MultiToolAccessibilityService : AccessibilityService() {

  private var shortFormHandler: ShortFormHandler? = null
  private var browserUrlHandler: BrowserUrlHandler? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    try {
      val app = application as MultiToolApp
      val db = app.database
      val settingsRepo = SettingsRepository(applicationContext.dataStore)
      val blockingRepo = BlockingRepository(db.blockingDao())
      val usageRepo = UsageRepository(db.usageDao())
      val browsingRepo = BrowsingRepository(db.browsingDao())

      val sfHandler = ShortFormHandler(
        settingsRepository = settingsRepo,
        blockingRepository = blockingRepo,
        usageRepository = usageRepo
      )
      shortFormHandler = sfHandler
      Dispatcher.register(sfHandler)

      val bHandler = BrowserUrlHandler(
        settingsRepository = settingsRepo,
        browsingRepository = browsingRepo,
        usageRepository = usageRepo
      )
      browserUrlHandler = bHandler
      Dispatcher.register(bHandler)
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

    browserUrlHandler?.let { handler ->
      Dispatcher.unregister(handler)
    }
    browserUrlHandler = null
  }
}

