package com.msahil432.multitool.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.msahil432.multitool.MainActivity

/**
 * Unit tests for [SecureScreen] context helper extensions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureScreenTest {

  @Test
  fun `findActivity on application context returns null`() {
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    assertNull(appContext.findActivity())
  }

  @Test
  fun `findActivity on Activity or wrapped Context returns Activity`() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    assertEquals(activity, activity.findActivity())

    val wrappedContext = ContextWrapper(activity)
    assertEquals(activity, wrappedContext.findActivity())

    val doubleWrapped = ContextWrapper(wrappedContext)
    assertEquals(activity, doubleWrapped.findActivity())
  }
}
