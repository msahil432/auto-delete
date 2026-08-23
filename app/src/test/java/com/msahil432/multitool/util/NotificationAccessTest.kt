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
 * Unit tests for [NotificationAccess] utility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationAccessTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `createSettingsIntent points to notification listener settings`() {
    val intent = NotificationAccess.createSettingsIntent()
    assertNotNull(intent)
    assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intent.action)
  }

  @Test
  fun `isGranted executes without error`() {
    val granted = NotificationAccess.isGranted(context)
    assertNotNull(granted)
  }
}
