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
