package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "block_groups")
data class BlockGroup(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val packageNames: String,   // delimited list "com.a;com.b" (see FilterRule style)
  val enabled: Boolean = true,
  val createdAt: Long
)

@Entity(tableName = "block_rules")
data class BlockRule(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val groupId: Long,          // FK -> block_groups.id
  val type: BlockRuleType,
  val enabled: Boolean = true,
  // schedule (type = SCHEDULE): bitmask of days + start/end minutes-of-day
  val daysOfWeekMask: Int = 0,        // bit0=Mon .. bit6=Sun
  val startMinuteOfDay: Int = 0,
  val endMinuteOfDay: Int = 0,
  // quota (type = DAILY_QUOTA): total foreground minutes/day across the group
  val dailyQuotaMinutes: Int = 0,
  // launch cap (type = LAUNCH_LIMIT)
  val maxLaunchesPerDay: Int = 0,
  // session limit (type = SESSION_LIMIT)
  val maxSessionMinutes: Int = 0,
  val cooldownMinutes: Int = 0,
  // goal-based (type = GOAL_UNLOCK)
  val goalPackageNames: String? = null,  // productive apps
  val goalRequiredMinutes: Int = 0
)

enum class BlockRuleType { SCHEDULE, DAILY_QUOTA, LAUNCH_LIMIT, SESSION_LIMIT, GOAL_UNLOCK }

@Entity(tableName = "block_interceptions", indices = [Index("timestamp")])
data class BlockInterception(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val packageName: String,
  val ruleId: Long,
  val ruleType: BlockRuleType
)

// Tracks live session/quota counters, reset daily.
@Entity(
  tableName = "block_counters",
  indices = [Index(value = ["dateEpochDay", "groupId"], unique = true)]
)
data class BlockCounter(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dateEpochDay: Long,
  val groupId: Long,
  val usedForegroundMillis: Long = 0,
  val launchesUsed: Int = 0,
  val lockedUntil: Long = 0   // epoch millis for session cooldown lockout
)
