package com.msahil432.autodelete.data

// ─── Data class ───────────────────────────────────────────────────────────────

enum class FilterMatchType {
    PREFIX,   // filename starts with the pattern
    SUFFIX,   // filename ends with the pattern (e.g. ".tmp")
    CONTAINS  // filename contains the pattern anywhere
}

data class FilterRule(
    val pattern: String,
    val matchType: FilterMatchType
) {
    /** Returns true if [fileName] (NOT full path) matches this rule. Case-insensitive. */
    fun matches(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val pat   = pattern.lowercase()
        return when (matchType) {
            FilterMatchType.PREFIX   -> lower.startsWith(pat)
            FilterMatchType.SUFFIX   -> lower.endsWith(pat)
            FilterMatchType.CONTAINS -> lower.contains(pat)
        }
    }
}

// ─── Defaults ─────────────────────────────────────────────────────────────────

val DEFAULT_EXCLUSION_RULES = listOf(
    FilterRule(".trash",   FilterMatchType.PREFIX),
    FilterRule(".pending", FilterMatchType.PREFIX),
    FilterRule(".nomedia", FilterMatchType.PREFIX),
    FilterRule(".tmp",     FilterMatchType.SUFFIX)
)

// ─── Serialization — NO external library needed ───────────────────────────────
//
// Storage format: "pattern|MATCHTYPE" pairs joined by semicolons
//   e.g.  ".trash|PREFIX;.pending|PREFIX;.tmp|SUFFIX"
//
// Fallback (previous JSON attempt): [{"pattern":"...","matchType":"..."},...]

fun encodeFilterRules(rules: List<FilterRule>): String =
    rules.joinToString(";") { "${it.pattern}|${it.matchType.name}" }

fun decodeFilterRules(raw: String?): List<FilterRule> {
    if (raw.isNullOrBlank()) return emptyList()
    return when {
        raw.contains("|") -> parsePipeFilterFormat(raw)
        raw.contains("{") -> parseFilterJsonFallback(raw)
        else              -> emptyList()
    }
}

/** New format: ".trash|PREFIX;.pending|PREFIX" */
private fun parsePipeFilterFormat(raw: String): List<FilterRule> {
    val result = mutableListOf<FilterRule>()
    raw.split(";").forEach { entry ->
        val idx = entry.lastIndexOf("|")
        if (idx > 0) {
            val pattern   = entry.substring(0, idx).trim()
            val typeName  = entry.substring(idx + 1).trim()
            val matchType = runCatching { FilterMatchType.valueOf(typeName) }.getOrNull()
            if (pattern.isNotBlank() && matchType != null) {
                result.add(FilterRule(pattern, matchType))
            }
        }
    }
    return result
}

/**
 * Parses previous Moshi JSON format without any JSON library.
 * Handles: [{"pattern":"...","matchType":"..."},...]
 */
private fun parseFilterJsonFallback(json: String): List<FilterRule> {
    val result = mutableListOf<FilterRule>()
    val objectRegex = Regex("""\{([^}]+)\}""")
    objectRegex.findAll(json).forEach { objMatch ->
        val body      = objMatch.groupValues[1]
        val pattern   = Regex(""""pattern"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        val typeName  = Regex(""""matchType"\s*:\s*"([A-Z]+)"""").find(body)?.groupValues?.get(1)
        val matchType = typeName?.let { runCatching { FilterMatchType.valueOf(it) }.getOrNull() }
        if (!pattern.isNullOrBlank() && matchType != null) {
            result.add(FilterRule(pattern, matchType))
        }
    }
    return result
}
