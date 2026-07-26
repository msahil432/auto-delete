package com.example.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val GLOBAL_DEFAULT_POOL = stringPreferencesKey("global_default_pool")
        val GLOBAL_DELETION_MODE = stringPreferencesKey("global_deletion_mode")
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
