package com.msahil432.multitool.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.sentry.Sentry

/**
 * Helper for detecting aggressive OEM background killers (Xiaomi, Samsung, Huawei, Oppo, Vivo)
 * and directing users to OEM-specific autostart and background battery whitelist settings.
 */
object OemAutostart {
    private const val TAG = "OemAutostart"

    /** Supported OEM brand categories with custom power managers. */
    enum class OemBrand(val displayName: String) {
        XIAOMI("Xiaomi / Redmi / POCO"),
        SAMSUNG("Samsung"),
        HUAWEI("Huawei / Honor"),
        OPPO("OPPO / Realme / OnePlus"),
        VIVO("vivo / iQOO"),
        OTHER("Other / Stock Android")
    }

    /** Detects the current device's OEM brand based on [Build.MANUFACTURER]. */
    fun detectOem(): OemBrand {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemBrand.XIAOMI
            manufacturer.contains("samsung") -> OemBrand.SAMSUNG
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemBrand.HUAWEI
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> OemBrand.OPPO
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> OemBrand.VIVO
            else -> OemBrand.OTHER
        }
    }

    /** Returns step-by-step instructions for allowing autostart on the detected or given OEM [brand]. */
    fun getInstructions(brand: OemBrand = detectOem()): String {
        return when (brand) {
            OemBrand.XIAOMI -> "Enable 'Autostart' and set Battery saver to 'No restrictions'."
            OemBrand.SAMSUNG -> "Allow 'Background activity' and ensure the app is not in 'Sleeping apps'."
            OemBrand.HUAWEI -> "Set App launch to 'Manage manually' and enable 'Auto-launch', 'Secondary launch', and 'Run in background'."
            OemBrand.OPPO -> "Enable 'Allow auto-launch' and 'Allow background activity'."
            OemBrand.VIVO -> "Enable 'Autostart' and allow 'High background power consumption'."
            OemBrand.OTHER -> "Allow background activity and disable battery optimizations in app settings."
        }
    }

    /** Returns candidate [Intent]s targeting known OEM autostart management screens. */
    fun getOemIntentCandidates(context: Context): List<Intent> {
        val brand = detectOem()
        val componentNames = when (brand) {
            OemBrand.XIAOMI -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            )
            OemBrand.SAMSUNG -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunProcessManager")
            )
            OemBrand.HUAWEI -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            )
            OemBrand.OPPO -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity")
            )
            OemBrand.VIVO -> listOf(
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")
            )
            OemBrand.OTHER -> emptyList()
        }

        return componentNames.map { comp ->
            Intent().apply {
                component = comp
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /** Attempts to open OEM-specific autostart settings, falling back to standard app details if unavailable. */
    fun open(context: Context) {
        val oem = detectOem()
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_SYSTEM,
            message = "Opening OEM autostart settings",
            data = mapOf("oem" to oem.name)
        )
        val candidates = getOemIntentCandidates(context)
        for (intent in candidates) {
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                // Try next candidate
                Log.w(TAG, "Failed to launch OEM autostart candidate: ${intent.component}", e)
            }
        }

        // Fallback to Application Details Settings
        openAppDetails(context)
    }

    /** Opens standard system application details settings for MultiTool. */
    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch ACTION_APPLICATION_DETAILS_SETTINGS, falling back to ACTION_SETTINGS", e)
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed to launch fallback ACTION_SETTINGS", fallbackEx)
                Sentry.captureException(fallbackEx)
            }
        }
    }
}

