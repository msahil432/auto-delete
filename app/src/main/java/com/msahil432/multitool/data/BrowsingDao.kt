package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for tracking and querying browser history events.
 */
@Dao
interface BrowsingDao {
  /** Inserts a new [BrowsingEvent] and returns its generated row ID. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(event: BrowsingEvent): Long

  /** Returns a [Flow] of browsing events timestamped on or after [since], ordered newest first. */
  @Query("SELECT * FROM browsing_events WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun recentSince(since: Long): Flow<List<BrowsingEvent>>

  /** Returns a [Flow] of all recorded browsing events, ordered newest first. */
  @Query("SELECT * FROM browsing_events ORDER BY timestamp DESC")
  fun allRecent(): Flow<List<BrowsingEvent>>

  /** Deletes browsing event records older than the [cutoff] timestamp. */
  @Query("DELETE FROM browsing_events WHERE timestamp < :cutoff")
  suspend fun pruneBrowsingEvents(cutoff: Long)
}

