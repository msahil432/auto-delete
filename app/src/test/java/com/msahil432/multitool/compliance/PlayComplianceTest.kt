package com.msahil432.multitool.compliance

import com.msahil432.multitool.ui.screens.buildPermissionList
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayComplianceTest {

    @Test
    fun testPermissionsListContainsRequiredAndOptionalItems() {
        val permissions = buildPermissionList()
        val permissionIds = permissions.map { it.id }.toSet()

        assertTrue("Must declare notifications permission", permissionIds.contains("notifications"))
        assertTrue("Must declare all_files permission", permissionIds.contains("all_files"))
        assertTrue("Must declare usage_access permission", permissionIds.contains("usage_access"))
        assertTrue("Must declare accessibility permission", permissionIds.contains("accessibility"))
        assertTrue("Must declare notification_listener permission", permissionIds.contains("notification_listener"))
        assertTrue("Must declare battery permission", permissionIds.contains("battery"))

        val required = permissions.filter { it.isRequired }.map { it.id }
        assertTrue("Notifications and all_files must be required", required.contains("notifications") && required.contains("all_files"))

        val optional = permissions.filter { !it.isRequired }.map { it.id }
        assertTrue("Usage and accessibility must be optional", optional.contains("usage_access") && optional.contains("accessibility"))
    }

    @Test
    fun testPrivacyDocumentationExistsAndMatchesPolicy() {
        val privacyFile = File("../docs/PRIVACY.md").takeIf { it.exists() }
            ?: File("docs/PRIVACY.md").takeIf { it.exists() }
            ?: File("../../docs/PRIVACY.md").takeIf { it.exists() }

        assertNotNull("PRIVACY.md must exist in docs directory", privacyFile)
        val text = privacyFile!!.readText()
        assertTrue("Must document on-device local storage", text.contains("On-Device") || text.contains("Local Storage"))
        assertTrue("Must document zero telemetry", text.contains("Zero Telemetry"))
        assertTrue("Must document purge on uninstall", text.contains("uninstall"))
        assertTrue("Must document Accessibility Service purpose", text.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertTrue("Must document Usage Stats purpose", text.contains("PACKAGE_USAGE_STATS"))
        assertTrue("Must document Device Admin purpose", text.contains("BIND_DEVICE_ADMIN"))
    }
}
