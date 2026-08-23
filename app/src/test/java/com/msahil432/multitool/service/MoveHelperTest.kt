package com.msahil432.multitool.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.DeletionMode
import com.msahil432.multitool.data.FolderConfig
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MoveHelper] covering error handling, notification dispatch, and guards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MoveHelperTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `fireErrorNotification posts notification without crashing`() {
    MoveHelper.fireErrorNotification(context, "/path/to/test.txt", "Failed to move file")
  }

  @Test
  fun `performMove handles missing destination safely`() = runTest {
    val config = FolderConfig(
      id = 1L,
      path = "/storage/emulated/0/Downloads",
      displayName = "Downloads",
      isDefaultScreenshotsFolder = false,
      enabled = true,
      deletionMode = DeletionMode.TRASH,
      defaultActionOnIgnore = "KEEP",
      candidateTimePeriods = "",
      recentlyUsedPeriods = "",
      fileTypeExcludeList = null,
      fileTypeIncludeList = null,
      createdAt = 1000L,
      moveDestinationPath = null,
      moveRuleEnabled = true
    )
    MoveHelper.performMove(context, config, "/storage/emulated/0/Downloads/non_existent.txt")
  }
}
