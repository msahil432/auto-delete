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
import android.os.Environment
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(Environment::class)
class ShadowTestEnvironment {
  companion object {
    @Implementation
    @JvmStatic
    fun isExternalStorageManager(): Boolean = true
  }
}

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

  @Test
  @Config(shadows = [ShadowTestEnvironment::class])
  fun `performMove copies file, preserves timestamp, deletes source and logs`() = runTest {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val op = android.app.AppOpsManager.permissionToOp(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE)
      ?: "android:manage_external_storage"
    org.robolectric.Shadows.shadowOf(appOps).setMode(
      op,
      android.os.Process.myUid(),
      context.packageName,
      android.app.AppOpsManager.MODE_ALLOWED
    )

    val sourceDir = java.io.File(context.cacheDir, "source_test").apply { mkdirs() }
    val destDir = java.io.File(context.cacheDir, "dest_test").apply { mkdirs() }

    val sourceFile = java.io.File(sourceDir, "test_photo.jpg")
    sourceFile.writeText("test photo data")
    val expectedTimestamp = 1600000000000L
    sourceFile.setLastModified(expectedTimestamp)

    val config = FolderConfig(
      id = 2L,
      path = sourceDir.absolutePath,
      displayName = "Source",
      isDefaultScreenshotsFolder = false,
      enabled = true,
      deletionMode = DeletionMode.TRASH,
      defaultActionOnIgnore = "KEEP",
      candidateTimePeriods = "",
      recentlyUsedPeriods = "",
      fileTypeExcludeList = null,
      fileTypeIncludeList = null,
      createdAt = 1000L,
      moveDestinationPath = destDir.absolutePath,
      moveRuleEnabled = true
    )

    MoveHelper.performMove(context, config, sourceFile.absolutePath)

    val destFile = java.io.File(destDir, "test_photo.jpg")
    org.junit.Assert.assertTrue("Destination file should exist", destFile.exists())
    org.junit.Assert.assertEquals("test photo data", destFile.readText())
    org.junit.Assert.assertFalse("Source file should be deleted", sourceFile.exists())

    // Clean up
    destFile.delete()
    sourceDir.deleteRecursively()
    destDir.deleteRecursively()
  }
}

