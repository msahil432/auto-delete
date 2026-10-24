package com.msahil432.multitool.ui.screens

import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.downloader.CanvasCapture
import com.msahil432.multitool.downloader.DetectedMedia
import com.msahil432.multitool.downloader.MediaDownloadWorker
import com.msahil432.multitool.downloader.DownloadToastController
import com.msahil432.multitool.downloader.MediaListEntry
import com.msahil432.multitool.downloader.WebViewMediaDetector
import com.msahil432.multitool.util.Breadcrumbs
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.TranscodeStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Units of measurement for file size filtering. */
enum class SizeUnit { KB, MB, GB }

/**
 * Filter criteria for detected candidate file sizes.
 *
 * @param value Raw text input representing the numeric threshold.
 * @param unit Unit multiplier for [value].
 */
data class SizeFilter(
    val value: String = "",
    val unit: SizeUnit = SizeUnit.MB
)

/**
 * User-facing status filter options for the download list.
 *
 * @param displayName Human-readable label displayed in the filter dropdown.
 */
enum class DownloadFilterStatus(val displayName: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    TRANSCODING("Transcoding"),
    TRANSCODED("Transcoded"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

/**
 * Checks whether this [DownloadItem] matches the given [DownloadFilterStatus].
 */
fun DownloadItem.matchesFilterStatus(filterStatus: DownloadFilterStatus): Boolean {
    return when (filterStatus) {
        DownloadFilterStatus.QUEUED -> this.status == DownloadStatus.QUEUED
        DownloadFilterStatus.DOWNLOADING -> this.status == DownloadStatus.DOWNLOADING
        DownloadFilterStatus.TRANSCODING -> this.transcodeStatus == TranscodeStatus.TRANSCODING
        DownloadFilterStatus.TRANSCODED -> this.status == DownloadStatus.COMPLETED &&
            (this.transcodeStatus == TranscodeStatus.COMPLETED || this.transcodeStatus == TranscodeStatus.ALREADY_H265)
        DownloadFilterStatus.COMPLETED -> this.status == DownloadStatus.COMPLETED && this.transcodeStatus != TranscodeStatus.TRANSCODING
        DownloadFilterStatus.FAILED -> this.status == DownloadStatus.FAILED || this.transcodeStatus == TranscodeStatus.FAILED
        DownloadFilterStatus.CANCELLED -> this.status == DownloadStatus.CANCELLED
    }
}

/**
 * Represents a discrete 1-hour time bucket containing download items.
 *
 * @param hourStartEpochMillis Epoch timestamp representing the start of the 1-hour window.
 * @param title Human-readable date and hour window (e.g., "Today, 7:00 PM - 8:00 PM").
 * @param items List of downloads belonging to this hour bucket, sorted newest first.
 * @param isExpanded Whether the group's items are currently visible in the UI.
 */
data class DownloadHourGroup(
    val hourStartEpochMillis: Long,
    val title: String,
    val items: List<DownloadItem>,
    val isExpanded: Boolean
)

/**
 * Groups and filters downloads into discrete 1-hour time buckets.
 *
 * @param downloads List of downloads to group, expected newest first.
 * @param statusFilters Set of active status filters. Empty = show all.
 * @param userToggledGroups Map of user expansion overrides per hour bucket.
 * @param zoneId Time zone used to evaluate local dates and hours.
 * @param clock Supplier for the current epoch timestamp in milliseconds.
 */
fun buildHourGroups(
    downloads: List<DownloadItem>,
    statusFilters: Set<DownloadFilterStatus>,
    userToggledGroups: Map<Long, Boolean>,
    zoneId: ZoneId,
    clock: () -> Long
): List<DownloadHourGroup> {
    val filtered = if (statusFilters.isEmpty()) {
        downloads
    } else {
        downloads.filter { item ->
            statusFilters.any { item.matchesFilterStatus(it) }
        }
    }

    if (filtered.isEmpty()) return emptyList()

    val groupedMap = LinkedHashMap<Long, MutableList<DownloadItem>>()
    for (item in filtered) {
        val zdt = Instant.ofEpochMilli(item.createdAt).atZone(zoneId)
        val hourStartMillis = zdt.truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()
        groupedMap.getOrPut(hourStartMillis) { mutableListOf() }.add(item)
    }

    val nowLocalDate = Instant.ofEpochMilli(clock()).atZone(zoneId).toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    var index = 0
    return groupedMap.map { (hourStartMillis, items) ->
        val hourStartZdt = Instant.ofEpochMilli(hourStartMillis).atZone(zoneId)
        val hourEndZdt = hourStartZdt.plusHours(1)
        val itemLocalDate = hourStartZdt.toLocalDate()
        val startTime = hourStartZdt.format(timeFormatter)
        val endTime = hourEndZdt.format(timeFormatter)
        val timeRange = "$startTime - $endTime"

        val datePrefix = when {
            itemLocalDate == nowLocalDate -> "Today"
            itemLocalDate == nowLocalDate.minusDays(1) -> "Yesterday"
            itemLocalDate.year == nowLocalDate.year -> itemLocalDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
            else -> itemLocalDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        }

        val title = "$datePrefix, $timeRange"
        val isExpanded = userToggledGroups[hourStartMillis] ?: (index == 0)
        index++

        DownloadHourGroup(
            hourStartEpochMillis = hourStartMillis,
            title = title,
            items = items,
            isExpanded = isExpanded
        )
    }
}

/**
 * ViewModel managing the video downloader state: URL input, media detection observation,
 * user selection, background download scheduling, and download history management.
 */
class VideoDownloaderViewModel(
    private val downloadRepository: DownloadRepository,
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    val detector: WebViewMediaDetector = WebViewMediaDetector()
) : ViewModel() {

    /** Reactive stream of all download records. */
    val downloads: StateFlow<List<DownloadItem>> = downloadRepository.allDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedStatusFilters = MutableStateFlow<Set<DownloadFilterStatus>>(emptySet())
    /** Set of active status filters for the download list screen. Empty = show all. */
    val selectedStatusFilters: StateFlow<Set<DownloadFilterStatus>> = _selectedStatusFilters.asStateFlow()

    private val _userToggledGroups = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    /** User manual expand/collapse overrides for hour groups. */
    val userToggledGroups: StateFlow<Map<Long, Boolean>> = _userToggledGroups.asStateFlow()

    /**
     * Download records grouped into 1-hour time buckets, filtered by [selectedStatusFilters].
     */
    val groupedDownloads: StateFlow<List<DownloadHourGroup>> = combine(
        downloads, _selectedStatusFilters, _userToggledGroups
    ) { allDownloads, statusFilters, toggledGroups ->
        buildHourGroups(allDownloads, statusFilters, toggledGroups, zoneId, clock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUrl = MutableStateFlow("")
    /** Currently entered web page URL. */
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _showSelectionDialog = MutableStateFlow(false)
    /** Whether the media selection sheet/dialog is currently visible. */
    val showSelectionDialog: StateFlow<Boolean> = _showSelectionDialog.asStateFlow()

    private val _selectedMedia = MutableStateFlow<Set<DetectedMedia>>(emptySet())
    /** Currently selected media items in the selection sheet. */
    val selectedMedia: StateFlow<Set<DetectedMedia>> = _selectedMedia.asStateFlow()

    private val _typeFilter = MutableStateFlow<Set<MediaType>>(emptySet())
    /** Set of MediaType values currently included by the type filter. Empty = show all. */
    val typeFilter: StateFlow<Set<MediaType>> = _typeFilter.asStateFlow()

    private val _sizeFilter = MutableStateFlow(SizeFilter())
    /** Active candidate size threshold filter. */
    val sizeFilter: StateFlow<SizeFilter> = _sizeFilter.asStateFlow()

    private fun currentFilteredMedia(): List<DetectedMedia> {
        val all = detector.detectedMedia.value
        val types = _typeFilter.value
        val size = _sizeFilter.value
        val minBytes = size.value.toDoubleOrNull()?.let { v ->
            when (size.unit) {
                SizeUnit.KB -> (v * 1024).toLong()
                SizeUnit.MB -> (v * 1024 * 1024).toLong()
                SizeUnit.GB -> (v * 1024 * 1024 * 1024).toLong()
            }
        }
        return all.filter { media ->
            val passType = types.isEmpty() || media.mediaType in types
            val passSize = minBytes == null
                || media.estimatedSizeBytes < 0L
                || media.estimatedSizeBytes >= minBytes
            passType && passSize
        }
    }

    /** Derived filtered media candidates combining type and minimum size filters. */
    val filteredMedia: StateFlow<List<DetectedMedia>> = combine(
        detector.detectedMedia, _typeFilter, _sizeFilter
    ) { _, _, _ ->
        currentFilteredMedia()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Grouped and flattened candidate list for UI rendering, grouping duplicates by file name.
     */
    val groupedFilteredMedia: StateFlow<List<MediaListEntry>> = filteredMedia.map { list ->
        val grouped = list.groupBy { it.suggestedFileName }
        buildList {
            grouped.forEach { (name, items) ->
                if (items.size > 1) {
                    add(MediaListEntry.GroupHeader(name, items.size))
                    items.forEach { add(MediaListEntry.Item(it, isGroupChild = true)) }
                } else {
                    add(MediaListEntry.Item(items.first(), isGroupChild = false))
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _existingDownloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    /** Map from mediaUrl -> completed DownloadItem for candidates in the current page. */
    val existingDownloads: StateFlow<Map<String, DownloadItem>> = _existingDownloads.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 5)
    /** Shared flow of download lifecycle toast messages. */
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val toastController = DownloadToastController(viewModelScope, downloadRepository.allDownloads())

    init {
        viewModelScope.launch {
            toastController.toastMessage.collect { msg ->
                _toastMessage.tryEmit(msg)
            }
        }
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
        viewModelScope.launch {
            detector.activeUrl.collect { newUrl ->
                if (newUrl.isNotBlank() && newUrl != "about:blank") {
                    _currentUrl.value = newUrl
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
        viewModelScope.launch(ioDispatcher) {
            try {
                val urls = detector.detectedMedia.value.map { it.url }
                _existingDownloads.value = downloadRepository.findExistingCompletedByUrls(urls)
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
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

    /**
     * Selects media items from the current filtered list, selecting only the candidate
     * with the largest file size for each media group (grouped by suggested file name).
     */
    fun selectAll() {
        val candidates = currentFilteredMedia()
        _selectedMedia.value = candidates
            .groupBy { it.suggestedFileName }
            .values
            .mapNotNull { items -> items.maxByOrNull { it.estimatedSizeBytes } }
            .toSet()
    }

    /**
     * Selects non-existing media items from the current filtered list, selecting only the
     * candidate with the largest file size for each media group.
     */
    fun selectAllExceptExisting() {
        val candidates = currentFilteredMedia().filter { it.url !in _existingDownloads.value }
        _selectedMedia.value = candidates
            .groupBy { it.suggestedFileName }
            .values
            .mapNotNull { items -> items.maxByOrNull { it.estimatedSizeBytes } }
            .toSet()
    }

    /** Deselects all items in the selection sheet. */
    fun deselectAll() {
        _selectedMedia.value = emptySet()
    }

    /**
     * Toggles inclusion of a [MediaType] in the candidate filter.
     * When [allAvailableTypes] is provided and all types were active (empty filter),
     * toggling unchecks [type] from [allAvailableTypes].
     */
    fun toggleTypeFilter(type: MediaType, allAvailableTypes: Set<MediaType> = emptySet()) {
        val current = _typeFilter.value

        if (current.isEmpty() && allAvailableTypes.isNotEmpty() && allAvailableTypes.contains(type)) {
            _typeFilter.value = allAvailableTypes - type
        } else {
            val updated = current.toMutableSet()
            if (updated.contains(type)) {
                if (allAvailableTypes.isEmpty() || updated.size > 1) {
                    updated.remove(type)
                }
            } else {
                updated.add(type)
            }
            if (allAvailableTypes.isNotEmpty() && updated.containsAll(allAvailableTypes)) {
                _typeFilter.value = emptySet()
            } else {
                _typeFilter.value = updated
            }
        }
    }

    /** Resets the media type filter to show all types. */
    fun clearTypeFilter() {
        _typeFilter.value = emptySet()
    }

    /** Updates the minimum size filter value and measurement unit. */
    fun updateSizeFilter(value: String, unit: SizeUnit) {
        _sizeFilter.value = SizeFilter(value, unit)
    }

    /** Clears all candidate filters. */
    fun clearFilters() {
        _typeFilter.value = emptySet()
        _sizeFilter.value = SizeFilter()
    }

    /**
     * Toggles inclusion of a [DownloadFilterStatus] in the status filter set.
     */
    fun toggleStatusFilter(status: DownloadFilterStatus) {
        val current = _selectedStatusFilters.value.toMutableSet()
        if (current.contains(status)) {
            current.remove(status)
        } else {
            current.add(status)
        }
        _selectedStatusFilters.value = current
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "Status filter toggled",
            data = mapOf("status" to status.name, "activeCount" to current.size)
        )
    }

    /**
     * Clears all active status filters, showing all download items.
     */
    fun clearStatusFilters() {
        _selectedStatusFilters.value = emptySet()
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "Status filters cleared",
            data = emptyMap()
        )
    }

    /**
     * Toggles the collapsed/expanded state of a specific hour group.
     *
     * @param hourStartMillis The start epoch timestamp of the hour group to toggle.
     */
    fun toggleHourGroupExpansion(hourStartMillis: Long) {
        val groups = groupedDownloads.value
        val targetGroup = groups.find { it.hourStartEpochMillis == hourStartMillis }
        val currentExpanded = targetGroup?.isExpanded ?: false
        val updated = _userToggledGroups.value.toMutableMap()
        updated[hourStartMillis] = !currentExpanded
        _userToggledGroups.value = updated
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_DOWNLOADER,
            message = "Hour group expansion toggled",
            data = mapOf("hourStartMillis" to hourStartMillis, "expanded" to !currentExpanded)
        )
    }

    /**
     * Enqueues all selected media items into the repository and schedules their downloads.
     */
    fun downloadSelected() {
        val selected = _selectedMedia.value.toList()
        if (selected.isEmpty()) return

        val count = selected.size
        _toastMessage.tryEmit("$count download${if (count != 1) "s" else ""} queued")

        val sourceUrl = _currentUrl.value

        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
            try {
                downloadRepository.clearCompleted()
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Clears all download records from history without deleting local files.
     */
    fun clearAll() {
        viewModelScope.launch(ioDispatcher) {
            try {
                downloadRepository.clearAll()
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Deletes all download records AND removes all local files from disk.
     */
    fun deleteAll() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val allItems = downloadRepository.allDownloads().first()
                allItems.forEach { item ->
                    item.localFilePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
                downloadRepository.clearAll()
                Breadcrumbs.record(
                    category = Breadcrumbs.CAT_DOWNLOADER,
                    message = "Delete All executed",
                    data = mapOf("count" to allItems.size)
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Scans all COMPLETED downloads with transcodeStatus=PENDING (and resets stuck/failed ones)
     * and schedules the first one for H265 conversion if no transcode is currently running.
     */
    fun triggerTranscodeForExistingIfNeeded() {
        viewModelScope.launch(ioDispatcher) {
            try {
                downloadRepository.resetFailedAndOrphanedTranscodes()
                com.msahil432.multitool.downloader.H265TranscodeWorker.scheduleNextTranscodeIfAvailable(appContext)
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Retries transcoding for a completed download that previously failed or was interrupted.
     */
    fun retryTranscode(downloadId: Long) {
        viewModelScope.launch(ioDispatcher) {
            try {
                downloadRepository.retryTranscode(downloadId)
                com.msahil432.multitool.downloader.H265TranscodeWorker.schedule(appContext, downloadId)
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
        toastController.close()
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
