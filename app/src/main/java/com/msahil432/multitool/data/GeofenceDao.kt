package com.msahil432.multitool.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for managing geofence profile definitions and states.
 */
@Dao
interface GeofenceDao {
    /** Returns a [Flow] of all geofence profiles ordered by ID descending. */
    @Query("SELECT * FROM geofence_profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<GeofenceProfile>>

    /** Returns a [Flow] of all active geofence profiles. */
    @Query("SELECT * FROM geofence_profiles WHERE enabled = 1")
    fun getEnabledProfiles(): Flow<List<GeofenceProfile>>

    /** Synchronously retrieves all active geofence profiles. */
    @Query("SELECT * FROM geofence_profiles WHERE enabled = 1")
    suspend fun getEnabledProfilesSync(): List<GeofenceProfile>

    /** Returns a reactive [Flow] for the geofence profile matching [id]. */
    @Query("SELECT * FROM geofence_profiles WHERE id = :id LIMIT 1")
    fun getProfileById(id: Long): Flow<GeofenceProfile?>

    /** Synchronously retrieves the geofence profile matching [id]. */
    @Query("SELECT * FROM geofence_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileByIdSync(id: Long): GeofenceProfile?

    /** Inserts or replaces a geofence profile and returns its row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GeofenceProfile): Long

    /** Updates an existing geofence profile. */
    @Update
    suspend fun updateProfile(profile: GeofenceProfile)

    /** Deletes a geofence profile. */
    @Delete
    suspend fun deleteProfile(profile: GeofenceProfile)

    /** Deletes a geofence profile by its row [id]. */
    @Query("DELETE FROM geofence_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    /** Updates the enabled state of a geofence profile by its row [id]. */
    @Query("UPDATE geofence_profiles SET enabled = :enabled WHERE id = :id")
    suspend fun setProfileEnabled(id: Long, enabled: Boolean)
}

