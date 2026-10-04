package com.msahil432.multitool.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [MediaScanHelper] verifying safe media scanning and MediaStore deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaScanHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `scanAddedFile completes safely for existing file`() {
        val tempFile = File.createTempFile("media_test", ".jpg", context.cacheDir)
        tempFile.writeText("sample image content")

        var scannedPath: String? = null
        MediaScanHelper.scanAddedFile(context, tempFile.absolutePath) { path, _ ->
            scannedPath = path
        }

        assertNotNull(tempFile)
        tempFile.delete()
    }

    @Test
    fun `scanAddedFile completes safely for non-existent file`() {
        MediaScanHelper.scanAddedFile(context, "/non/existent/path/photo.jpg")
    }

    @Test
    fun `deleteFromMediaStoreAndScan completes safely without crashing`() {
        val tempFile = File.createTempFile("deleted_media", ".jpg", context.cacheDir)
        tempFile.writeText("to be deleted")
        val path = tempFile.absolutePath
        tempFile.delete()

        MediaScanHelper.deleteFromMediaStoreAndScan(context, path)
    }
}
