package com.msahil432.multitool.ui.screens

import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.downloader.CanvasCapture
import com.msahil432.multitool.downloader.DetectedMedia
import com.msahil432.multitool.downloader.MediaDownloadWorker
import com.msahil432.multitool.downloader.WebViewMediaDetector
import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel managing the video downloader state: URL input, media detection observation,
 * user selection, background download scheduling, and download history management.
 */
class VideoDownloaderViewModel(
    private val downloadRepository: DownloadRepository,
    private val appContext: Context
) : ViewModel() {

    /** WebView media detector coordinating DOM inspection, network requests, and canvas capture. */
    val detector = WebViewMediaDetector()

    /** Reactive stream of all download records. */
    val downloads: StateFlow<List<DownloadItem>> = downloadRepository.allDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUrl = MutableStateFlow("")
    /** Currently entered web page URL. */
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _showSelectionDialog = MutableStateFlow(false)
    /** Whether the media selection sheet/dialog is currently visible. */
    val showSelectionDialog: StateFlow<Boolean> = _showSelectionDialog.asStateFlow()

    private val _selectedMedia = MutableStateFlow<Set<DetectedMedia>>(emptySet())
    /** Currently selected media items in the selection sheet. */
    val selectedMedia: StateFlow<Set<DetectedMedia>> = _selectedMedia.asStateFlow()

    init {
        viewModelScope.launch {
            detector.detectedMedia.collect { list ->
                if (_selectedMedia.value.isNotEmpty()) {
                    val mediaByUrl = list.associateBy { it.url }
                    _selectedMedia.value = _selectedMedia.value.map { selected ->
                        mediaByUrl[selected.url] ?: selected
                    }.toSet()
                }
            }
        }
    }

    /** Updates the URL string from the text input field. */
    fun onUrlChanged(url: String) {
        _currentUrl.value = url
    }

    /**
     * Normalizes the URL, resets media detection state, and instructs the WebView to load it.
     */
    fun loadUrl(webView: WebView) {
        val rawUrl = _currentUrl.value.trim()
        if (rawUrl.isBlank()) return

        val normalizedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }

        _currentUrl.value = normalizedUrl
        detector.reset()
        webView.loadUrl(normalizedUrl)

        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "WebView URL load triggered",
            data = mapOf("url" to normalizedUrl)
        )
    }

    /**
     * Clears the entered URL string, resets media detection candidate state, and unloads any active page.
     *
     * @param webView Optional active [WebView] instance to reset to about:blank.
     */
    fun clearUrl(webView: WebView? = null) {
        _currentUrl.value = ""
        detector.reset()
        webView?.loadUrl("about:blank")
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "Cleared URL and reset WebView",
            data = emptyMap()
        )
    }

    /** Opens the detected media selection sheet. */
    fun showMediaSelection() {
        _showSelectionDialog.value = true
        _selectedMedia.value = emptySet()
    }

    /** Dismisses the detected media selection sheet. */
    fun dismissMediaSelection() {
        _showSelectionDialog.value = false
        _selectedMedia.value = emptySet()
    }

    /** Toggles the selection state of a detected media candidate. */
    fun toggleMediaSelection(media: DetectedMedia) {
        val current = _selectedMedia.value.toMutableSet()
        val match = current.find { it.url == media.url }
        if (match != null) {
            current.remove(match)
        } else {
            current.add(media)
        }
        _selectedMedia.value = current
    }

    /** Selects all currently detected media items. */
    fun selectAll() {
        _selectedMedia.value = detector.detectedMedia.value.toSet()
    }

    /** Deselects all items in the selection sheet. */
    fun deselectAll() {
        _selectedMedia.value = emptySet()
    }

    /**
     * Enqueues all selected media items into the repository and schedules their downloads.
     */
    fun downloadSelected() {
        val selected = _selectedMedia.value.toList()
        if (selected.isEmpty()) return

        val sourceUrl = _currentUrl.value

        viewModelScope.launch(Dispatchers.IO) {
            try {
                selected.forEach { media ->
                    val downloadId = downloadRepository.enqueue(
                        sourcePageUrl = sourceUrl,
                        mediaUrl = media.url,
                        fileName = media.suggestedFileName,
                        mimeType = media.mimeType,
                        mediaType = media.mediaType,
                        thumbnailUrl = media.thumbnailUrl
                    )
                    MediaDownloadWorker.schedule(appContext, downloadId)
                }

                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Enqueued media items for download",
                    data = mapOf("count" to selected.size)
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        dismissMediaSelection()
    }

    /**
     * Starts canvas stream recording for an identified non-standard video container.
     */
    fun startCanvasCapture(webView: WebView, capture: CanvasCapture, durationMs: Long = 10_000L) {
        detector.captureCanvas(webView, capture.selector, durationMs)
    }

    /**
     * Retries a failed or cancelled download.
     */
    fun retryDownload(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadRepository.retry(downloadId)
                MediaDownloadWorker.schedule(appContext, downloadId)
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Cancels an active or queued download.
     */
    fun cancelDownload(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadRepository.cancel(downloadId)
                WorkManager.getInstance(appContext).cancelUniqueWork("Download_$downloadId")
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Deletes a download record and removes its downloaded file from local storage.
     */
    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val item = downloadRepository.getById(downloadId)
                item?.localFilePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                downloadRepository.deleteDownload(downloadId)
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Clears all completed downloads from history without deleting files.
     */
    fun clearCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadRepository.clearCompleted()
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Clears all download records and removes their local files.
     */
    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allItems = downloads.value
                allItems.forEach { item ->
                    item.localFilePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
                downloadRepository.clearAll()
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Retrieves the source page URL for reloading in the downloader screen.
     */
    suspend fun getSourceUrl(downloadId: Long): String? {
        return downloadRepository.getById(downloadId)?.sourcePageUrl
    }

    override fun onCleared() {
        super.onCleared()
        detector.reset()
    }

    /**
     * Factory for creating [VideoDownloaderViewModel] with manual dependencies.
     */
    class Factory(
        private val downloadRepository: DownloadRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoDownloaderViewModel::class.java)) {
                return VideoDownloaderViewModel(downloadRepository, appContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
