package com.msahil432.multitool.util

import io.sentry.SentryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BreadcrumbsTest {

    @Test
    fun testCategoryConstants() {
        assertEquals("accessibility", Breadcrumbs.CAT_ACCESSIBILITY)
        assertEquals("blocking", Breadcrumbs.CAT_BLOCKING)
        assertEquals("files", Breadcrumbs.CAT_FILES)
        assertEquals("geofence", Breadcrumbs.CAT_GEOFENCE)
        assertEquals("notification", Breadcrumbs.CAT_NOTIFICATION)
        assertEquals("usage", Breadcrumbs.CAT_USAGE)
        assertEquals("challenge", Breadcrumbs.CAT_CHALLENGE)
        assertEquals("admin", Breadcrumbs.CAT_ADMIN)
        assertEquals("navigation", Breadcrumbs.CAT_NAVIGATION)
        assertEquals("system", Breadcrumbs.CAT_SYSTEM)
    }

    @Test
    fun testRecordBreadcrumbExecutesWithoutThrowing() {
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_BLOCKING,
            message = "App blocked for test",
            level = SentryLevel.INFO,
            data = mapOf("pkg" to "com.example.app", "ruleId" to 123L, "nullable" to null)
        )
        assertNotNull(Breadcrumbs)
    }

    @Test
    fun testRecordBreadcrumbDefaultParameters() {
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_SYSTEM,
            message = "System test event"
        )
        assertNotNull(Breadcrumbs)
    }
}
