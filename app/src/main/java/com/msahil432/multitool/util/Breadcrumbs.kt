package com.msahil432.multitool.util

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Centralized utility for recording structured, privacy-safe Sentry breadcrumbs across all flows.
 *
 * Ensures consistent category naming, structured metadata formatting, and ring buffer protection.
 */
object Breadcrumbs {

    const val CAT_ACCESSIBILITY = "accessibility"
    const val CAT_BLOCKING = "blocking"
    const val CAT_FILES = "files"
    const val CAT_GEOFENCE = "geofence"
    const val CAT_NOTIFICATION = "notification"
    const val CAT_USAGE = "usage"
    const val CAT_CHALLENGE = "challenge"
    const val CAT_ADMIN = "admin"
    const val CAT_NAVIGATION = "navigation"
    const val CAT_SYSTEM = "system"

    /**
     * Records a breadcrumb with standard category, message, level, and structured metadata.
     *
     * @param category Subsystem identifier (use [CAT_ACCESSIBILITY], [CAT_BLOCKING], etc.)
     * @param message Human-readable event description
     * @param level Severity level (defaults to [SentryLevel.INFO])
     * @param data Optional non-sensitive key-value pairs providing diagnostic state
     */
    fun record(
        category: String,
        message: String,
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any?> = emptyMap()
    ) {
        try {
            val breadcrumb = Breadcrumb().apply {
                this.category = category
                this.message = message
                this.level = level
                data.forEach { (key, value) ->
                    if (value != null) {
                        setData(key, value.toString())
                    }
                }
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) {
            // Failsafe: Breadcrumb recording must never crash caller
        }
    }
}
