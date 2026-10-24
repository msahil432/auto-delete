package com.msahil432.multitool.downloader

import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Observes download lifecycle changes and emits throttled human-readable status messages
 * suitable for transient toast notifications.
 *
 * Rules:
 * - "All downloads completed" emits immediately without throttling once all active downloads finish.
 * - Progress/failure update messages emit immediately on first occurrence, then are throttled for [throttleMs].
 * - If state changes while throttled, the latest message is dispatched once the throttle window elapses.
 */
class DownloadToastController(
    scope: CoroutineScope,
    private val downloads: Flow<List<DownloadItem>>,
    private val throttleMs: Long = 15_000L,
    private val clock: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val controllerScope = CoroutineScope(scope.coroutineContext + job)

    init {
        controllerScope.launch {
            var lastToastTime = 0L
            var pendingMessage: String? = null
            var lastActiveCount = 0
            var allCompletedEmitted = false
            var flushJob: Job? = null

            downloads
                .map { list ->
                    val active = list.count {
                        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
                    }
                    val failedItem = list.firstOrNull { it.status == DownloadStatus.FAILED }
                    val allDone = list.isNotEmpty() && active == 0
                    Triple(active, failedItem, allDone)
                }
                .distinctUntilChanged()
                .collect { (active, failedItem, allDone) ->
                    val now = clock()

                    // Rule: "All completed" fires immediately, once per active cycle
                    if (allDone && !allCompletedEmitted && lastActiveCount > 0) {
                        flushJob?.cancel()
                        flushJob = null
                        pendingMessage = null
                        _toastMessage.tryEmit("All downloads completed")
                        allCompletedEmitted = true
                        lastToastTime = now
                        lastActiveCount = 0
                        return@collect
                    }

                    if (active > 0) {
                        allCompletedEmitted = false
                    } else {
                        flushJob?.cancel()
                        flushJob = null
                        pendingMessage = null
                    }

                    val message: String? = when {
                        failedItem != null && active > 0 ->
                            "${failedItem.fileName} failed, $active download${if (active != 1) "s" else ""} ongoing"
                        active > 0 ->
                            "$active download${if (active != 1) "s" else ""} remaining"
                        else -> null
                    }

                    if (message != null) {
                        val elapsed = now - lastToastTime
                        if (elapsed >= throttleMs || lastToastTime == 0L) {
                            flushJob?.cancel()
                            flushJob = null
                            pendingMessage = null
                            _toastMessage.tryEmit(message)
                            lastToastTime = now
                        } else {
                            pendingMessage = message
                            if (flushJob == null || !flushJob!!.isActive) {
                                val remainingMs = (throttleMs - elapsed).coerceAtLeast(1L)
                                flushJob = controllerScope.launch {
                                    delay(remainingMs)
                                    val emitTime = clock()
                                    pendingMessage?.let {
                                        _toastMessage.tryEmit(it)
                                        lastToastTime = emitTime
                                        pendingMessage = null
                                    }
                                }
                            }
                        }
                    }
                    lastActiveCount = active
                }
        }
    }

    override fun close() {
        job.cancel()
    }
}
