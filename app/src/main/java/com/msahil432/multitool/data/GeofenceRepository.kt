package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow

class GeofenceRepository(private val dao: GeofenceDao) {

    fun allProfiles(): Flow<List<GeofenceProfile>> = dao.getAllProfiles()

    fun enabledProfiles(): Flow<List<GeofenceProfile>> = dao.getEnabledProfiles()

    suspend fun getEnabledProfilesSync(): List<GeofenceProfile> = dao.getEnabledProfilesSync()

    fun profileById(id: Long): Flow<GeofenceProfile?> = dao.getProfileById(id)

    suspend fun getProfileById(id: Long): GeofenceProfile? = dao.getProfileByIdSync(id)

    suspend fun upsertProfile(profile: GeofenceProfile): Long = dao.insertProfile(profile)

    suspend fun updateProfile(profile: GeofenceProfile) = dao.updateProfile(profile)

    suspend fun deleteProfile(profile: GeofenceProfile) = dao.deleteProfile(profile)

    suspend fun deleteProfileById(id: Long) = dao.deleteProfileById(id)

    suspend fun setProfileEnabled(id: Long, enabled: Boolean) = dao.setProfileEnabled(id, enabled)

    companion object {
        fun parseGroupIds(raw: String): List<Long> {
            if (raw.isBlank()) return emptyList()
            return raw.split(';', ',')
                .mapNotNull { it.trim().toLongOrNull() }
                .distinct()
        }

        fun formatGroupIds(ids: Collection<Long>): String {
            return ids.filter { it > 0 }.distinct().joinToString(";")
        }
    }
}
