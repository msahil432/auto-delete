package com.msahil432.multitool.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val GLOBAL_DEFAULT_POOL = stringPreferencesKey("global_default_pool")
        val GLOBAL_DELETION_MODE = stringPreferencesKey("global_deletion_mode")
        val USAGE_LAST_PROCESSED_TS = longPreferencesKey("usage_last_processed_ts")
        val BLOCK_YT_SHORTS = booleanPreferencesKey("block_yt_shorts")
        val BLOCK_IG_REELS = booleanPreferencesKey("block_ig_reels")
        val BLOCK_FB_REELS = booleanPreferencesKey("block_fb_reels")
        val TRACK_BROWSER_URLS = booleanPreferencesKey("track_browser_urls")
        val NOTIFICATION_VAULT_ENABLED = booleanPreferencesKey("notification_vault_enabled")
        val NOTIFICATION_BLOCKED_PACKAGES = stringSetPreferencesKey("notification_blocked_packages")

        // Strict Mode preferences
        val STRICT_MODE_ACTIVE = booleanPreferencesKey("strict_mode_active")
        val STRICT_MODE_STARTED_AT = longPreferencesKey("strict_mode_started_at")
        val STRICT_MODE_END_AT = longPreferencesKey("strict_mode_end_at")
        val STRICT_UNLOCK_METHOD = stringPreferencesKey("strict_unlock_method")
        val STRICT_PENDING_DEACTIVATION_AT = longPreferencesKey("strict_pending_deactivation_at")

        // Challenge configuration
        val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        val QR_EXPECTED_VALUE = stringPreferencesKey("qr_expected_value")
        val COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val TEXT_CHALLENGE_LENGTH = intPreferencesKey("text_challenge_length")

        // Tamper alarm preference
        val TAMPER_ALARM_ENABLED = booleanPreferencesKey("tamper_alarm_enabled")
    }

    val tamperAlarmEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[TAMPER_ALARM_ENABLED] ?: false
    }

    suspend fun setTamperAlarmEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TAMPER_ALARM_ENABLED] = enabled
        }
    }

    val strictModeState: Flow<StrictModeState> = dataStore.data.map { preferences ->
        val rawMethod = preferences[STRICT_UNLOCK_METHOD] ?: UnlockMethod.TEXT.name
        val method = try {
            UnlockMethod.valueOf(rawMethod)
        } catch (_: Exception) {
            UnlockMethod.TEXT
        }
        StrictModeState(
            isActive = preferences[STRICT_MODE_ACTIVE] ?: false,
            startedAt = preferences[STRICT_MODE_STARTED_AT] ?: 0L,
            endAt = preferences[STRICT_MODE_END_AT] ?: 0L,
            unlockMethod = method,
            pendingDeactivationAt = preferences[STRICT_PENDING_DEACTIVATION_AT] ?: 0L
        )
    }

    suspend fun setStrictModeState(state: StrictModeState) {
        dataStore.edit { preferences ->
            preferences[STRICT_MODE_ACTIVE] = state.isActive
            preferences[STRICT_MODE_STARTED_AT] = state.startedAt
            preferences[STRICT_MODE_END_AT] = state.endAt
            preferences[STRICT_UNLOCK_METHOD] = state.unlockMethod.name
            preferences[STRICT_PENDING_DEACTIVATION_AT] = state.pendingDeactivationAt
        }
    }

    val strictModeActive: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[STRICT_MODE_ACTIVE] ?: false
    }

    suspend fun setStrictModeActive(active: Boolean) {
        dataStore.edit { preferences ->
            preferences[STRICT_MODE_ACTIVE] = active
        }
    }

    suspend fun setStrictPendingDeactivationAt(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[STRICT_PENDING_DEACTIVATION_AT] = timestamp
        }
    }

    val masterPasswordHash: Flow<String?> = dataStore.data.map { preferences ->
        preferences[MASTER_PASSWORD_HASH]
    }

    suspend fun setMasterPasswordHash(hash: String) {
        dataStore.edit { preferences ->
            preferences[MASTER_PASSWORD_HASH] = hash
        }
    }

    val qrExpectedValue: Flow<String?> = dataStore.data.map { preferences ->
        preferences[QR_EXPECTED_VALUE]
    }

    suspend fun setQrExpectedValue(value: String) {
        dataStore.edit { preferences ->
            preferences[QR_EXPECTED_VALUE] = value
        }
    }

    val cooldownMinutes: Flow<Int> = dataStore.data.map { preferences ->
        preferences[COOLDOWN_MINUTES] ?: 15
    }

    suspend fun setCooldownMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[COOLDOWN_MINUTES] = minutes
        }
    }

    val textChallengeLength: Flow<Int> = dataStore.data.map { preferences ->
        preferences[TEXT_CHALLENGE_LENGTH] ?: 100
    }

    suspend fun setTextChallengeLength(length: Int) {
        dataStore.edit { preferences ->
            preferences[TEXT_CHALLENGE_LENGTH] = length
        }
    }

    val notificationVaultEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATION_VAULT_ENABLED] ?: false
    }

    suspend fun setNotificationVaultEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_VAULT_ENABLED] = enabled
        }
    }

    val notificationBlockedPackages: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[NOTIFICATION_BLOCKED_PACKAGES] ?: emptySet()
    }

    suspend fun setNotificationBlockedPackages(packages: Set<String>) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_BLOCKED_PACKAGES] = packages
        }
    }

    val trackBrowserUrls: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[TRACK_BROWSER_URLS] ?: false
    }

    suspend fun setTrackBrowserUrls(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TRACK_BROWSER_URLS] = enabled
        }
    }

    val blockYtShorts: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BLOCK_YT_SHORTS] ?: false
    }

    suspend fun setBlockYtShorts(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BLOCK_YT_SHORTS] = enabled
        }
    }

    val blockIgReels: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BLOCK_IG_REELS] ?: false
    }

    suspend fun setBlockIgReels(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BLOCK_IG_REELS] = enabled
        }
    }

    val blockFbReels: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BLOCK_FB_REELS] ?: false
    }

    suspend fun setBlockFbReels(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BLOCK_FB_REELS] = enabled
        }
    }

    val usageLastProcessedTs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[USAGE_LAST_PROCESSED_TS] ?: 0L
    }

    suspend fun setUsageLastProcessedTs(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[USAGE_LAST_PROCESSED_TS] = timestamp
        }
    }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        }
    }

    val globalDefaultPool: Flow<String> = dataStore.data.map { preferences ->
        preferences[GLOBAL_DEFAULT_POOL] ?: "30s,1h,1w,1mo,never"
    }

    suspend fun setGlobalDefaultPool(pool: String) {
        dataStore.edit { preferences ->
            preferences[GLOBAL_DEFAULT_POOL] = pool
        }
    }

    val globalDeletionMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[GLOBAL_DELETION_MODE] ?: DeletionMode.TRASH.name
    }

    suspend fun setGlobalDeletionMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[GLOBAL_DELETION_MODE] = mode
        }
    }
}
