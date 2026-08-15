package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow

class BlockingRepository(private val dao: BlockingDao) {
  fun groups(): Flow<List<BlockGroup>> = dao.getAllGroups()

  fun rulesFor(groupId: Long): Flow<List<BlockRule>> = dao.getRulesForGroup(groupId)

  fun allRules(): Flow<List<BlockRule>> = dao.getAllRules()

  fun groupById(id: Long): Flow<BlockGroup?> = dao.getGroupById(id)

  suspend fun getGroupById(id: Long): BlockGroup? = dao.getGroupByIdSync(id)

  suspend fun getRulesForGroupSync(groupId: Long): List<BlockRule> = dao.getRulesForGroupSync(groupId)

  suspend fun upsertGroup(g: BlockGroup): Long = dao.insertGroup(g)

  suspend fun deleteGroup(g: BlockGroup) {
    dao.deleteRulesForGroup(g.id)
    dao.deleteCountersForGroup(g.id)
    dao.deleteGroup(g)
  }

  suspend fun upsertRule(r: BlockRule): Long = dao.insertRule(r)

  suspend fun deleteRule(r: BlockRule) = dao.deleteRule(r)

  suspend fun deleteRulesForGroup(groupId: Long) = dao.deleteRulesForGroup(groupId)

  fun counter(day: Long, groupId: Long): Flow<BlockCounter?> = dao.getCounter(day, groupId)

  suspend fun getCounterSync(day: Long, groupId: Long): BlockCounter? = dao.getCounterSync(day, groupId)

  suspend fun upsertCounter(counter: BlockCounter) = dao.upsertCounter(counter)

  suspend fun enabledGroupsContaining(pkg: String): List<BlockGroup> {
    return dao.getEnabledGroups().filter { group ->
      group.packageNames.split(";").map { it.trim() }.contains(pkg)
    }
  }

  suspend fun enabledRules(groupId: Long): List<BlockRule> = dao.getEnabledRulesForGroup(groupId)

  suspend fun counterForToday(groupId: Long, day: Long = java.time.LocalDate.now().toEpochDay()): BlockCounter {
    val existing = dao.getCounterSync(day, groupId)
    if (existing != null) return existing
    val newCounter = BlockCounter(dateEpochDay = day, groupId = groupId)
    dao.upsertCounter(newCounter)
    return newCounter
  }

  fun interceptionsSince(since: Long): Flow<List<BlockInterception>> = dao.getInterceptionsSince(since)

  suspend fun recordInterception(interception: BlockInterception): Long = dao.insertInterception(interception)

  suspend fun logInterception(packageName: String, ruleId: Long, ruleType: BlockRuleType): Long {
    return dao.insertInterception(
      BlockInterception(
        timestamp = System.currentTimeMillis(),
        packageName = packageName,
        ruleId = ruleId,
        ruleType = ruleType
      )
    )
  }
}
