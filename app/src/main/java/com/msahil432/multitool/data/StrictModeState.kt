package com.msahil432.multitool.data

/**
 * Unlock methods supported for deactivating strict mode.
 */
enum class UnlockMethod {
    TEXT,
    PIN,
    COOLDOWN,
    QR;

    fun displayName(): String = when (this) {
        TEXT -> "Text Match Challenge"
        PIN -> "Master PIN / Password"
        COOLDOWN -> "Cooldown Delay Timer"
        QR -> "Physical QR Code Scan"
    }

    fun description(): String = when (this) {
        TEXT -> "Retype a randomized long text passage with zero typos to unlock."
        PIN -> "Enter your preset master PIN or passphrase."
        COOLDOWN -> "Wait for an enforced cooldown timer before deactivation is allowed."
        QR -> "Scan a previously printed QR code placed in another room."
    }
}

/**
 * DataStore-backed strict mode configuration and runtime state.
 */
data class StrictModeState(
    val isActive: Boolean = false,
    val startedAt: Long = 0L,
    val endAt: Long = 0L, // 0 = until manually unlocked via challenge
    val unlockMethod: UnlockMethod = UnlockMethod.TEXT,
    val pendingDeactivationAt: Long = 0L // epoch millis when cooldown completes
)

/**
 * Optional challenge configuration parameters used during activation.
 */
data class UnlockParams(
    val textLength: Int = 100,
    val masterPasswordHash: String? = null,
    val cooldownMinutes: Int = 15,
    val qrExpectedValue: String? = null
)

/**
 * State of a deactivation request.
 */
sealed interface DeactivationFlow {
    /** Strict mode is active and requires passing the specified challenge. */
    data class ChallengeRequired(
        val method: UnlockMethod,
        val pendingDeactivationAt: Long = 0L
    ) : DeactivationFlow

    /** The timed strict mode session has passed its scheduled end timestamp. */
    data object TimeExpired : DeactivationFlow

    /** Strict mode is not currently active. */
    data object NotActive : DeactivationFlow
}
