package com.msahil432.multitool.admin

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MultiToolDeviceAdminReceiver].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiToolDeviceAdminReceiverTest {

  private lateinit var context: Context
  private lateinit var receiver: MultiToolDeviceAdminReceiver

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    receiver = MultiToolDeviceAdminReceiver()
  }

  @Test
  fun `onDisableRequested returns explanatory warning message`() {
    val message = receiver.onDisableRequested(context, Intent())
    assertNotNull(message)
    assertTrue(message.toString().contains("anti-uninstall protection"))
  }

  @Test
  fun `onEnabled and onDisabled lifecycle callbacks execute safely`() {
    receiver.onEnabled(context, Intent())
    receiver.onDisabled(context, Intent())
  }
}
