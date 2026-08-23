package com.msahil432.multitool.accessibility

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [AccessibilityUtil] checking service enablement detection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccessibilityUtilTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `isEnabled returns false when no services configured in Settings`() {
    Settings.Secure.putString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ""
    )
    assertFalse(AccessibilityUtil.isEnabled(context))
  }

  @Test
  fun `isEnabled returns true when MultiTool accessibility service is present`() {
    val serviceName = "${context.packageName}/${MultiToolAccessibilityService::class.java.name}"
    Settings.Secure.putString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      "other.app/SomeService:$serviceName"
    )
    assertTrue(AccessibilityUtil.isEnabled(context))
  }
}
