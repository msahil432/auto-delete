package com.msahil432.multitool.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Reader wrapping system [UsageStatsManager] to query Android usage events across a specified time window.
 */
open class UsageStatsReader(private val context: Context) {
    /**
     * Queries and returns all [UsageEvents.Event] records between [sinceMillis] and [nowMillis].
     *
     * @param sinceMillis Epoch millisecond start boundary.
     * @param nowMillis Epoch millisecond end boundary.
     * @return List of retrieved usage events.
     */
    open fun queryEvents(sinceMillis: Long, nowMillis: Long): List<UsageEvents.Event> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val events = usm.queryEvents(sinceMillis, nowMillis) ?: return emptyList()
        val out = ArrayList<UsageEvents.Event>()
        while (events.hasNextEvent()) {
            val ev = UsageEvents.Event()
            events.getNextEvent(ev)
            out += ev
        }
        return out
    }
}

