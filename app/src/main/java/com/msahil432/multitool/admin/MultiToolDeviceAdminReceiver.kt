package com.msahil432.multitool.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.msahil432.multitool.util.Breadcrumbs

/**
 * Device Administrator receiver for anti-uninstall protection.
 * Used during active strict-mode focus sessions to prevent premature app removal.
 */
class MultiToolDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Breadcrumbs.record(Breadcrumbs.CAT_ADMIN, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Breadcrumbs.record(Breadcrumbs.CAT_ADMIN, "Device admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Breadcrumbs.record(Breadcrumbs.CAT_ADMIN, "Device admin disable requested")
        return "Disabling Device Admin will remove anti-uninstall protection during active focus sessions. Are you sure you want to deactivate it?"
    }
}
