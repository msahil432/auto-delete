package com.msahil432.multitool.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FileActionWorker] scheduling logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileActionWorkerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    try {
      val config = androidx.work.Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .build()
      WorkManager.initialize(context, config)
    } catch (_: Exception) {
      // WorkManager already initialized
    }
  }

  @Test
  fun `schedule enqueues unique work with correct name`() {
    val filePath = "/storage/emulated/0/Download/test_file.pdf"
    val folderId = 123L
    val delayMillis = 30_000L

    FileActionWorker.schedule(context, folderId, filePath, delayMillis)

    val workInfos = WorkManager.getInstance(context)
      .getWorkInfosForUniqueWork("Action_${filePath.hashCode()}")
      .get()

    assertNotNull(workInfos)
    assertTrue(workInfos.isNotEmpty())
  }
}
