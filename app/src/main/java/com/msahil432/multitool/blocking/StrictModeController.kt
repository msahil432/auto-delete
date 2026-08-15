package com.msahil432.multitool.blocking

import android.content.Context
import com.msahil432.multitool.admin.DeviceAdminHelper
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.DeactivationFlow
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.StrictModeState
import com.msahil432.multitool.data.UnlockMethod
import com.msahil432.multitool.data.UnlockParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Controller enforcing asymmetric lock-in for strict mode.
 * While strict mode is active, focus rules can be made stricter but never weakened,
 * edited-down, or deleted; deactivation requires passing an unlock challenge or waiting out
 * a timed session.
 */
object StrictModeController {

    private var appContext: Context? = null
    private var repository: SettingsRepository? = null
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeJob: Job? = null
    private var clock: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow(StrictModeState())
    val state: StateFlow<StrictModeState> = _state.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Initializes the controller with the application context and settings repository.
     */
    fun init(
        context: Context,
        settingsRepo: SettingsRepository,
        coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        clockProvider: () -> Long = System::currentTimeMillis
    ) {
        appContext = context.applicationContext
        repository = settingsRepo
        scope = coroutineScope
        clock = clockProvider

        observeJob?.cancel()
        observeJob = scope.launch {
            settingsRepo.strictModeState.collectLatest { newState ->
                // Check if timed strict mode has expired
                if (newState.isActive && newState.endAt > 0 && clock() >= newState.endAt) {
                    _state.value = newState.copy(isActive = false)
                    _isActive.value = false
                    settingsRepo.setStrictModeActive(false)
                    appContext?.let { ctx ->
                        if (DeviceAdminHelper.isActive(ctx)) {
                            DeviceAdminHelper.deactivate(ctx)
                        }
                    }
                } else {
                    _state.value = newState
                    _isActive.value = newState.isActive
                }
            }
        }
    }

    /**
     * Activates strict mode with the specified unlock method, optional end timestamp,
     * and challenge configuration parameters. Also activates Device Admin protection.
     */
    fun activate(
        method: UnlockMethod,
        endAt: Long = 0L,
        params: UnlockParams = UnlockParams()
    ) {
        val now = clock()
        val newState = StrictModeState(
            isActive = true,
            startedAt = now,
            endAt = endAt,
            unlockMethod = method,
            pendingDeactivationAt = 0L
        )

        _state.value = newState
        _isActive.value = true

        scope.launch {
            val repo = repository
            if (repo != null) {
                repo.setStrictModeState(newState)
                params.masterPasswordHash?.let { repo.setMasterPasswordHash(it) }
                params.qrExpectedValue?.let { repo.setQrExpectedValue(it) }
                if (params.cooldownMinutes > 0) repo.setCooldownMinutes(params.cooldownMinutes)
                if (params.textLength > 0) repo.setTextChallengeLength(params.textLength)
            }
        }

        // Activate Device Admin (Anti-Uninstall protection)
        appContext?.let { ctx ->
            if (!DeviceAdminHelper.isActive(ctx)) {
                DeviceAdminHelper.requestActivation(ctx)
            }
        }
    }

    /**
     * Guard: returns false if deleting a block group is blocked by active strict mode.
     */
    fun canDeleteGroup(): Boolean = !_isActive.value

    /**
     * Guard: returns false if disabling a block group is blocked by active strict mode.
     */
    fun canDisableGroup(): Boolean = !_isActive.value

    /**
     * Guard: checks whether a modification to a block group weakens restrictions.
     * While strict mode is active, disabling the group or removing packages is prohibited.
     */
    fun canWeakenGroup(oldGroup: BlockGroup, newGroup: BlockGroup): Boolean {
        if (!_isActive.value) return true

        // Prohibit disabling
        if (oldGroup.enabled && !newGroup.enabled) return false

        // Prohibit removing package names
        val oldPkgs = oldGroup.packageNames.split(';').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val newPkgs = newGroup.packageNames.split(';').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (!newPkgs.containsAll(oldPkgs)) return false

        return true
    }

    /**
     * Guard: checks whether [new] weakens [old]. Returns false if the edit makes the rule weaker.
     * While strict mode is active, weakening is prohibited; strengthening is always allowed.
     */
    fun canWeakenRule(old: BlockRule, new: BlockRule): Boolean {
        if (!_isActive.value) return true

        // Disabling an enabled rule is weaker
        if (old.enabled && !new.enabled) return false

        // Changing the rule type while active is prohibited
        if (old.type != new.type) return false

        return when (old.type) {
            BlockRuleType.SCHEDULE -> {
                // 1. Check days of week: new must contain all days that old had
                val daysNarrowed = (old.daysOfWeekMask and new.daysOfWeekMask) != old.daysOfWeekMask
                if (daysNarrowed) return false

                // 2. Check time window duration
                val oldSpan = calculateScheduleSpanMinutes(old.startMinuteOfDay, old.endMinuteOfDay)
                val newSpan = calculateScheduleSpanMinutes(new.startMinuteOfDay, new.endMinuteOfDay)
                if (newSpan < oldSpan) return false

                true
            }
            BlockRuleType.DAILY_QUOTA -> {
                // Increasing daily quota gives more allowed time (weaker)
                new.dailyQuotaMinutes <= old.dailyQuotaMinutes
            }
            BlockRuleType.LAUNCH_LIMIT -> {
                // Increasing max launches allows more launches (weaker)
                new.maxLaunchesPerDay <= old.maxLaunchesPerDay
            }
            BlockRuleType.SESSION_LIMIT -> {
                // Increasing session minutes allows longer sessions (weaker)
                if (new.maxSessionMinutes > old.maxSessionMinutes) return false
                // Reducing cooldown minutes reduces enforced break (weaker)
                if (new.cooldownMinutes < old.cooldownMinutes) return false
                true
            }
            BlockRuleType.GOAL_UNLOCK -> {
                // Lowering required goal minutes makes unlocking easier (weaker)
                if (new.goalRequiredMinutes < old.goalRequiredMinutes) return false

                // Shrinking goal package set makes fulfilling goal harder / alters requirements
                val oldGoals = old.goalPackageNames?.split(';')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
                val newGoals = new.goalPackageNames?.split(';')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
                if (!newGoals.containsAll(oldGoals)) return false

                true
            }
        }
    }

    /**
     * Calculates the duration in minutes for a schedule window handling midnight wrap.
     */
    fun calculateScheduleSpanMinutes(startMinuteOfDay: Int, endMinuteOfDay: Int): Int {
        val start = startMinuteOfDay.coerceIn(0, 1439)
        val end = endMinuteOfDay.coerceIn(0, 1439)
        return if (start <= end) {
            end - start
        } else {
            (1440 - start) + end
        }
    }

    /**
     * Initiates deactivation. Returns the required [DeactivationFlow].
     */
    fun requestDeactivation(): DeactivationFlow {
        val current = _state.value
        if (!current.isActive) {
            return DeactivationFlow.NotActive
        }

        val now = clock()
        if (current.endAt > 0 && now >= current.endAt) {
            completeDeactivation()
            return DeactivationFlow.TimeExpired
        }

        if (current.unlockMethod == UnlockMethod.COOLDOWN) {
            if (current.pendingDeactivationAt == 0L) {
                // Start cooldown delay timer (default 15 mins if not set)
                val cooldownTarget = now + (15 * 60_000L)
                val updatedState = current.copy(pendingDeactivationAt = cooldownTarget)
                _state.value = updatedState
                scope.launch {
                    repository?.setStrictPendingDeactivationAt(cooldownTarget)
                }
                return DeactivationFlow.ChallengeRequired(
                    method = UnlockMethod.COOLDOWN,
                    pendingDeactivationAt = cooldownTarget
                )
            } else if (now >= current.pendingDeactivationAt) {
                // Cooldown elapsed
                return DeactivationFlow.ChallengeRequired(
                    method = UnlockMethod.COOLDOWN,
                    pendingDeactivationAt = current.pendingDeactivationAt
                )
            } else {
                return DeactivationFlow.ChallengeRequired(
                    method = UnlockMethod.COOLDOWN,
                    pendingDeactivationAt = current.pendingDeactivationAt
                )
            }
        }

        return DeactivationFlow.ChallengeRequired(
            method = current.unlockMethod,
            pendingDeactivationAt = current.pendingDeactivationAt
        )
    }

    /**
     * Completes deactivation after the challenge has been successfully passed.
     * Clears strict mode flags and deactivates Device Admin protection.
     */
    fun completeDeactivation() {
        val resetState = StrictModeState(
            isActive = false,
            startedAt = 0L,
            endAt = 0L,
            unlockMethod = _state.value.unlockMethod,
            pendingDeactivationAt = 0L
        )

        _state.value = resetState
        _isActive.value = false

        scope.launch {
            repository?.setStrictModeState(resetState)
        }

        appContext?.let { ctx ->
            if (DeviceAdminHelper.isActive(ctx)) {
                DeviceAdminHelper.deactivate(ctx)
            }
        }
    }

    /**
     * Testing hook to set custom state or mock dependencies.
     */
    fun resetForTesting(
        state: StrictModeState = StrictModeState(),
        repo: SettingsRepository? = null,
        clockProvider: () -> Long = System::currentTimeMillis
    ) {
        observeJob?.cancel()
        _state.value = state
        _isActive.value = state.isActive
        repository = repo
        clock = clockProvider
    }
}
