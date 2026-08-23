package com.msahil432.multitool.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [NotificationDigestWorker] constants and channel initialization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationDigestWorkerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `channel constants are defined correctly`() {
    assertEquals("notification_vault_digest", NotificationDigestWorker.CHANNEL_ID)
    assertEquals(2002, NotificationDigestWorker.DIGEST_NOTIFICATION_ID)
    assertEquals("notification_vault_digest_scheduled", NotificationDigestWorker.WORK_NAME_SCHEDULED)
    assertEquals("notification_vault_digest_onetime", NotificationDigestWorker.WORK_NAME_ONETIME)
  }

  @Test
  fun `createNotificationChannel registers notification channel on Android O+`() {
    NotificationDigestWorker.createNotificationChannel(context)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel: NotificationChannel? = notificationManager.getNotificationChannel(NotificationDigestWorker.CHANNEL_ID)
      assertNotNull(channel)
      assertEquals(NotificationDigestWorker.CHANNEL_ID, channel?.id)
      assertEquals("Notification Vault Digest", channel?.name)
    }
  }
}
