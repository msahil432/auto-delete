package com.msahil432.multitool.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import io.sentry.Sentry

/**
 * Utility for synchronizing local filesystem operations with Android's [MediaStore] index.
 *
 * Ensures newly created or moved media files are promptly indexed and visible in gallery apps,
 * and deleted files have their stale [MediaStore] entries and thumbnails purged.
 */
object MediaScanHelper {

    private const val TAG = "MediaScanHelper"

    /**
     * Scans an added or updated file at [filePath] so Android's [MediaStore] indexes it immediately.
     *
     * @param context Application context
     * @param filePath Absolute filesystem path to the newly added file
     * @param onScanned Optional callback invoked once the media scanner finishes indexing
     */
    fun scanAddedFile(
        context: Context,
        filePath: String,
        onScanned: ((path: String, uri: Uri?) -> Unit)? = null
    ) {
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_FILES,
            message = "Media scan requested for added file"
        )
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(filePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Media scan completed for $path -> $uri")
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_FILES,
                    message = "Media scan completed for added file"
                )
                onScanned?.invoke(path, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan added file: $filePath", e)
            Sentry.captureException(e)
        }
    }

    /**
     * Purges the [MediaStore] entry for [filePath] and triggers a media scan to remove stale
     * ghost records or broken thumbnails from gallery applications.
     *
     * @param context Application context
     * @param filePath Absolute filesystem path of the deleted file
     */
    fun deleteFromMediaStoreAndScan(context: Context, filePath: String) {
        val appContext = context.applicationContext

        // 1. Direct ContentResolver deletion to immediately remove the row from MediaStore
        try {
            val contentUri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            val deletedCount = appContext.contentResolver.delete(contentUri, selection, selectionArgs)
            Log.d(TAG, "Deleted $deletedCount MediaStore row(s) for $filePath")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to directly delete MediaStore entry for $filePath", e)
            Sentry.captureException(e)
        }

        // 2. Scan file path to notify the media scanner to reconcile its index
        try {
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(filePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Media scan reconciled deleted file $path -> $uri")
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_FILES,
                    message = "Media scan reconciled deleted file"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan deleted file path: $filePath", e)
            Sentry.captureException(e)
        }
    }
}
