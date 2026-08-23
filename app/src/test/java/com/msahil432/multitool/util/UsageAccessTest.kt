package com.msahil432.multitool.util

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [UsageAccess] utility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageAccessTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `createSettingsIntent points to usage access settings`() {
    val intent = UsageAccess.createSettingsIntent()
    assertNotNull(intent)
    assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, intent.action)
  }

  @Test
  fun `isGranted executes without error`() {
    val granted = UsageAccess.isGranted(context)
    assertNotNull(granted)
  }
}
