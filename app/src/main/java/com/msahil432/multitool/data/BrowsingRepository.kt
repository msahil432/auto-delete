package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Repository for logging and querying web browsing activity (e.g. visited URLs, search queries).
 */
class BrowsingRepository(
  private val dao: BrowsingDao,
  private val clock: () -> Long = System::currentTimeMillis
) {
  /** Returns the epoch millisecond timestamp representing midnight at the start of today in the system timezone. */
  fun startOfDayMillisNow(): Long =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
      .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  /** Returns a [Flow] of [BrowsingEvent] items recorded since midnight today. */
  fun recentToday(): Flow<List<BrowsingEvent>> = dao.recentSince(startOfDayMillisNow())

  /** Returns a [Flow] of [BrowsingEvent] items recorded since the given epoch millisecond timestamp [since]. */
  fun recentSince(since: Long): Flow<List<BrowsingEvent>> = dao.recentSince(since)

  /** Returns a [Flow] of all recorded [BrowsingEvent] items, ordered newest first. */
  fun allRecent(): Flow<List<BrowsingEvent>> = dao.allRecent()

  /**
   * Records a new browsing event with the given details.
   *
   * @param packageName Package name of the browser or app where the event occurred.
   * @param kind Category of browsing event (e.g., URL or SEARCH).
   * @param value The visited URL or search term.
   * @param timestamp Epoch millisecond timestamp of the event, defaulting to current time.
   */
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

  /**
   * Prunes browsing event records older than [days] to keep the database lean.
   *
   * @param days Retention window in days (default: 90).
   */
  suspend fun pruneOlderThanDays(days: Int = 90) {
    val cutoff = clock() - TimeUnit.DAYS.toMillis(days.toLong())
    dao.pruneBrowsingEvents(cutoff)
  }
}

