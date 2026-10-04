package com.msahil432.multitool.blocking

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.msahil432.multitool.accessibility.ForegroundAppState
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Observes [ForegroundAppState] and evaluates blocking rules via [BlockEngine],
 * updates launch/usage counters in real time, and manages [BlockOverlayManager]
 * and [FloatingTimerBubbleManager] displays.
 */
class BlockEnforcementController(
    private val scope: CoroutineScope,
    private val foregroundState: StateFlow<String> = ForegroundAppState.currentPackage,
    private val engine: BlockEngine,
    private val blockingRepo: BlockingRepository,
    private val usageRepo: UsageRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isStrictAllowFriction: (suspend () -> Boolean) = { false },
    private val settingsRepo: SettingsRepository? = null,
    private val isFloatingBubbleEnabled: (suspend () -> Boolean)? = null
) {

    private var monitoringJob: Job? = null
    private var periodicJob: Job? = null
    private var timerTickerJob: Job? = null
    private var activeTickerPkg: String? = null
    private val dismissedPackagesInSession = mutableSetOf<String>()
    private var currentBlockedPkg: String? = null
    private var sessionStartTime: Long = 0L
    private var lastTickTime: Long = 0L
    private var activePkg: String = ""

    /**
     * Starts listening to foreground app changes and periodic session tracking.
     */
    fun start(context: Context) {
        stop()
        Sentry.addBreadcrumb("Block enforcement started")

        val appContext = context.applicationContext

        monitoringJob = scope.launch {
            foregroundState.collectLatest { newPkg ->
                handlePackageChanged(appContext, newPkg)
            }
        }

        periodicJob = scope.launch {
            while (isActive) {
                delay(5000L)
                handlePeriodicTick(appContext)
            }
        }
    }

    /**
     * Stops monitoring and releases jobs.
     */
    fun stop() {
        monitoringJob?.cancel()
        periodicJob?.cancel()
        stopTimerTicker()
        FloatingTimerBubbleManager.hide()
        monitoringJob = null
        periodicJob = null
        currentBlockedPkg = null
        activePkg = ""
        dismissedPackagesInSession.clear()
    }

    private suspend fun handlePackageChanged(context: Context, newPkg: String) {
        try {
            val prevPkg = activePkg
            val now = clock()
            Sentry.addBreadcrumb("BlockEnforcementCtrl: handlePackageChanged newPkg=$newPkg (prev was $prevPkg)")

            if (prevPkg.isNotBlank() && prevPkg != newPkg) {
                val sessionElapsed = (now - lastTickTime).coerceAtLeast(0L)
                if (sessionElapsed > 0) {
                    updateForegroundDuration(prevPkg, sessionElapsed)
                }
                dismissedPackagesInSession.remove(prevPkg)
                stopTimerTicker()
                FloatingTimerBubbleManager.hide()
            }

            activePkg = newPkg
            sessionStartTime = now
            lastTickTime = now

            if (newPkg.isBlank() || newPkg == context.packageName) {
                if (BlockOverlayManager.isShowing()) {
                    BlockOverlayManager.hide()
                }
                FloatingTimerBubbleManager.hide()
                stopTimerTicker()
                currentBlockedPkg = null
                return
            }

            // Increment launch counter for matching enabled groups
            val groups = blockingRepo.enabledGroupsContaining(newPkg)
            for (g in groups) {
                val counter = blockingRepo.counterForToday(g.id)
                blockingRepo.upsertCounter(counter.copy(launchesUsed = counter.launchesUsed + 1))
            }

            // Record launch in usage stats
            usageRepo.recordLaunch(newPkg)

            // Evaluate rule condition
            evaluateAndEnforce(context, newPkg)
        } catch (e: Exception) {
            Log.e("BlockEnforcementCtrl", "Error in handlePackageChanged for $newPkg", e)
            Sentry.captureException(e)
        }
    }

    private suspend fun handlePeriodicTick(context: Context) {
        val currentPkg = activePkg
        if (currentPkg.isBlank() || currentPkg == context.packageName) return

        val now = clock()
        val elapsed = (now - lastTickTime).coerceAtLeast(0L)
        lastTickTime = now

        if (elapsed > 0) {
            updateForegroundDuration(currentPkg, elapsed)
        }

        val sessionDuration = (now - sessionStartTime).coerceAtLeast(0L)
        val groups = blockingRepo.enabledGroupsContaining(currentPkg)
        for (g in groups) {
            val rules = blockingRepo.enabledRules(g.id)
            for (rule in rules) {
                if (rule.type == BlockRuleType.SESSION_LIMIT && rule.maxSessionMinutes > 0) {
                    if (sessionDuration >= rule.maxSessionMinutes * 60_000L) {
                        val counter = blockingRepo.counterForToday(g.id)
                        val lockout = now + (rule.cooldownMinutes * 60_000L)
                        blockingRepo.upsertCounter(counter.copy(lockedUntil = lockout))
                    }
                }
            }
        }

        evaluateAndEnforce(context, currentPkg)
    }

    private suspend fun updateForegroundDuration(pkg: String, elapsedMillis: Long) {
        val groups = blockingRepo.enabledGroupsContaining(pkg)
        for (g in groups) {
            val counter = blockingRepo.counterForToday(g.id)
            blockingRepo.upsertCounter(
                counter.copy(usedForegroundMillis = counter.usedForegroundMillis + elapsedMillis)
            )
        }
        usageRepo.recordForeground(pkg, elapsedMillis)
    }

    private suspend fun evaluateAndEnforce(context: Context, pkg: String) {
        val decision = engine.evaluate(pkg)
        when (decision) {
            is Blocked -> {
                FloatingTimerBubbleManager.hide()
                stopTimerTicker()

                if (currentBlockedPkg != pkg || !BlockOverlayManager.isShowing()) {
                    currentBlockedPkg = pkg
                    val appLabel = resolveAppLabel(context, pkg)
                    val allowFriction = isStrictAllowFriction()

                    val blockInfo = BlockOverlayManager.BlockInfo(
                        packageName = pkg,
                        appLabel = appLabel,
                        reason = decision.reason,
                        allowFriction = allowFriction,
                        usedSeconds = decision.usedSeconds,
                        limitSeconds = decision.limitSeconds,
                        endsAtMillis = decision.endsAtMillis
                    )

                    BlockOverlayManager.show(
                        context = context,
                        info = blockInfo,
                        onClose = {
                            currentBlockedPkg = null
                        },
                        onFriction = {
                            currentBlockedPkg = null
                        }
                    )

                    // Log interception in database
                    blockingRepo.logInterception(
                        packageName = pkg,
                        ruleId = decision.rule.id,
                        ruleType = decision.rule.type
                    )

                    // Record timeline event
                    usageRepo.recordTimeline(pkg, TimelineEventType.BLOCK_INTERCEPT)
                }
            }
            is Allowed -> {
                if (currentBlockedPkg == pkg || BlockOverlayManager.isShowing()) {
                    BlockOverlayManager.hide()
                    currentBlockedPkg = null
                }
                updateFloatingBubble(context, pkg)
            }
        }
    }

    private suspend fun updateFloatingBubble(context: Context, pkg: String) {
        try {
            if (pkg.isBlank() || pkg == context.packageName || dismissedPackagesInSession.contains(pkg)) {
                Sentry.addBreadcrumb("BlockEnforcementCtrl: Hiding bubble (pkg=$pkg, dismissed=${dismissedPackagesInSession.contains(pkg)})")
                FloatingTimerBubbleManager.hide()
                stopTimerTicker()
                return
            }

            if (!isBubbleEnabled()) {
                Sentry.addBreadcrumb("BlockEnforcementCtrl: Floating timer bubble disabled in settings")
                FloatingTimerBubbleManager.hide()
                stopTimerTicker()
                return
            }

            val timerInfo = calculateActiveTimer(context, pkg)
            if (timerInfo != null && timerInfo.remainingMillis > 0) {
                Sentry.addBreadcrumb("BlockEnforcementCtrl: Showing bubble for $pkg (remaining=${timerInfo.remainingMillis}ms, rule=${timerInfo.ruleType})")
                FloatingTimerBubbleManager.showOrUpdate(
                    context = context,
                    info = timerInfo,
                    onDismiss = {
                        Sentry.addBreadcrumb("BlockEnforcementCtrl: Bubble dismissed for session: $pkg")
                        dismissedPackagesInSession.add(pkg)
                        stopTimerTicker()
                    }
                )
                startTimerTicker(context, pkg)
            } else {
                Sentry.addBreadcrumb("BlockEnforcementCtrl: No active timer for $pkg (info=$timerInfo) - hiding bubble")
                FloatingTimerBubbleManager.hide()
                stopTimerTicker()
            }
        } catch (e: Exception) {
            Log.e("BlockEnforcementCtrl", "Error in updateFloatingBubble for $pkg", e)
            Sentry.captureException(e)
        }
    }

    private fun startTimerTicker(context: Context, pkg: String) {
        if (timerTickerJob?.isActive == true && activeTickerPkg == pkg) return
        stopTimerTicker()
        activeTickerPkg = pkg
        Sentry.addBreadcrumb("BlockEnforcementCtrl: Started timer ticker for $pkg")

        timerTickerJob = scope.launch {
            try {
                while (isActive && activePkg == pkg && !dismissedPackagesInSession.contains(pkg)) {
                    delay(1000L)
                    val updatedInfo = calculateActiveTimer(context, pkg)
                    if (updatedInfo != null && updatedInfo.remainingMillis > 0) {
                        FloatingTimerBubbleManager.showOrUpdate(
                            context = context,
                            info = updatedInfo,
                            onDismiss = {
                                Sentry.addBreadcrumb("BlockEnforcementCtrl: Bubble dismissed for session: $pkg")
                                dismissedPackagesInSession.add(pkg)
                                stopTimerTicker()
                            }
                        )
                    } else if (updatedInfo != null && updatedInfo.remainingMillis <= 0) {
                        Sentry.addBreadcrumb("BlockEnforcementCtrl: Timer expired (0s) for $pkg - triggering enforcement")
                        FloatingTimerBubbleManager.hide()
                        evaluateAndEnforce(context, pkg)
                        break
                    } else {
                        Sentry.addBreadcrumb("BlockEnforcementCtrl: No timer active for $pkg during tick - hiding bubble")
                        FloatingTimerBubbleManager.hide()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("BlockEnforcementCtrl", "Error during timer ticker for $pkg", e)
                Sentry.captureException(e)
            }
        }
    }

    private fun stopTimerTicker() {
        if (activeTickerPkg != null) {
            Sentry.addBreadcrumb("BlockEnforcementCtrl: Stopped timer ticker for $activeTickerPkg")
        }
        timerTickerJob?.cancel()
        timerTickerJob = null
        activeTickerPkg = null
    }

    private suspend fun isBubbleEnabled(): Boolean {
        return when {
            isFloatingBubbleEnabled != null -> isFloatingBubbleEnabled.invoke()
            settingsRepo != null -> settingsRepo.showFloatingTimerBubble.first()
            else -> true
        }
    }

    /**
     * Calculates the active timer state with least remaining time for [pkg].
     * Returns [AppTimerInfo] if an enabled timer rule (Daily Quota or Session Limit) is active, or null otherwise.
     */
    suspend fun calculateActiveTimer(context: Context, pkg: String): AppTimerInfo? {
        if (pkg.isBlank() || pkg == context.packageName) return null

        val now = clock()
        val currentLiveSession = if (activePkg == pkg) (now - sessionStartTime).coerceAtLeast(0L) else 0L
        val currentLiveSinceTick = if (activePkg == pkg) (now - lastTickTime).coerceAtLeast(0L) else 0L
        val groups = blockingRepo.enabledGroupsContaining(pkg)
        var shortestTimer: AppTimerInfo? = null

        for (group in groups) {
            val counter = blockingRepo.counterForToday(group.id)
            val rules = blockingRepo.enabledRules(group.id)

            for (rule in rules) {
                when (rule.type) {
                    BlockRuleType.DAILY_QUOTA -> {
                        if (rule.dailyQuotaMinutes > 0) {
                            val limitMillis = rule.dailyQuotaMinutes * 60_000L
                            val totalUsed = counter.usedForegroundMillis + currentLiveSinceTick
                            val remaining = (limitMillis - totalUsed).coerceAtLeast(0L)
                            val timerInfo = AppTimerInfo(
                                packageName = pkg,
                                appLabel = resolveAppLabel(context, pkg),
                                ruleType = BlockRuleType.DAILY_QUOTA,
                                remainingMillis = remaining,
                                totalLimitMillis = limitMillis,
                                groupName = group.name
                            )
                            if (shortestTimer == null || remaining < shortestTimer.remainingMillis) {
                                shortestTimer = timerInfo
                            }
                        }
                    }
                    BlockRuleType.SESSION_LIMIT -> {
                        if (rule.maxSessionMinutes > 0) {
                            val limitMillis = rule.maxSessionMinutes * 60_000L
                            val remaining = (limitMillis - currentLiveSession).coerceAtLeast(0L)
                            val timerInfo = AppTimerInfo(
                                packageName = pkg,
                                appLabel = resolveAppLabel(context, pkg),
                                ruleType = BlockRuleType.SESSION_LIMIT,
                                remainingMillis = remaining,
                                totalLimitMillis = limitMillis,
                                groupName = group.name
                            )
                            if (shortestTimer == null || remaining < shortestTimer.remainingMillis) {
                                shortestTimer = timerInfo
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        return shortestTimer
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.w("BlockEnforcementCtrl", "Failed to resolve app label for $packageName", e)
            packageName
        }
    }
}
