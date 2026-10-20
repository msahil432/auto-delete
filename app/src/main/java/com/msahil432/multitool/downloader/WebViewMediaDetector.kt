package com.msahil432.multitool.downloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.util.Breadcrumbs
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Possible loading states of a web page loaded for media detection.
 */
enum class PageLoadState {
    /** Initial idle state before loading any URL. */
    IDLE,
    /** Page is currently being fetched and parsed. */
    LOADING,
    /** Page finished loading and detection scripts were injected. */
    LOADED,
    /** Main frame page load failed. */
    ERROR
}

/**
 * Represents a candidate element discovered by canvas/non-standard video detection.
 */
data class CanvasCapture(
    val type: String,
    val selector: String,
    val width: Int,
    val height: Int,
    val directUrl: String? = null
)

/**
 * Coordinates media detection across DOM inspection, network interception, and canvas streaming.
 *
 * Exposes reactive [StateFlow] streams for detected items and page lifecycle state.
 */
class WebViewMediaDetector {

    internal companion object {
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "webm", "mkv", "m3u8", "ts", "gif", "webp", "mpd", "m4s", "m4v", "mov", "avi", "flv"
        )

        val VIDEO_MIME_TYPES = setOf(
            "video/mp4",
            "video/webm",
            "video/x-matroska",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "video/mp2t",
            "application/dash+xml",
            "image/gif",
            "image/webp",
            "video/quicktime",
            "video/x-m4v"
        )
    }

    private val moshi = Moshi.Builder().build()
    private val detectedListType = Types.newParameterizedType(List::class.java, JsDetectedItem::class.java)
    private val detectedAdapter = moshi.adapter<List<JsDetectedItem>>(detectedListType)
    private val candidateListType = Types.newParameterizedType(List::class.java, JsCanvasCandidate::class.java)
    private val candidateAdapter = moshi.adapter<List<JsCanvasCandidate>>(candidateListType)

    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    /** Reactive stream of all detected media items, deduplicated by URL. */
    val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia.asStateFlow()

    private val _pageLoadingState = MutableStateFlow(PageLoadState.IDLE)
    /** Current page load lifecycle state. */
    val pageLoadingState: StateFlow<PageLoadState> = _pageLoadingState.asStateFlow()

    private val _canvasCaptures = MutableStateFlow<List<CanvasCapture>>(emptyList())
    /** Canvas and non-standard candidate elements identified on the page. */
    val canvasCaptures: StateFlow<List<CanvasCapture>> = _canvasCaptures.asStateFlow()

    private val seenUrls = mutableSetOf<String>()

    /**
     * Configures the provided [WebView] with JavaScript bridge, DOM scan injection,
     * ServiceWorker network interception, and main client request interception.
     */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun configure(webView: WebView) {
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.addJavascriptInterface(JsBridge(), "MultiToolBridge")
        configureServiceWorker()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                _pageLoadingState.value = PageLoadState.LOADING
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Page load started",
                    data = mapOf("url" to (url ?: "null"))
                )
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                // Inject early detection hooks as soon as page DOM is visible
                view?.evaluateJavascript(MediaDetectionJs.HOOKS_AND_OBSERVER_SCRIPT, null)
                view?.evaluateJavascript(MediaDetectionJs.DOM_SCAN_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                _pageLoadingState.value = PageLoadState.LOADED

                // Strategy 1: Inject runtime hooks and comprehensive DOM scan scripts
                view?.evaluateJavascript(MediaDetectionJs.HOOKS_AND_OBSERVER_SCRIPT, null)
                view?.evaluateJavascript(MediaDetectionJs.DOM_SCAN_SCRIPT, null)

                // Strategy 3: Inject canvas detection script with brief delay for lazy elements
                view?.postDelayed({
                    view.evaluateJavascript(MediaDetectionJs.CANVAS_DETECT_SCRIPT, null)
                }, 1500)

                // Rescan at intervals to catch lazy SPA component hydration (Reddit shreddit-player, RedGIFs dynamic player)
                view?.postDelayed({
                    view.evaluateJavascript(MediaDetectionJs.DOM_SCAN_SCRIPT, null)
                }, 1000)

                view?.postDelayed({
                    view.evaluateJavascript(MediaDetectionJs.DOM_SCAN_SCRIPT, null)
                }, 2500)

                view?.postDelayed({
                    view.evaluateJavascript(MediaDetectionJs.DOM_SCAN_SCRIPT, null)
                }, 5000)

                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Page load finished, detection scripts injected",
                    data = mapOf("url" to (url ?: "null"))
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    _pageLoadingState.value = PageLoadState.ERROR
                    Breadcrumbs.record(
                        category = Breadcrumbs.CAT_DOWNLOADER,
                        message = "Page load error",
                        data = mapOf(
                            "errorCode" to (error?.errorCode ?: -1),
                            "description" to (error?.description?.toString() ?: "unknown")
                        )
                    )
                }
            }

            // Strategy 2: Network resource interception
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    if (isMediaUrl(url)) {
                        val mediaType = classifyUrl(url)
                        addDetectedMedia(
                            DetectedMedia(
                                url = url,
                                mimeType = guessMimeType(url),
                                suggestedFileName = extractFileName(url),
                                mediaType = mediaType,
                                detectionStrategy = DetectionStrategy.NETWORK_INTERCEPT
                            )
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    /**
     * Registers a [ServiceWorkerClient] to intercept network media streams requested by background
     * service workers (commonly used by modern web apps like Reddit and RedGIFs).
     */
    private fun configureServiceWorker() {
        try {
            val swController = ServiceWorkerController.getInstance()
            swController.setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? {
                    request?.url?.toString()?.let { url ->
                        if (isMediaUrl(url)) {
                            val mediaType = classifyUrl(url)
                            addDetectedMedia(
                                DetectedMedia(
                                    url = url,
                                    mimeType = guessMimeType(url),
                                    suggestedFileName = extractFileName(url),
                                    mediaType = mediaType,
                                    detectionStrategy = DetectionStrategy.NETWORK_INTERCEPT
                                )
                            )
                        }
                    }
                    return null
                }
            })
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Resets internal detection state, cleared URLs, and status for loading a new URL.
     */
    fun reset() {
        _detectedMedia.value = emptyList()
        _canvasCaptures.value = emptyList()
        _pageLoadingState.value = PageLoadState.IDLE
        synchronized(seenUrls) {
            seenUrls.clear()
        }
    }

    /**
     * Initiates canvas stream capture on the specified element.
     */
    fun captureCanvas(webView: WebView, selector: String, durationMs: Long = 5000L) {
        val script = MediaDetectionJs.canvasCaptureScript(selector, durationMs)
        webView.evaluateJavascript(script, null)
    }

    /**
     * Internal helper to add and deduplicate detected media items.
     */
    internal fun addDetectedMedia(media: DetectedMedia) {
        synchronized(seenUrls) {
            if (seenUrls.add(media.url)) {
                _detectedMedia.value = _detectedMedia.value + media
            }
        }
    }

    /**
     * Checks if a URL points to video or GIF media by file extension, MIME hints, or platform signatures.
     */
    internal fun isMediaUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        // Skip analytics, telemetry, and tracking endpoints
        if (lowerUrl.contains("/telemetry") || lowerUrl.contains("/analytics") || lowerUrl.contains("/event")) {
            return false
        }
        // Skip audio-only DASH tracks to prioritize complete video and HLS streams
        if (lowerUrl.contains("dash_audio")) {
            return false
        }

        val path = lowerUrl.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', "")
        if (extension in VIDEO_EXTENSIONS) return true
        if (VIDEO_MIME_TYPES.any { lowerUrl.contains(it) }) return true

        // Reddit: v.redd.it stream manifests and video endpoints
        if (lowerUrl.contains("v.redd.it")) {
            if (lowerUrl.contains("dash") || lowerUrl.contains("hls") || lowerUrl.contains(".mp4") || lowerUrl.contains(".m3u8")) {
                return true
            }
            if (path.substringAfterLast('/').startsWith("dash_")) {
                return true
            }
        }

        // RedGIFs: media.redgifs.com or thumbs2.redgifs.com video assets
        if (lowerUrl.contains("redgifs.com")) {
            if (lowerUrl.contains("media.redgifs.com") || lowerUrl.contains("thumbs2.redgifs.com")) {
                if (lowerUrl.contains(".mp4") || lowerUrl.contains(".m3u8") || lowerUrl.contains(".m4s") || lowerUrl.contains("-mobile")) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Categorizes a media URL into a [MediaType].
     */
    internal fun classifyUrl(url: String): MediaType {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".m3u8") || lowerUrl.contains(".m3u8") || lowerUrl.contains("mpegurl") || lowerUrl.contains("hlsplaylist") -> MediaType.HLS_STREAM
            path.endsWith(".mpd") || lowerUrl.contains(".mpd") || lowerUrl.contains("dashplaylist") -> MediaType.DASH_STREAM
            path.endsWith(".gif") || lowerUrl.contains(".gif?") -> MediaType.GIF
            path.endsWith(".webp") || lowerUrl.contains(".webp?") -> MediaType.ANIMATED_WEBP
            else -> MediaType.VIDEO
        }
    }

    /**
     * Guesses the MIME type from the resource URL path or platform domain.
     */
    internal fun guessMimeType(url: String): String? {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".mp4") || lowerUrl.contains(".mp4") -> "video/mp4"
            path.endsWith(".webm") || lowerUrl.contains(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".m3u8") || lowerUrl.contains(".m3u8") || lowerUrl.contains("hlsplaylist") -> "application/vnd.apple.mpegurl"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".mpd") || lowerUrl.contains(".mpd") || lowerUrl.contains("dashplaylist") -> "application/dash+xml"
            path.endsWith(".gif") || lowerUrl.contains(".gif") -> "image/gif"
            path.endsWith(".webp") || lowerUrl.contains(".webp") -> "image/webp"
            lowerUrl.contains("v.redd.it") -> "video/mp4"
            lowerUrl.contains("redgifs.com") -> "video/mp4"
            else -> null
        }
    }

    /**
     * Derives a clean file name from the URL path or platform identifiers.
     */
    internal fun extractFileName(url: String): String {
        val lowerUrl = url.lowercase()
        val path = url.substringBefore('?').substringBefore('#')
        val rawName = path.substringAfterLast('/')

        if (lowerUrl.contains("v.redd.it")) {
            val segments = path.split('/').filter { it.isNotBlank() }
            val videoId = if (segments.size >= 2) segments[segments.size - 2] else "reddit"
            return if (lowerUrl.contains("hls")) {
                "reddit_${videoId}_hls.mp4"
            } else if (lowerUrl.contains("dash")) {
                val quality = rawName.substringAfter("DASH_", "video").substringBefore('.')
                "reddit_${videoId}_$quality.mp4"
            } else {
                "reddit_${videoId}.mp4"
            }
        }

        if (lowerUrl.contains("redgifs.com")) {
            val cleanId = rawName.substringBefore('-').substringBefore('.')
            return "redgifs_${cleanId}.mp4"
        }

        return if (rawName.isNotBlank() && rawName.contains('.')) {
            rawName.take(100)
        } else {
            "media_${System.currentTimeMillis()}.mp4"
        }
    }

    /**
     * JavaScript interface bridge class bound to `window.MultiToolBridge`.
     */
    private inner class JsBridge {
        @JavascriptInterface
        fun onMediaDetected(json: String) {
            try {
                val items = detectedAdapter.fromJson(json) ?: emptyList()
                items.forEach { item ->
                    val mediaType = when (item.mediaType?.uppercase()) {
                        "GIF" -> MediaType.GIF
                        "ANIMATED_WEBP" -> MediaType.ANIMATED_WEBP
                        "HLS_STREAM" -> MediaType.HLS_STREAM
                        "DASH_STREAM" -> MediaType.DASH_STREAM
                        else -> classifyUrl(item.url)
                    }
                    addDetectedMedia(
                        DetectedMedia(
                            url = item.url,
                            mimeType = item.mimeType ?: guessMimeType(item.url),
                            suggestedFileName = extractFileName(item.url),
                            mediaType = mediaType,
                            detectionStrategy = when (item.strategy) {
                                "REDDIT_PLAYER" -> DetectionStrategy.DOM_ELEMENT
                                "REDGIFS_API" -> DetectionStrategy.NETWORK_INTERCEPT
                                "REDGIFS_EMBED" -> DetectionStrategy.DOM_ELEMENT
                                "NETWORK_FETCH" -> DetectionStrategy.NETWORK_INTERCEPT
                                "NETWORK_XHR" -> DetectionStrategy.NETWORK_INTERCEPT
                                "PLAYBACK_EVENT" -> DetectionStrategy.DOM_ELEMENT
                                else -> DetectionStrategy.DOM_ELEMENT
                            },
                            width = item.width ?: -1,
                            height = item.height ?: -1,
                            durationSeconds = item.duration ?: -1.0,
                            thumbnailUrl = item.thumbnail
                        )
                    )
                }
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "DOM media items detected",
                    data = mapOf("count" to items.size)
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        @JavascriptInterface
        fun onCanvasCandidates(json: String) {
            try {
                val candidates = candidateAdapter.fromJson(json) ?: emptyList()
                _canvasCaptures.value = candidates.map { c ->
                    CanvasCapture(
                        type = c.type,
                        selector = c.selector,
                        width = c.width ?: -1,
                        height = c.height ?: -1,
                        directUrl = c.url
                    )
                }

                candidates.filter { !it.url.isNullOrBlank() }.forEach { c ->
                    val url = c.url!!
                    addDetectedMedia(
                        DetectedMedia(
                            url = url,
                            mimeType = guessMimeType(url),
                            suggestedFileName = extractFileName(url),
                            mediaType = classifyUrl(url),
                            detectionStrategy = DetectionStrategy.CANVAS_CAPTURE,
                            width = c.width ?: -1,
                            height = c.height ?: -1
                        )
                    )
                }

                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Canvas candidates detected",
                    data = mapOf("count" to candidates.size)
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        @JavascriptInterface
        fun onCanvasCaptured(base64Data: String, mimeType: String) {
            try {
                val timestamp = System.currentTimeMillis()
                addDetectedMedia(
                    DetectedMedia(
                        url = "captured://canvas_$timestamp",
                        mimeType = mimeType,
                        suggestedFileName = "canvas_capture_$timestamp.webm",
                        mediaType = MediaType.VIDEO,
                        detectionStrategy = DetectionStrategy.CANVAS_CAPTURE
                    )
                )
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Canvas stream captured",
                    data = mapOf("mimeType" to mimeType, "dataLength" to base64Data.length)
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        @JavascriptInterface
        fun onCaptureStarted(selector: String, durationMs: Long) {
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Canvas capture started",
                data = mapOf("selector" to selector, "durationMs" to durationMs)
            )
        }

        @JavascriptInterface
        fun onCaptureError(error: String) {
            Breadcrumbs.record(
                category = Breadcrumbs.CAT_DOWNLOADER,
                message = "Canvas capture error",
                data = mapOf("error" to error)
            )
        }
    }
}
