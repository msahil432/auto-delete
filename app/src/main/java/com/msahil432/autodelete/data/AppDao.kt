package com.msahil432.autodelete.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert
    suspend fun insertFolder(folder: Folder): Long

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("SELECT * FROM time_usages WHERE folderId = :folderId ORDER BY usageCount DESC LIMIT 4")
    suspend fun getTopTimeUsages(folderId: Long): List<TimeUsage>
    
    @Query("SELECT * FROM time_usages WHERE folderId = :folderId AND duration = :duration")
    suspend fun getTimeUsage(folderId: Long, duration: Long): TimeUsage?

    @Insert
    suspend fun insertTimeUsage(timeUsage: TimeUsage)

    @Update
    suspend fun updateTimeUsage(timeUsage: TimeUsage)

    @Insert
    suspend fun insertScheduledDeletion(scheduledDeletion: ScheduledDeletion): Long

    @Query("SELECT * FROM scheduled_deletions WHERE id = :id")
    suspend fun getScheduledDeletion(id: Long): ScheduledDeletion?

    @Query("DELETE FROM scheduled_deletions WHERE id = :id")
    suspend fun removeScheduledDeletion(id: Long)

    @Query("SELECT * FROM scheduled_deletions")
    suspend fun getAllScheduledDeletions(): List<ScheduledDeletion>

    @Insert
    suspend fun insertHistory(history: History): Long

    @Update
    suspend fun updateHistory(history: History)
    
    @Query("SELECT * FROM history ORDER BY createdTime DESC")
    fun getAllHistory(): Flow<List<History>>
}
