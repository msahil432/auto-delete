package com.msahil432.multitool.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object AccessibilityUtil {
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

  fun openSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(fallbackIntent)
    }
  }
}
