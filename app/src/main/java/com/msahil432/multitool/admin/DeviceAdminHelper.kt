package com.msahil432.multitool.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object DeviceAdminHelper {

    fun component(ctx: Context): ComponentName =
        ComponentName(ctx, MultiToolDeviceAdminReceiver::class.java)

    fun isActive(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isAdminActive(component(ctx)) == true
    }

    fun requestActivation(ctx: Context) {
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

    fun deactivate(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm?.removeActiveAdmin(component(ctx))
    }
}
