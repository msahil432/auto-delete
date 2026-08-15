package com.msahil432.multitool.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

open class UsageStatsReader(private val context: Context) {
    // Returns events since [sinceMillis] up to now.
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
