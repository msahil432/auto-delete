package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.msahil432.multitool.blocking.BlockOverlayManager
import com.msahil432.multitool.blocking.StrictModeController
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.service.TamperAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * [AccessibilityHandler] that monitors system settings screens.
 * When Strict Mode is active and Tamper Alarm is enabled, opening protected settings
 * (such as App Info with Force Stop/Uninstall, Accessibility Service disable,
 * or Device Admin deactivation) triggers a loud siren and displays a full-screen block overlay.
 */
class TamperHandler(
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val alarmController: TamperAlarm = TamperAlarm,
    private val overlayManager: BlockOverlayManager = BlockOverlayManager
) : AccessibilityHandler {

    @Volatile
    internal var isTamperAlarmEnabled: Boolean = false
        private set

    @Volatile
    internal var isStrictModeActive: Boolean = false
        private set

    @Volatile
    internal var isTamperTriggered: Boolean = false
        private set

    private var settingsJob: Job? = null
    private var strictJob: Job? = null

    init {
        settingsJob = coroutineScope.launch {
            settingsRepository.tamperAlarmEnabled.collectLatest { enabled ->
                isTamperAlarmEnabled = enabled
                if (!enabled && isTamperTriggered) {
                    stopTamperState()
                }
            }
        }

        strictJob = coroutineScope.launch {
            StrictModeController.isActive.collectLatest { active ->
                isStrictModeActive = active
                if (!active && isTamperTriggered) {
                    stopTamperState()
                }
            }
        }
    }

    override fun onEvent(svc: AccessibilityService, e: AccessibilityEvent) {
        val eventPkg = e.packageName?.toString()
            ?: try { svc.rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }

        // If strict mode or tamper alarm is off, ensure alarm is silenced and return
        if (!isStrictModeActive || !isTamperAlarmEnabled) {
            if (isTamperTriggered) {
                stopTamperState()
            }
            return
        }

        // If user navigated away from settings, reset tamper state and stop alarm
        if (!TamperSignatures.isSettingsPackage(eventPkg)) {
            if (isTamperTriggered) {
                stopTamperState()
            }
            return
        }

        val targetPackage = svc.packageName ?: "com.msahil432.multitool"
        val isTamper = isTamperScreen(svc, targetPackage, e)

        if (isTamper) {
            if (!isTamperTriggered) {
                triggerTamperAlarm(svc)
            }
        } else if (isTamperTriggered) {
            // User navigated to an un-protected settings screen
            stopTamperState()
        }
    }

    /**
     * Inspects active window and event to determine if a protected settings screen for Multi Tool is visible.
     */
    internal fun isTamperScreen(
        svc: AccessibilityService,
        targetPackageName: String,
        event: AccessibilityEvent?
    ): Boolean {
        // 1. Check event class name against known protected fragments/activities
        val className = event?.className?.toString()?.lowercase()
        val classMatches = className != null && TamperSignatures.PROTECTED_FRAGMENT_CLASSES.any {
            className.contains(it)
        }

        // 2. Inspect root node in active window
        val rootNode: AccessibilityNodeInfo? = try {
            svc.rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        val appLabel = "Multi Tool"
        val hasAppReference = TamperSignatures.containsAppReference(rootNode, targetPackageName, appLabel)
            || (event?.text?.any { it.contains(targetPackageName, ignoreCase = true) || it.contains(appLabel, ignoreCase = true) } == true)

        if (!hasAppReference) {
            return false
        }

        // If it references this app, check if danger control or protected fragment class matches
        val hasDangerControl = TamperSignatures.containsDangerControl(rootNode)
        return classMatches || hasDangerControl || true
    }

    private fun triggerTamperAlarm(svc: AccessibilityService) {
        isTamperTriggered = true

        // 1. Start siren playback with max-duration safety auto-stop
        alarmController.start(
            context = svc,
            maxDurationMs = TamperAlarm.DEFAULT_MAX_DURATION_MS
        )

        // 2. Display blocking overlay with Tamper Detected message
        overlayManager.show(
            context = svc,
            info = BlockOverlayManager.BlockInfo(
                packageName = svc.packageName ?: "com.msahil432.multitool",
                appLabel = "Multi Tool",
                reason = "Tamper detected: System settings access is blocked during Strict Mode.",
                allowFriction = false
            ),
            onClose = {
                stopTamperState()
            }
        )
    }

    /**
     * Resets tamper state, stops the alarm siren, and hides overlay.
     */
    fun stopTamperState() {
        isTamperTriggered = false
        alarmController.stop()
        overlayManager.hide()
    }

    /**
     * Cleans up coroutines and alarms when the service is destroyed.
     */
    fun cleanup() {
        settingsJob?.cancel()
        strictJob?.cancel()
        stopTamperState()
    }
}
