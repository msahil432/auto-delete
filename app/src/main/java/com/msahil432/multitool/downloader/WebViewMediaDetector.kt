package com.msahil432.multitool.downloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
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
            "mp4", "webm", "mkv", "m3u8", "ts", "gif", "webp"
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
            "image/webp"
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
     * and network interception.
     */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.addJavascriptInterface(JsBridge(), "MultiToolBridge")

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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                _pageLoadingState.value = PageLoadState.LOADED

                // Strategy 1: Inject DOM scan script
                view?.evaluateJavascript(MediaDetectionJs.DOM_SCAN_SCRIPT, null)

                // Strategy 3: Inject canvas detection script with brief delay for lazy elements
                view?.postDelayed({
                    view.evaluateJavascript(MediaDetectionJs.CANVAS_DETECT_SCRIPT, null)
                }, 1500)

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
     * Checks if a URL points to video or GIF media by file extension or MIME hints.
     */
    internal fun isMediaUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', "")
        return extension in VIDEO_EXTENSIONS || VIDEO_MIME_TYPES.any { lowerUrl.contains(it) }
    }

    /**
     * Categorizes a media URL into a [MediaType].
     */
    internal fun classifyUrl(url: String): MediaType {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".m3u8") || lowerUrl.contains(".m3u8") || lowerUrl.contains("mpegurl") -> MediaType.HLS_STREAM
            path.endsWith(".mpd") || lowerUrl.contains(".mpd") || lowerUrl.contains("dash") -> MediaType.DASH_STREAM
            path.endsWith(".gif") || lowerUrl.contains(".gif?") -> MediaType.GIF
            path.endsWith(".webp") || lowerUrl.contains(".webp?") -> MediaType.ANIMATED_WEBP
            else -> MediaType.VIDEO
        }
    }

    /**
     * Guesses the MIME type from the resource URL path.
     */
    internal fun guessMimeType(url: String): String? {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            else -> null
        }
    }

    /**
     * Derives a file name from the URL path or provides a fallback name.
     */
    internal fun extractFileName(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        return if (name.isNotBlank() && name.contains('.')) {
            name.take(100)
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
                            detectionStrategy = DetectionStrategy.DOM_ELEMENT,
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
