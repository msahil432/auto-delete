package com.msahil432.multitool.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.core.app.AppOpsManagerCompat

/**
 * Utility object for checking and requesting system Usage Access permissions.
 */
object UsageAccess {
    /** Returns true if [AppOpsManager.OPSTR_GET_USAGE_STATS] permission is granted for MultiTool. */
    fun isGranted(context: Context): Boolean {
        val mode = AppOpsManagerCompat.noteOpNoThrow(
            context,
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManagerCompat.MODE_ALLOWED
    }

    /** Launches the system Usage Access settings screen. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Creates an [Intent] to launch the system Usage Access settings screen. */
    fun createSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
}

