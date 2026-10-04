package com.msahil432.multitool.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.msahil432.multitool.util.Breadcrumbs

/**
 * Helper object for querying, activating, and deactivating Device Administrator privileges (anti-uninstall protection).
 */
object DeviceAdminHelper {

    /** Returns the [ComponentName] of [MultiToolDeviceAdminReceiver]. */
    fun component(ctx: Context): ComponentName =
        ComponentName(ctx, MultiToolDeviceAdminReceiver::class.java)

    /** Returns true if MultiTool is currently active as a device administrator. */
    fun isActive(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isAdminActive(component(ctx)) == true
    }

    /** Prompts the user to activate Device Administrator privileges. */
    fun requestActivation(ctx: Context) {
        Breadcrumbs.record(Breadcrumbs.CAT_ADMIN, "Prompting user for Device Admin activation")
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(ctx))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enables Multi Tool to prevent uninstalling during an active focus session."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /** Deactivates Device Administrator privileges when focus sessions or strict mode end. */
    fun deactivate(ctx: Context) {
        Breadcrumbs.record(Breadcrumbs.CAT_ADMIN, "Deactivating Device Admin privileges")
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm?.removeActiveAdmin(component(ctx))
    }
}

