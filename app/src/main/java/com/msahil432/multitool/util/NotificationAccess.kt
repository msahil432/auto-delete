package com.msahil432.multitool.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledListeners.contains(context.packageName)) {
            return true
        }

        // Fallback check via Secure settings string
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val pkgName = context.packageName
        return flat.split(":").any { componentNameString ->
            val cn = ComponentName.unflattenFromString(componentNameString)
            cn != null && cn.packageName == pkgName
        }
    }

    fun openSettings(context: Context) {
        val intent = createSettingsIntent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun createSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
