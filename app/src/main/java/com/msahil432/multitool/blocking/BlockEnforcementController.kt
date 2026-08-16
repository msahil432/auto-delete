package com.msahil432.multitool.blocking

import android.content.Context
import android.content.pm.PackageManager
import com.msahil432.multitool.accessibility.ForegroundAppState
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Observes [ForegroundAppState] and evaluates blocking rules via [BlockEngine],
 * updates launch/usage counters in real time, and manages [BlockOverlayManager] display.
 */
class BlockEnforcementController(
    private val scope: CoroutineScope,
    private val foregroundState: StateFlow<String> = ForegroundAppState.currentPackage,
    private val engine: BlockEngine,
    private val blockingRepo: BlockingRepository,
    private val usageRepo: UsageRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isStrictAllowFriction: (suspend () -> Boolean) = { false }
) {

    private var monitoringJob: Job? = null
    private var periodicJob: Job? = null
    private var currentBlockedPkg: String? = null
    private var sessionStartTime: Long = 0L
    private var lastTickTime: Long = 0L
    private var activePkg: String = ""

    /**
     * Starts listening to foreground app changes and periodic session tracking.
     */
    fun start(context: Context) {
        stop()

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
        monitoringJob = null
        periodicJob = null
        currentBlockedPkg = null
        activePkg = ""
    }

    private suspend fun handlePackageChanged(context: Context, newPkg: String) {
        val prevPkg = activePkg
        val now = clock()

        if (prevPkg.isNotBlank() && prevPkg != newPkg) {
            val sessionElapsed = (now - lastTickTime).coerceAtLeast(0L)
            if (sessionElapsed > 0) {
                updateForegroundDuration(prevPkg, sessionElapsed)
            }
        }

        activePkg = newPkg
        sessionStartTime = now
        lastTickTime = now

        if (newPkg.isBlank() || newPkg == context.packageName) {
            if (BlockOverlayManager.isShowing()) {
                BlockOverlayManager.hide()
            }
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
            }
        }
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
        } catch (_: Exception) {
            packageName
        }
    }
}
