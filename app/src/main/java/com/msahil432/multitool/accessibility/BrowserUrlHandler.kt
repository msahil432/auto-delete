package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.msahil432.multitool.data.BrowsingKind
import com.msahil432.multitool.data.BrowsingRepository
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * [AccessibilityHandler] that tracks domain names and search engine queries
 * from address bars in supported web browsers.
 *
 * Security & Privacy:
 * - Password fields (`isPassword == true`) are strictly skipped.
 * - Stored locally only; no network transmission.
 * - Respects the user's opt-in toggle (`track_browser_urls`).
 * - Debounces rapid typing by 800ms to record only stable inputs.
 */
class BrowserUrlHandler(
    private val settingsRepository: SettingsRepository,
    private val browsingRepository: BrowsingRepository,
    private val usageRepository: UsageRepository? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val debounceDelayMs: Long = 800L,
    private val clock: () -> Long = System::currentTimeMillis
) : AccessibilityHandler {

    @Volatile
    internal var trackBrowserUrls: Boolean = false
        private set

    private val debounceJobs = ConcurrentHashMap<String, Job>()
    private val lastRecordedEntry = ConcurrentHashMap<String, Pair<BrowsingKind, String>>()

    init {
        coroutineScope.launch {
            settingsRepository.trackBrowserUrls.collectLatest { enabled ->
                trackBrowserUrls = enabled
            }
        }
    }

    override fun onEvent(svc: AccessibilityService, e: AccessibilityEvent) {
        if (!trackBrowserUrls) return

        val pkg = e.packageName?.toString() ?: return

        if (!BrowserSignatures.isSupportedBrowser(pkg)) return

        val node = findAddressBarNode(svc, pkg, e) ?: return

        try {
            // Strictly enforce password boundary: never read masked password fields
            if (node.isPassword) {
                return
            }

            val text = node.text?.toString()?.trim()
            if (text.isNullOrBlank()) return

            val parsed = normalizeBrowsingText(text) ?: return
            val (kind, value) = parsed

            // Ignore if this is already the last recorded value for this package
            if (lastRecordedEntry[pkg] == parsed) {
                return
            }

            // Schedule recording after debounce period
            val job = coroutineScope.launch {
                delay(debounceDelayMs)
                lastRecordedEntry[pkg] = parsed
                browsingRepository.recordBrowsing(
                    packageName = pkg,
                    kind = kind,
                    value = value,
                    timestamp = clock()
                )
            }
            debounceJobs[pkg] = job
        } catch (_: Exception) {
            // Failsafe: avoid crashing accessibility service on node recycling or parse error
        }
    }

    /**
     * Attempts to find the address bar node from event source or window hierarchy.
     */
    private fun findAddressBarNode(
        svc: AccessibilityService,
        pkg: String,
        event: AccessibilityEvent
    ): AccessibilityNodeInfo? {
        val targetViewId = BrowserSignatures.getUrlBarViewId(pkg) ?: return null

        // 1. Check event source node if available
        val sourceNode = try { event.source } catch (_: Exception) { null }
        if (sourceNode != null) {
            val viewId = try { sourceNode.viewIdResourceName } catch (_: Exception) { null }
            if (viewId == targetViewId || (viewId != null && targetViewId.endsWith(viewId))) {
                return sourceNode
            }
        }

        // 2. Search root in active window
        val rootNode = try { svc.rootInActiveWindow } catch (_: Exception) { null } ?: return null
        val matchedNodes = try {
            rootNode.findAccessibilityNodeInfosByViewId(targetViewId)
        } catch (_: Exception) {
            emptyList()
        }

        if (!matchedNodes.isNullOrEmpty()) {
            return matchedNodes[0]
        }

        // Fallback: search by simple ID (e.g. "url_bar")
        val simpleId = targetViewId.substringAfter(":id/")
        if (simpleId.isNotEmpty() && simpleId != targetViewId) {
            val fallbackMatches = try {
                rootNode.findAccessibilityNodeInfosByViewId(simpleId)
            } catch (_: Exception) {
                emptyList()
            }
            if (!fallbackMatches.isNullOrEmpty()) {
                return fallbackMatches[0]
            }
        }

        return null
    }

    companion object {
        private val DOMAIN_PATTERN = Pattern.compile(
            "^(?:https?://)?([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+)(?::\\d+)?(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
        )

        private val SEARCH_ENGINES = listOf(
            "google.",
            "bing.com",
            "duckduckgo.com",
            "search.yahoo.com",
            "ecosia.org",
            "yandex.",
            "baidu.com",
            "brave.com/search"
        )

        private val PLACEHOLDERS = setOf(
            "search or type url",
            "search or type web address",
            "search or enter address",
            "search or enter url",
            "search",
            "type a url",
            "search the web",
            "type web address"
        )

        /**
         * Parses and normalizes raw address bar text into either a URL domain or search query.
         */
        fun normalizeBrowsingText(rawText: String): Pair<BrowsingKind, String>? {
            val trimmed = rawText.trim()
            if (trimmed.isBlank()) return null

            val lower = trimmed.lowercase(Locale.ROOT)
            if (PLACEHOLDERS.contains(lower)) return null

            // Check if full URL (starts with scheme or contains search engine URL)
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return parseHttpUrl(trimmed)
            }

            // If text contains whitespace, it is clearly a search query
            if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) {
                return Pair(BrowsingKind.SEARCH_QUERY, trimmed)
            }

            // Check if text conforms to domain format
            val matcher = DOMAIN_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                val host = matcher.group(1)?.lowercase(Locale.ROOT)
                if (host != null) {
                    val cleanHost = host.removePrefix("www.")
                    // Check if search engine domain with query
                    if (isSearchEngineHost(cleanHost) && trimmed.contains("?")) {
                        val query = extractQueryFromUrl(trimmed)
                        if (!query.isNullOrBlank()) {
                            return Pair(BrowsingKind.SEARCH_QUERY, query)
                        }
                    }
                    return Pair(BrowsingKind.URL, cleanHost)
                }
            }

            // Fallback for short inputs without spaces or dots: treated as search query
            return Pair(BrowsingKind.SEARCH_QUERY, trimmed)
        }

        private fun parseHttpUrl(url: String): Pair<BrowsingKind, String> {
            return try {
                val uri = Uri.parse(url)
                val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")

                if (host != null && isSearchEngineHost(host)) {
                    val query = extractQueryFromUri(uri)
                    if (!query.isNullOrBlank()) {
                        return Pair(BrowsingKind.SEARCH_QUERY, query)
                    }
                }

                val domain = host ?: url.substringBefore('?').substringBefore('#')
                Pair(BrowsingKind.URL, domain)
            } catch (_: Exception) {
                Pair(BrowsingKind.URL, url)
            }
        }

        private fun isSearchEngineHost(host: String): Boolean {
            return SEARCH_ENGINES.any { host.contains(it) }
        }

        private fun extractQueryFromUri(uri: Uri): String? {
            val queryParamNames = listOf("q", "p", "query", "text", "search_query", "wd")
            for (param in queryParamNames) {
                val value = uri.getQueryParameter(param)
                if (!value.isNullOrBlank()) {
                    return value.trim()
                }
            }
            return null
        }

        private fun extractQueryFromUrl(url: String): String? {
            val queryString = url.substringAfter('?', "")
            if (queryString.isEmpty()) return null

            val params = queryString.split('&')
            val queryKeys = listOf("q=", "p=", "query=", "text=", "search_query=", "wd=")
            for (param in params) {
                for (key in queryKeys) {
                    if (param.startsWith(key, ignoreCase = true)) {
                        val rawVal = param.substring(key.length)
                        return try {
                            URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name()).trim()
                        } catch (_: Exception) {
                            rawVal.replace('+', ' ').trim()
                        }
                    }
                }
            }
            return null
        }
    }
}
