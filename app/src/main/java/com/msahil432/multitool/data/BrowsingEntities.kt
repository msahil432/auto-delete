package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "browsing_events", indices = [Index("timestamp")])
data class BrowsingEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val packageName: String,      // browser package
  val kind: BrowsingKind,       // URL or SEARCH_QUERY
  val value: String             // domain/url or query text
)

enum class BrowsingKind { URL, SEARCH_QUERY }
