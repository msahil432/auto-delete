package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing geofence profiles and converting serialized group ID lists.
 */
class GeofenceRepository(private val dao: GeofenceDao) {

    /** Returns a [Flow] of all configured [GeofenceProfile] records. */
    fun allProfiles(): Flow<List<GeofenceProfile>> = dao.getAllProfiles()

    /** Returns a [Flow] of currently enabled [GeofenceProfile] records. */
    fun enabledProfiles(): Flow<List<GeofenceProfile>> = dao.getEnabledProfiles()

    /** Fetches all currently enabled [GeofenceProfile] records synchronously. */
    suspend fun getEnabledProfilesSync(): List<GeofenceProfile> = dao.getEnabledProfilesSync()

    /** Returns a reactive [Flow] for the [GeofenceProfile] matching [id]. */
    fun profileById(id: Long): Flow<GeofenceProfile?> = dao.getProfileById(id)

    /** Fetches a [GeofenceProfile] matching [id] synchronously. */
    suspend fun getProfileById(id: Long): GeofenceProfile? = dao.getProfileByIdSync(id)

    /** Inserts a new [GeofenceProfile] and returns its row ID. */
    suspend fun upsertProfile(profile: GeofenceProfile): Long = dao.insertProfile(profile)

    /** Updates an existing [GeofenceProfile]. */
    suspend fun updateProfile(profile: GeofenceProfile) = dao.updateProfile(profile)

    /** Deletes a [GeofenceProfile] entity. */
    suspend fun deleteProfile(profile: GeofenceProfile) = dao.deleteProfile(profile)

    /** Deletes a [GeofenceProfile] matching [id]. */
    suspend fun deleteProfileById(id: Long) = dao.deleteProfileById(id)

    /** Sets the enabled status of the geofence profile identified by [id]. */
    suspend fun setProfileEnabled(id: Long, enabled: Boolean) = dao.setProfileEnabled(id, enabled)

    companion object {
        /**
         * Parses a delimiter-separated string of group IDs (semicolons or commas) into a unique list of [Long] IDs.
         *
         * @param raw The raw string containing group IDs.
         */
        fun parseGroupIds(raw: String): List<Long> {
            if (raw.isBlank()) return emptyList()
            return raw.split(';', ',')
                .mapNotNull { it.trim().toLongOrNull() }
                .distinct()
        }

        /**
         * Formats a collection of group IDs into a semicolon-separated string.
         *
         * @param ids Collection of group ID numbers.
         */
        fun formatGroupIds(ids: Collection<Long>): String {
            return ids.filter { it > 0 }.distinct().joinToString(";")
        }
    }
}

