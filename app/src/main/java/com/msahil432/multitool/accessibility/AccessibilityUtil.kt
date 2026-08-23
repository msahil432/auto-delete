package com.msahil432.multitool.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

/**
 * Utility object for checking accessibility service enablement and opening system accessibility settings.
 */
object AccessibilityUtil {
  private const val TAG = "AccessibilityUtil"

  /** Returns true if [MultiToolAccessibilityService] is enabled in Android accessibility settings. */
  fun isEnabled(context: Context): Boolean {
    val expectedServiceName = "${context.packageName}/${MultiToolAccessibilityService::class.java.name}"
    val expectedShortName = "${context.packageName}/.accessibility.MultiToolAccessibilityService"
    val enabledServices = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
      val componentName = colonSplitter.next()
      if (componentName.equals(expectedServiceName, ignoreCase = true) ||
          componentName.equals(expectedShortName, ignoreCase = true)) {
        return true
      }
    }
    return false
  }

  /** Launches system accessibility settings to allow the user to enable the service. */
  fun openSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to launch ACTION_ACCESSIBILITY_SETTINGS, falling back to ACTION_SETTINGS", e)
      val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        context.startActivity(fallbackIntent)
      } catch (fallbackEx: Exception) {
        Log.w(TAG, "Failed to launch fallback ACTION_SETTINGS", fallbackEx)
      }
    }
  }
}
