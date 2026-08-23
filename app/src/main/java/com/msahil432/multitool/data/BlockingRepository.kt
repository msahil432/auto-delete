package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing app blocking configuration, rules, daily interception counters, and event logs.
 */
class BlockingRepository(
  private val dao: BlockingDao,
  private val clock: () -> Long = System::currentTimeMillis
) {
  /** Returns a [Flow] of all configured [BlockGroup] items. */
  fun groups(): Flow<List<BlockGroup>> = dao.getAllGroups()

  /** Returns a [Flow] of [BlockRule] items belonging to the given [groupId]. */
  fun rulesFor(groupId: Long): Flow<List<BlockRule>> = dao.getRulesForGroup(groupId)

  /** Returns a [Flow] of all [BlockRule] items across all groups. */
  fun allRules(): Flow<List<BlockRule>> = dao.getAllRules()

  /** Returns a reactive [Flow] for a single [BlockGroup] matching [id], or null if not found. */
  fun groupById(id: Long): Flow<BlockGroup?> = dao.getGroupById(id)

  /** Fetches a single [BlockGroup] by [id] synchronously. */
  suspend fun getGroupById(id: Long): BlockGroup? = dao.getGroupByIdSync(id)

  /** Fetches all [BlockRule] items for [groupId] synchronously. */
  suspend fun getRulesForGroupSync(groupId: Long): List<BlockRule> = dao.getRulesForGroupSync(groupId)

  /** Inserts or updates a [BlockGroup] and returns its row ID. */
  suspend fun upsertGroup(g: BlockGroup): Long = dao.insertGroup(g)

  /** Updates the enabled state for a collection of [groupIds]. */
  suspend fun setGroupsEnabled(groupIds: Collection<Long>, enabled: Boolean) {
    val ids = groupIds.toList()
    if (ids.isNotEmpty()) {
      dao.setGroupsEnabled(ids, enabled)
    }
  }

  /** Deletes a [BlockGroup] and all of its associated rules and counters. */
  suspend fun deleteGroup(g: BlockGroup) {
    dao.deleteRulesForGroup(g.id)
    dao.deleteCountersForGroup(g.id)
    dao.deleteGroup(g)
  }

  /** Inserts or updates a [BlockRule] and returns its row ID. */
  suspend fun upsertRule(r: BlockRule): Long = dao.insertRule(r)

  /** Deletes a specific [BlockRule]. */
  suspend fun deleteRule(r: BlockRule) = dao.deleteRule(r)

  /** Deletes all rules associated with the given [groupId]. */
  suspend fun deleteRulesForGroup(groupId: Long) = dao.deleteRulesForGroup(groupId)

  /** Returns a [Flow] for the [BlockCounter] of a specific group on a given epoch day. */
  fun counter(day: Long, groupId: Long): Flow<BlockCounter?> = dao.getCounter(day, groupId)

  /** Fetches the [BlockCounter] for a specific group on a given epoch day synchronously. */
  suspend fun getCounterSync(day: Long, groupId: Long): BlockCounter? = dao.getCounterSync(day, groupId)

  /** Inserts or updates a [BlockCounter] record. */
  suspend fun upsertCounter(counter: BlockCounter) = dao.upsertCounter(counter)

  /** Returns all active [BlockGroup]s that target the specified [pkg] name. */
  suspend fun enabledGroupsContaining(pkg: String): List<BlockGroup> {
    return dao.getEnabledGroups().filter { group ->
      group.packageNames.split(";").map { it.trim() }.contains(pkg)
    }
  }

  /** Returns all enabled [BlockRule] items for a specific [groupId]. */
  suspend fun enabledRules(groupId: Long): List<BlockRule> = dao.getEnabledRulesForGroup(groupId)

  /**
   * Retrieves or initializes today's [BlockCounter] for the given [groupId].
   *
   * @param groupId The ID of the block group.
   * @param day The epoch day index, defaulting to today.
   */
  suspend fun counterForToday(groupId: Long, day: Long = java.time.LocalDate.now().toEpochDay()): BlockCounter {
    val existing = dao.getCounterSync(day, groupId)
    if (existing != null) return existing
    val newCounter = BlockCounter(dateEpochDay = day, groupId = groupId)
    dao.upsertCounter(newCounter)
    return newCounter
  }

  /** Returns a [Flow] of interception events recorded since the given epoch millisecond timestamp [since]. */
  fun interceptionsSince(since: Long): Flow<List<BlockInterception>> = dao.getInterceptionsSince(since)

  /** Inserts a new [BlockInterception] record and returns its row ID. */
  suspend fun recordInterception(interception: BlockInterception): Long = dao.insertInterception(interception)

  /**
   * Logs an interception event for [packageName] triggered by [ruleId] of type [ruleType] at the current clock time.
   */
  suspend fun logInterception(packageName: String, ruleId: Long, ruleType: BlockRuleType): Long {
    return dao.insertInterception(
      BlockInterception(
        timestamp = clock(),
        packageName = packageName,
        ruleId = ruleId,
        ruleType = ruleType
      )
    )
  }
}
