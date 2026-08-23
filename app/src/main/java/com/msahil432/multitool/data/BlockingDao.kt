package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for block groups, block rules, daily counters, and interception logs.
 */
@Dao
interface BlockingDao {
  // ── Block Groups ──

  /** Returns a [Flow] of all block groups ordered by creation time descending. */
  @Query("SELECT * FROM block_groups ORDER BY createdAt DESC")
  fun getAllGroups(): Flow<List<BlockGroup>>

  /** Returns a reactive [Flow] for the block group matching [id]. */
  @Query("SELECT * FROM block_groups WHERE id = :id LIMIT 1")
  fun getGroupById(id: Long): Flow<BlockGroup?>

  /** Fetches a single block group matching [id] synchronously. */
  @Query("SELECT * FROM block_groups WHERE id = :id LIMIT 1")
  suspend fun getGroupByIdSync(id: Long): BlockGroup?

  /** Inserts or replaces a block group and returns its generated row ID. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroup(group: BlockGroup): Long

  /** Updates an existing block group. */
  @Update
  suspend fun updateGroup(group: BlockGroup)

  /** Deletes a block group. */
  @Delete
  suspend fun deleteGroup(group: BlockGroup)

  /** Deletes a block group by its row [id]. */
  @Query("DELETE FROM block_groups WHERE id = :id")
  suspend fun deleteGroupById(id: Long)

  /** Returns all block groups where enabled is true. */
  @Query("SELECT * FROM block_groups WHERE enabled = 1")
  suspend fun getEnabledGroups(): List<BlockGroup>

  /** Updates the enabled column for all block groups matching [ids]. */
  @Query("UPDATE block_groups SET enabled = :enabled WHERE id IN (:ids)")
  suspend fun setGroupsEnabled(ids: List<Long>, enabled: Boolean)

  // ── Block Rules ──

  /** Returns a [Flow] of all rules belonging to [groupId]. */
  @Query("SELECT * FROM block_rules WHERE groupId = :groupId")
  fun getRulesForGroup(groupId: Long): Flow<List<BlockRule>>

  /** Returns all active rules belonging to [groupId]. */
  @Query("SELECT * FROM block_rules WHERE groupId = :groupId AND enabled = 1")
  suspend fun getEnabledRulesForGroup(groupId: Long): List<BlockRule>

  /** Synchronously fetches all rules belonging to [groupId]. */
  @Query("SELECT * FROM block_rules WHERE groupId = :groupId")
  suspend fun getRulesForGroupSync(groupId: Long): List<BlockRule>

  /** Returns a [Flow] of all block rules across all groups. */
  @Query("SELECT * FROM block_rules")
  fun getAllRules(): Flow<List<BlockRule>>

  /** Synchronously fetches all block rules across all groups. */
  @Query("SELECT * FROM block_rules")
  suspend fun getAllRulesSync(): List<BlockRule>

  /** Inserts or replaces a block rule and returns its row ID. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRule(rule: BlockRule): Long

  /** Updates an existing block rule. */
  @Update
  suspend fun updateRule(rule: BlockRule)

  /** Deletes a block rule. */
  @Delete
  suspend fun deleteRule(rule: BlockRule)

  /** Deletes all rules associated with [groupId]. */
  @Query("DELETE FROM block_rules WHERE groupId = :groupId")
  suspend fun deleteRulesForGroup(groupId: Long)

  // ── Block Counters ──

  /** Returns a [Flow] of the block counter record for a specific [day] and [groupId]. */
  @Query("SELECT * FROM block_counters WHERE dateEpochDay = :day AND groupId = :groupId LIMIT 1")
  fun getCounter(day: Long, groupId: Long): Flow<BlockCounter?>

  /** Synchronously fetches the block counter record for a specific [day] and [groupId]. */
  @Query("SELECT * FROM block_counters WHERE dateEpochDay = :day AND groupId = :groupId LIMIT 1")
  suspend fun getCounterSync(day: Long, groupId: Long): BlockCounter?

  /** Inserts or replaces a block counter entry. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCounter(counter: BlockCounter)

  /** Deletes all counter entries associated with [groupId]. */
  @Query("DELETE FROM block_counters WHERE groupId = :groupId")
  suspend fun deleteCountersForGroup(groupId: Long)

  // ── Block Interceptions ──

  /** Returns a [Flow] of all interception records timestamped on or after [since] in descending order. */
  @Query("SELECT * FROM block_interceptions WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun getInterceptionsSince(since: Long): Flow<List<BlockInterception>>

  /** Inserts or replaces an interception record and returns its row ID. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertInterception(interception: BlockInterception): Long
}

