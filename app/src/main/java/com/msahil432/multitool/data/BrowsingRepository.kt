package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class BrowsingRepository(
  private val dao: BrowsingDao,
  private val clock: () -> Long = System::currentTimeMillis
) {
  fun startOfDayMillisNow(): Long =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
      .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  fun recentToday(): Flow<List<BrowsingEvent>> = dao.recentSince(startOfDayMillisNow())

  fun recentSince(since: Long): Flow<List<BrowsingEvent>> = dao.recentSince(since)

  fun allRecent(): Flow<List<BrowsingEvent>> = dao.allRecent()

  suspend fun recordBrowsing(
    packageName: String,
    kind: BrowsingKind,
    value: String,
    timestamp: Long = clock()
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
    val cutoff = clock() - TimeUnit.DAYS.toMillis(days.toLong())
    dao.pruneBrowsingEvents(cutoff)
  }
}
