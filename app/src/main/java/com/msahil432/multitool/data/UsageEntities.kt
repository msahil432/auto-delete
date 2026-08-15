package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "usage_daily_stats",
  indices = [Index(value = ["dateEpochDay", "packageName"], unique = true)]
)
data class UsageDailyStat(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dateEpochDay: Long,        // LocalDate.toEpochDay()
  val packageName: String,
  val foregroundMillis: Long,    // cumulative foreground time that day
  val launchCount: Int,          // launches that day
  val lastUpdated: Long          // epoch millis of last write
)

@Entity(
  tableName = "app_launch_events",
  indices = [Index("timestamp"), Index("packageName")]
)
data class AppLaunchEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val timestamp: Long            // epoch millis of ACTIVITY_RESUMED
)

@Entity(
  tableName = "unlock_events",
  indices = [Index("timestamp")]
)
data class UnlockEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,           // epoch millis
  val type: UnlockType
)

enum class UnlockType { SCREEN_ON, USER_PRESENT }

@Entity(
  tableName = "timeline_events",
  indices = [Index("timestamp")]
)
data class TimelineEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val packageName: String,
  val eventType: TimelineEventType,
  val durationMillis: Long? = null  // set when a foreground segment ends
)

enum class TimelineEventType { APP_FOREGROUND, APP_BACKGROUND, UNLOCK, BLOCK_INTERCEPT }
