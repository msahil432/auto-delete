package com.msahil432.multitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * [AccessibilityHandler] that detects when a user opens short-form video feeds
 * (such as YouTube Shorts, Instagram Reels, and Facebook Reels) and dismisses them
 * while preserving access to the rest of the host applications.
 */
class ShortFormHandler(
    private val settingsRepository: SettingsRepository,
    private val blockingRepository: BlockingRepository,
    private val usageRepository: UsageRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val recheckDelayMs: Long = 500L,
    private val cooldownMs: Long = 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) : AccessibilityHandler {

    @Volatile
    internal var blockYtShorts: Boolean = false
        private set

    @Volatile
    internal var blockIgReels: Boolean = false
        private set

    @Volatile
    internal var blockFbReels: Boolean = false
        private set

    @Volatile
    private var lastActionTime: Long = 0L

    init {
        coroutineScope.launch {
            settingsRepository.blockYtShorts.collectLatest { enabled ->
                blockYtShorts = enabled
            }
        }
        coroutineScope.launch {
            settingsRepository.blockIgReels.collectLatest { enabled ->
                blockIgReels = enabled
            }
        }
        coroutineScope.launch {
            settingsRepository.blockFbReels.collectLatest { enabled ->
                blockFbReels = enabled
            }
        }
    }

    override fun onEvent(svc: AccessibilityService, e: AccessibilityEvent) {
        val pkg = e.packageName?.toString()
            ?: try { svc.rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
            ?: return

        if (!ShortFormSignatures.isSupportedPackage(pkg)) return
        if (!isBlockingEnabledForPackage(pkg)) return

        if (isShortFormFeed(svc, pkg, e)) {
            val now = clock()
            if (now - lastActionTime < cooldownMs) {
                return
            }
            lastActionTime = now

            // 1. Perform BACK action to dismiss short-form feed
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

            // 2. Log interception to blocking repository and usage timeline
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    blockingRepository.logInterception(
                        packageName = pkg,
                        ruleId = 0L,
                        ruleType = BlockRuleType.SCHEDULE
                    )
                    usageRepository.recordTimeline(pkg, TimelineEventType.BLOCK_INTERCEPT)
                } catch (_: Exception) {}
            }

            // 3. Recheck feed after delay; if still stuck in feed, route to home
            coroutineScope.launch {
                delay(recheckDelayMs)
                if (isShortFormFeed(svc, pkg, null)) {
                    routeToHome(svc)
                }
            }
        }
    }

    /**
     * Checks if the active window or event matches known short-form UI signatures.
     */
    internal fun isShortFormFeed(
        svc: AccessibilityService,
        pkg: String,
        event: AccessibilityEvent?
    ): Boolean {
        // 1. Check event class name against known Shorts/Reels activity or fragment classes
        val className = event?.className?.toString()
        if (!className.isNullOrBlank()) {
            val knownClasses = ShortFormSignatures.SHORT_FORM_CLASSES[pkg]
            if (knownClasses?.any { className.contains(it, ignoreCase = true) } == true) {
                return true
            }
        }

        // 2. Check root node in active window for view-id signatures
        val rootNode: AccessibilityNodeInfo? = try {
            svc.rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        if (rootNode == null) return false

        val signatures = ShortFormSignatures.SHORT_FORM[pkg] ?: return false
        for (sig in signatures) {
            val fullViewId = "$pkg:id/$sig"
            val matchingNodes = try {
                rootNode.findAccessibilityNodeInfosByViewId(fullViewId)
            } catch (_: Exception) {
                emptyList()
            }

            if (!matchingNodes.isNullOrEmpty()) {
                return true
            }
        }

        return false
    }

    /**
     * Returns true if short-form blocking is toggled ON for the specified package.
     */
    fun isBlockingEnabledForPackage(pkg: String): Boolean {
        return when (pkg) {
            ShortFormSignatures.PKG_YOUTUBE -> blockYtShorts
            ShortFormSignatures.PKG_INSTAGRAM -> blockIgReels
            ShortFormSignatures.PKG_FACEBOOK -> blockFbReels
            else -> false
        }
    }

    private fun routeToHome(svc: AccessibilityService) {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            svc.startActivity(homeIntent)
        } catch (_: Exception) {
            try {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            } catch (_: Exception) {}
        }
    }
}
