package com.msahil432.multitool.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for persisting and querying vaulted notifications.
 */
@Dao
interface NotificationDao {
    /** Inserts a new vaulted notification record and returns its row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: VaultedNotification): Long

    /** Returns a [Flow] of all vaulted notifications ordered by post time descending. */
    @Query("SELECT * FROM vaulted_notifications ORDER BY postedAt DESC")
    fun getAllVaulted(): Flow<List<VaultedNotification>>

    /** Returns a [Flow] of undelivered notifications ordered by post time descending. */
    @Query("SELECT * FROM vaulted_notifications WHERE delivered = 0 ORDER BY postedAt DESC")
    fun getUndelivered(): Flow<List<VaultedNotification>>

    /** Synchronously retrieves all undelivered notifications. */
    @Query("SELECT * FROM vaulted_notifications WHERE delivered = 0 ORDER BY postedAt DESC")
    suspend fun getUndeliveredSync(): List<VaultedNotification>

    /** Marks all undelivered notifications as delivered. */
    @Query("UPDATE vaulted_notifications SET delivered = 1 WHERE delivered = 0")
    suspend fun markAllDelivered(): Int

    /** Marks notifications matching [ids] as delivered. */
    @Query("UPDATE vaulted_notifications SET delivered = 1 WHERE id IN (:ids)")
    suspend fun markDelivered(ids: List<Long>): Int

    /** Deletes all vaulted notifications from the database. */
    @Query("DELETE FROM vaulted_notifications")
    suspend fun clearAll(): Int

    /** Deletes a single vaulted notification matching [id]. */
    @Query("DELETE FROM vaulted_notifications WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** Returns a [Flow] of the total count of undelivered notifications. */
    @Query("SELECT COUNT(*) FROM vaulted_notifications WHERE delivered = 0")
    fun getUndeliveredCount(): Flow<Int>
}

