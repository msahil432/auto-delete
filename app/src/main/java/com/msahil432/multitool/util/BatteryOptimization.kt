package com.msahil432.multitool.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    fun isIgnoring(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

    @SuppressLint("BatteryLife")
    fun requestIgnore(ctx: Context) {
        val intent = createRequestIntent(ctx)
        ctx.startActivity(intent)
    }

    @SuppressLint("BatteryLife")
    fun createRequestIntent(ctx: Context): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
