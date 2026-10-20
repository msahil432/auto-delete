package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object providing reactive queries and mutation operations
 * for video and media download records.
 */
@Dao
interface DownloadDao {
    /** Returns a reactive [Flow] of all downloads ordered newest first. */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    /** Returns a reactive [Flow] of downloads matching a specific [status], newest first. */
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>

    /** Returns a single download item matching [id], or null if not found. */
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    /** Returns the count of downloads currently in QUEUED or DOWNLOADING status. */
    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun getActiveDownloadCount(): Int

    /** Returns a reactive [Flow] of the count of active (QUEUED or DOWNLOADING) downloads. */
    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING')")
    fun observeActiveDownloadCount(): Flow<Int>

    /** Returns the next QUEUED download ready to run, ordered by creation time (FIFO). */
    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextQueued(): DownloadItem?

    /** Inserts a new download item and returns its generated row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem): Long

    /** Updates an existing download item. */
    @Update
    suspend fun update(item: DownloadItem)

    /** Deletes a specific download item. */
    @Delete
    suspend fun delete(item: DownloadItem)

    /** Deletes all downloads matching a specific [status]. */
    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)

    /** Deletes all download records from the database. */
    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    /**
     * Resets a failed or cancelled download back to QUEUED for retry,
     * resetting progress and clearing error messages.
     */
    @Query("""
        UPDATE downloads 
        SET status = 'QUEUED', progressPercent = 0, downloadedBytes = 0, 
            errorMessage = NULL, updatedAt = :now 
        WHERE id = :id
    """)
    suspend fun retryDownload(id: Long, now: Long)
}
