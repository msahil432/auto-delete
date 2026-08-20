package com.msahil432.multitool.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Signature definitions and heuristics for identifying system settings screens
 * that could be used to tamper with, disable, or force-stop Multi Tool.
 */
object TamperSignatures {

    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.miui.securitycenter",
        "com.samsung.android.settings",
        "com.coloros.safecenter",
        "com.huawei.systemmanager",
        "com.vivo.permissionmanager"
    )

    val DANGER_KEYWORDS = setOf(
        "force stop",
        "force close",
        "uninstall",
        "clear data",
        "clear storage",
        "disable",
        "deactivate",
        "remove device admin",
        "turn off",
        "off"
    )

    val PROTECTED_FRAGMENT_CLASSES = setOf(
        "installedappdetails",
        "appinfodashboardfragment",
        "appbuttonspreferencecontroller",
        "deviceadminadd",
        "deviceadminsettings",
        "accessibilitysettings",
        "toggleaccessibilityservicepreferencefragment"
    )

    fun isSettingsPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return SETTINGS_PACKAGES.contains(pkg) || pkg.endsWith(".settings")
    }

    /**
     * Inspects accessibility node tree to determine if the node hierarchy references
     * target app's package name or label, along with any danger action or protected screen.
     */
    fun containsAppReference(
        rootNode: AccessibilityNodeInfo?,
        targetPackageName: String,
        targetAppLabel: String
    ): Boolean {
        if (rootNode == null) return false

        val searchTerms = listOf(targetPackageName, targetAppLabel, "Multi Tool", "MultiTool")
        for (term in searchTerms) {
            val matching = try {
                rootNode.findAccessibilityNodeInfosByText(term)
            } catch (_: Exception) {
                emptyList()
            }
            if (!matching.isNullOrEmpty()) {
                return true
            }
        }

        return scanNodeHierarchyForText(rootNode, searchTerms)
    }

    /**
     * Checks if the node hierarchy contains danger controls (such as Force Stop, Uninstall, Deactivate).
     */
    fun containsDangerControl(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        for (keyword in DANGER_KEYWORDS) {
            val matching = try {
                rootNode.findAccessibilityNodeInfosByText(keyword)
            } catch (_: Exception) {
                emptyList()
            }
            if (!matching.isNullOrEmpty()) {
                return true
            }
        }
        return false
    }

    private fun scanNodeHierarchyForText(
        node: AccessibilityNodeInfo?,
        targets: List<String>,
        depth: Int = 0
    ): Boolean {
        if (node == null || depth > 20) return false

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName

        for (target in targets) {
            if (text?.contains(target, ignoreCase = true) == true) return true
            if (desc?.contains(target, ignoreCase = true) == true) return true
            if (viewId?.contains(target, ignoreCase = true) == true) return true
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                val found = scanNodeHierarchyForText(child, targets, depth + 1)
                if (found) return true
            }
        }

        return false
    }
}
