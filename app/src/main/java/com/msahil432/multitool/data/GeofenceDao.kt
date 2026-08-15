package com.msahil432.multitool.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {
    @Query("SELECT * FROM geofence_profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<GeofenceProfile>>

    @Query("SELECT * FROM geofence_profiles WHERE enabled = 1")
    fun getEnabledProfiles(): Flow<List<GeofenceProfile>>

    @Query("SELECT * FROM geofence_profiles WHERE enabled = 1")
    suspend fun getEnabledProfilesSync(): List<GeofenceProfile>

    @Query("SELECT * FROM geofence_profiles WHERE id = :id LIMIT 1")
    fun getProfileById(id: Long): Flow<GeofenceProfile?>

    @Query("SELECT * FROM geofence_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileByIdSync(id: Long): GeofenceProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GeofenceProfile): Long

    @Update
    suspend fun updateProfile(profile: GeofenceProfile)

    @Delete
    suspend fun deleteProfile(profile: GeofenceProfile)

    @Query("DELETE FROM geofence_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("UPDATE geofence_profiles SET enabled = :enabled WHERE id = :id")
    suspend fun setProfileEnabled(id: Long, enabled: Boolean)
}
