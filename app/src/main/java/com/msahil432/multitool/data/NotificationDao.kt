package com.msahil432.multitool.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: VaultedNotification): Long

    @Query("SELECT * FROM vaulted_notifications ORDER BY postedAt DESC")
    fun getAllVaulted(): Flow<List<VaultedNotification>>

    @Query("SELECT * FROM vaulted_notifications WHERE delivered = 0 ORDER BY postedAt DESC")
    fun getUndelivered(): Flow<List<VaultedNotification>>

    @Query("SELECT * FROM vaulted_notifications WHERE delivered = 0 ORDER BY postedAt DESC")
    suspend fun getUndeliveredSync(): List<VaultedNotification>

    @Query("UPDATE vaulted_notifications SET delivered = 1 WHERE delivered = 0")
    suspend fun markAllDelivered(): Int

    @Query("UPDATE vaulted_notifications SET delivered = 1 WHERE id IN (:ids)")
    suspend fun markDelivered(ids: List<Long>): Int

    @Query("DELETE FROM vaulted_notifications")
    suspend fun clearAll(): Int

    @Query("DELETE FROM vaulted_notifications WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM vaulted_notifications WHERE delivered = 0")
    fun getUndeliveredCount(): Flow<Int>
}
