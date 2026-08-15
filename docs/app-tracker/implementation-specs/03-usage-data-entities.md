# 03 — Usage Tracking: Room Entities

> **Status:** ✅ Complete

Prerequisites: `01-rename-package.md`. Follow the Room rules in `00-conventions.md`.

## Goal

Add Room entities to store usage telemetry locally: per-app daily screen time, app
launch events, device unlock events, and a chronological activity timeline.

## Files to create / modify

- Create `data/UsageEntities.kt` with the entities + enums below.
- Modify `data/AppDatabase.kt`:
  - add the new entities to the `@Database(entities = [...])` array,
  - bump `version = 5` → `6`,
  - add `MIGRATION_5_6` that `CREATE TABLE`s the new tables.
- Modify `MultiToolApp` (formerly `AutoDeleteApp`): add `MIGRATION_5_6` to
  `.addMigrations(...)`.

## Entities

```kotlin
@Entity(tableName = "usage_daily_stats",
        indices = [Index(value = ["dateEpochDay", "packageName"], unique = true)])
data class UsageDailyStat(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dateEpochDay: Long,        // LocalDate.toEpochDay()
  val packageName: String,
  val foregroundMillis: Long,    // cumulative foreground time that day
  val launchCount: Int,          // launches that day
  val lastUpdated: Long          // epoch millis of last write
)

@Entity(tableName = "app_launch_events",
        indices = [Index("timestamp"), Index("packageName")])
data class AppLaunchEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val timestamp: Long            // epoch millis of ACTIVITY_RESUMED
)

@Entity(tableName = "unlock_events", indices = [Index("timestamp")])
data class UnlockEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,           // epoch millis
  val type: UnlockType
)

enum class UnlockType { SCREEN_ON, USER_PRESENT }

@Entity(tableName = "timeline_events", indices = [Index("timestamp")])
data class TimelineEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val packageName: String,
  val eventType: TimelineEventType,
  val durationMillis: Long? = null  // set when a foreground segment ends
)

enum class TimelineEventType { APP_FOREGROUND, APP_BACKGROUND, UNLOCK, BLOCK_INTERCEPT }
```

## Migration

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS usage_daily_stats(
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      dateEpochDay INTEGER NOT NULL, packageName TEXT NOT NULL,
      foregroundMillis INTEGER NOT NULL, launchCount INTEGER NOT NULL,
      lastUpdated INTEGER NOT NULL)""")
    db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS
      index_usage_daily_stats_dateEpochDay_packageName
      ON usage_daily_stats(dateEpochDay, packageName)""")
    // app_launch_events, unlock_events, timeline_events + their indices similarly
  }
}
```

Create every table and index declared on the entities. Column types must match Room's
expectations (INTEGER for Long/Int/Boolean, TEXT for String/enum).

## Acceptance criteria

- Project compiles; Room generates no schema-mismatch errors at build time.
- App upgrades from a v5 DB to v6 without data loss and without destructive fallback.
- The four tables exist with the declared indices.

## Out of scope

- DAOs / repository (see `04-usage-repository.md`). Collection logic (06/07).

## Implementation Decisions

- Added `@TypeConverter` methods for `UnlockType` and `TimelineEventType` in `data/Converters.kt` following the existing `Converters` pattern for enum persistence in Room.
- Created `data/UsageEntities.kt` containing `UsageDailyStat`, `AppLaunchEvent`, `UnlockEvent`, `UnlockType`, `TimelineEvent`, and `TimelineEventType`.
- Added `MIGRATION_5_6` in `data/AppDatabase.kt` and registered it in `MultiToolApp.kt` database builder with database version bumped to `6`.