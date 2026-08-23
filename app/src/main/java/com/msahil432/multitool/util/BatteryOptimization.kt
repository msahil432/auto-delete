package com.msahil432.multitool.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Utility object for querying and requesting exemption from Android battery optimizations / Doze mode.
 */
object BatteryOptimization {
    /** Returns true if the app is currently whitelisted from battery optimizations. */
    fun isIgnoring(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

    /** Requests system battery optimization exemption by launching the system dialog. */
    @SuppressLint("BatteryLife")
    fun requestIgnore(ctx: Context) {
        val intent = createRequestIntent(ctx)
        ctx.startActivity(intent)
    }

    /** Creates the [Intent] for requesting battery optimization exemption. */
    @SuppressLint("BatteryLife")
    fun createRequestIntent(ctx: Context): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

