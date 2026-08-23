package com.msahil432.multitool.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ActionReceiver] broadcast handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionReceiverTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `onReceive with invalid or empty intent does not crash`() {
    val receiver = ActionReceiver()
    receiver.onReceive(context, Intent())

    val missingFolderIntent = Intent("com.msahil432.multitool.ACTION_KEEP").apply {
      putExtra("filePath", "/some/path/file.txt")
    }
    receiver.onReceive(context, missingFolderIntent)
  }
}
