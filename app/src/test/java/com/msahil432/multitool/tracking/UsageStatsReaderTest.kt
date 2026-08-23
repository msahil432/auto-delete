package com.msahil432.multitool.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [UsageStatsReader].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageStatsReaderTest {

  private lateinit var context: Context
  private lateinit var reader: UsageStatsReader

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    reader = UsageStatsReader(context)
  }

  @Test
  fun `queryEvents returns list of events without throwing`() {
    val events = reader.queryEvents(1000L, 2000L)
    assertNotNull(events)
  }
}
