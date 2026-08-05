package com.msahil432.autodelete.data

// ─── Data class ───────────────────────────────────────────────────────────────

data class TimePeriodPreset(
    val label: String,  // human-readable, e.g. "30 seconds"
    val millis: Long    // machine-readable duration
) {
    companion object {
        fun from(value: Long, unit: TimeUnit): TimePeriodPreset =
            TimePeriodPreset(label = unit.format(value), millis = value * unit.multiplierMs)
    }
}

// ─── Time unit picker helper ──────────────────────────────────────────────────

enum class TimeUnit(val displayName: String, val multiplierMs: Long) {
    SECONDS("Seconds", 1_000L) {
        override fun format(value: Long) = if (value == 1L) "1 second" else "$value seconds"
    },
    MINUTES("Minutes", 60_000L) {
        override fun format(value: Long) = if (value == 1L) "1 minute" else "$value minutes"
    },
    HOURS("Hours", 3_600_000L) {
        override fun format(value: Long) = if (value == 1L) "1 hour" else "$value hours"
    },
    DAYS("Days", 86_400_000L) {
        override fun format(value: Long) = if (value == 1L) "1 day" else "$value days"
    };

    abstract fun format(value: Long): String
}

// ─── Defaults ─────────────────────────────────────────────────────────────────

val DEFAULT_TIME_PRESETS = listOf(
    TimePeriodPreset("30 seconds", 30_000L),
    TimePeriodPreset("1 minute",   60_000L),
    TimePeriodPreset("1 hour",     3_600_000L),
    TimePeriodPreset("1 day",      86_400_000L)
)

// ─── Serialization — NO external library needed ───────────────────────────────
//
// Storage format: "label|millis" pairs joined by commas
//   e.g.  "30 seconds|30000,1 minute|60000,1 hour|3600000,1 day|86400000"
//
// Fallback 1 (previous JSON attempt): [{"label":"...","millis":...},...]
// Fallback 2 (original legacy CSV):   "30 sec,1 hour,1 week,1 month,never"

fun encodeTimePeriodPresets(presets: List<TimePeriodPreset>): String =
    presets.joinToString(",") { "${it.label}|${it.millis}" }

fun decodeTimePeriodPresets(raw: String?): List<TimePeriodPreset> {
    if (raw.isNullOrBlank()) return DEFAULT_TIME_PRESETS
    return when {
        raw.contains("|") -> parsePipeFormat(raw)
        raw.contains("{") -> parseJsonFallback(raw).takeIf { it.isNotEmpty() } ?: DEFAULT_TIME_PRESETS
        else              -> parseLegacyCsvPresets(raw).takeIf { it.isNotEmpty() } ?: DEFAULT_TIME_PRESETS
    }
}

/** New pipe format: "30 seconds|30000,1 minute|60000" */
private fun parsePipeFormat(raw: String): List<TimePeriodPreset> {
    val result = mutableListOf<TimePeriodPreset>()
    raw.split(",").forEach { entry ->
        val idx = entry.lastIndexOf("|")
        if (idx > 0) {
            val label  = entry.substring(0, idx).trim()
            val millis = entry.substring(idx + 1).trim().toLongOrNull()
            if (label.isNotBlank() && millis != null && millis > 0) {
                result.add(TimePeriodPreset(label, millis))
            }
        }
    }
    return if (result.isEmpty()) DEFAULT_TIME_PRESETS else result
}

/**
 * Parses our previous JSON storage format without any JSON library.
 * Handles:  [{"label":"30 seconds","millis":30000},...]
 *       and [{"millis":30000,"label":"30 seconds"},...]  (field-order agnostic)
 */
private fun parseJsonFallback(json: String): List<TimePeriodPreset> {
    val result = mutableListOf<TimePeriodPreset>()
    // Match each {...} object block
    val objectRegex = Regex("""\{([^}]+)\}""")
    objectRegex.findAll(json).forEach { objMatch ->
        val body = objMatch.groupValues[1]
        val label  = Regex(""""label"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        val millis = Regex(""""millis"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toLongOrNull()
        if (!label.isNullOrBlank() && millis != null && millis > 0) {
            result.add(TimePeriodPreset(label, millis))
        }
    }
    return result
}

/**
 * Migrates the original comma-separated string format.
 * e.g. "30 sec,1 hour,1 week,1 month,never"
 * Unknown/unparseable tokens (like "never") are silently dropped.
 */
fun parseLegacyCsvPresets(csv: String): List<TimePeriodPreset> {
    val result = mutableListOf<TimePeriodPreset>()
    csv.split(",").map { it.trim() }.forEach { token ->
        val preset = parseLegacyToken(token)
        if (preset != null) result.add(preset)
    }
    return result
}

private val legacyPatterns = listOf(
    Regex("""(\d+)\s*sec(?:ond)?s?""",  RegexOption.IGNORE_CASE) to 1_000L,
    Regex("""(\d+)\s*min(?:ute)?s?""",  RegexOption.IGNORE_CASE) to 60_000L,
    Regex("""(\d+)\s*h(?:our)?s?""",    RegexOption.IGNORE_CASE) to 3_600_000L,
    Regex("""(\d+)\s*d(?:ay)?s?""",     RegexOption.IGNORE_CASE) to 86_400_000L,
    Regex("""(\d+)\s*w(?:eek)?s?""",    RegexOption.IGNORE_CASE) to 604_800_000L,
    Regex("""(\d+)\s*mo(?:nth)?s?""",   RegexOption.IGNORE_CASE) to 2_592_000_000L
)

private fun parseLegacyToken(token: String): TimePeriodPreset? {
    // Skip tokens that look like JSON fragments (contain { or ")
    if (token.contains('{') || token.contains('"')) return null
    for ((regex, multiplier) in legacyPatterns) {
        val match = regex.find(token) ?: continue
        val value = match.groupValues[1].toLongOrNull() ?: continue
        return TimePeriodPreset(label = token, millis = value * multiplier)
    }
    return null
}
