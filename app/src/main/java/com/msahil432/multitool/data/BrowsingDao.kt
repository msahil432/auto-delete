package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowsingDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(event: BrowsingEvent): Long

  @Query("SELECT * FROM browsing_events WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun recentSince(since: Long): Flow<List<BrowsingEvent>>

  @Query("SELECT * FROM browsing_events ORDER BY timestamp DESC")
  fun allRecent(): Flow<List<BrowsingEvent>>

  @Query("DELETE FROM browsing_events WHERE timestamp < :cutoff")
  suspend fun pruneBrowsingEvents(cutoff: Long)
}
