package com.msahil432.multitool.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Administrator receiver for anti-uninstall protection.
 * Used during active strict-mode focus sessions to prevent premature app removal.
 */
class MultiToolDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Device Admin will remove anti-uninstall protection during active focus sessions. Are you sure you want to deactivate it?"
    }
}
