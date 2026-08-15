package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class BrowsingRepository(private val dao: BrowsingDao) {
  fun startOfDayMillisNow(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  fun recentToday(): Flow<List<BrowsingEvent>> = dao.recentSince(startOfDayMillisNow())

  fun recentSince(since: Long): Flow<List<BrowsingEvent>> = dao.recentSince(since)

  fun allRecent(): Flow<List<BrowsingEvent>> = dao.allRecent()

  suspend fun recordBrowsing(
    packageName: String,
    kind: BrowsingKind,
    value: String,
    timestamp: Long = System.currentTimeMillis()
  ): Long {
    return dao.insert(
      BrowsingEvent(
        timestamp = timestamp,
        packageName = packageName,
        kind = kind,
        value = value
      )
    )
  }

  suspend fun pruneOlderThanDays(days: Int = 90) {
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
    dao.pruneBrowsingEvents(cutoff)
  }
}
