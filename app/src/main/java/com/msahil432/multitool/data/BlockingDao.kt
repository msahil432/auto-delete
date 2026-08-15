package com.msahil432.multitool.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingDao {
  // ── Block Groups ──
  @Query("SELECT * FROM block_groups ORDER BY createdAt DESC")
  fun getAllGroups(): Flow<List<BlockGroup>>

  @Query("SELECT * FROM block_groups WHERE id = :id LIMIT 1")
  fun getGroupById(id: Long): Flow<BlockGroup?>

  @Query("SELECT * FROM block_groups WHERE id = :id LIMIT 1")
  suspend fun getGroupByIdSync(id: Long): BlockGroup?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroup(group: BlockGroup): Long

  @Update
  suspend fun updateGroup(group: BlockGroup)

  @Delete
  suspend fun deleteGroup(group: BlockGroup)

  @Query("DELETE FROM block_groups WHERE id = :id")
  suspend fun deleteGroupById(id: Long)

  @Query("SELECT * FROM block_groups WHERE enabled = 1")
  suspend fun getEnabledGroups(): List<BlockGroup>

  // ── Block Rules ──
  @Query("SELECT * FROM block_rules WHERE groupId = :groupId")
  fun getRulesForGroup(groupId: Long): Flow<List<BlockRule>>

  @Query("SELECT * FROM block_rules WHERE groupId = :groupId AND enabled = 1")
  suspend fun getEnabledRulesForGroup(groupId: Long): List<BlockRule>

  @Query("SELECT * FROM block_rules WHERE groupId = :groupId")
  suspend fun getRulesForGroupSync(groupId: Long): List<BlockRule>

  @Query("SELECT * FROM block_rules")
  fun getAllRules(): Flow<List<BlockRule>>

  @Query("SELECT * FROM block_rules")
  suspend fun getAllRulesSync(): List<BlockRule>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRule(rule: BlockRule): Long

  @Update
  suspend fun updateRule(rule: BlockRule)

  @Delete
  suspend fun deleteRule(rule: BlockRule)

  @Query("DELETE FROM block_rules WHERE groupId = :groupId")
  suspend fun deleteRulesForGroup(groupId: Long)

  // ── Block Counters ──
  @Query("SELECT * FROM block_counters WHERE dateEpochDay = :day AND groupId = :groupId LIMIT 1")
  fun getCounter(day: Long, groupId: Long): Flow<BlockCounter?>

  @Query("SELECT * FROM block_counters WHERE dateEpochDay = :day AND groupId = :groupId LIMIT 1")
  suspend fun getCounterSync(day: Long, groupId: Long): BlockCounter?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCounter(counter: BlockCounter)

  @Query("DELETE FROM block_counters WHERE groupId = :groupId")
  suspend fun deleteCountersForGroup(groupId: Long)

  // ── Block Interceptions ──
  @Query("SELECT * FROM block_interceptions WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun getInterceptionsSince(since: Long): Flow<List<BlockInterception>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertInterception(interception: BlockInterception): Long
}
