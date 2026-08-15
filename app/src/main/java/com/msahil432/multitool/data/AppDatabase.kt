package com.msahil432.multitool.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM folder_configs")
    fun getAllFolderConfigs(): Flow<List<FolderConfig>>

    @Query("SELECT * FROM folder_configs WHERE enabled = 1")
    fun getEnabledFolderConfigs(): Flow<List<FolderConfig>>

    @Query("SELECT * FROM folder_configs WHERE enabled = 1")
    suspend fun getEnabledFolderConfigsSync(): List<FolderConfig>

    @Query("SELECT * FROM folder_configs WHERE id = :id")
    fun getFolderConfigById(id: Long): Flow<FolderConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderConfig(config: FolderConfig): Long

    @Update
    suspend fun updateFolderConfig(config: FolderConfig)

    @Delete
    suspend fun deleteFolderConfig(config: FolderConfig)

    @Query("SELECT * FROM pending_actions")
    fun getAllPendingActions(): Flow<List<PendingAction>>
    
    @Query("SELECT * FROM pending_actions WHERE fileUri = :uri AND status = 'PENDING'")
    suspend fun getPendingActionByUri(uri: String): PendingAction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingAction(action: PendingAction): Long

    @Update
    suspend fun updatePendingAction(action: PendingAction)

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntry)
    
    @Update
    suspend fun updateActivityLog(log: ActivityLogEntry)
    
    @Query("SELECT * FROM activity_logs WHERE id = :id")
    suspend fun getActivityLogById(id: Long): ActivityLogEntry?
}

/**
 * Migration from version 1 → 2:
 *  - Adds `fileTypeIncludeList` column (nullable TEXT, default NULL)
 *  - Converts legacy CSV `candidateTimePeriods` / `recentlyUsedPeriods` to the
 *    new pipe-delimited format via [parseLegacyCsvPresets] + [encodeTimePeriodPresets].
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add the new include-list column
        db.execSQL(
            "ALTER TABLE folder_configs ADD COLUMN fileTypeIncludeList TEXT DEFAULT NULL"
        )

        // 2. Migrate candidateTimePeriods and recentlyUsedPeriods from legacy CSV → pipe format
        val cursor = db.query("SELECT id, candidateTimePeriods, recentlyUsedPeriods FROM folder_configs")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val candidateRaw = it.getString(1) ?: ""
                val recentlyRaw  = it.getString(2) ?: ""

                // Already in new pipe format? Leave it alone.
                val newCandidate = if (candidateRaw.contains("|")) {
                    candidateRaw
                } else {
                    encodeTimePeriodPresets(
                        if (candidateRaw.contains("{")) {
                            // Previous JSON attempt — parse and re-encode
                            decodeTimePeriodPresets(candidateRaw)
                        } else {
                            // Original legacy CSV
                            parseLegacyCsvPresets(candidateRaw).takeIf { p -> p.isNotEmpty() }
                                ?: DEFAULT_TIME_PRESETS
                        }
                    )
                }

                val newRecently = if (recentlyRaw.contains("|")) {
                    recentlyRaw
                } else {
                    encodeTimePeriodPresets(
                        if (recentlyRaw.contains("{")) {
                            decodeTimePeriodPresets(recentlyRaw)
                        } else {
                            parseLegacyCsvPresets(recentlyRaw).takeIf { p -> p.isNotEmpty() }
                                ?: DEFAULT_TIME_PRESETS
                        }
                    )
                }

                db.execSQL(
                    "UPDATE folder_configs SET candidateTimePeriods = ?, recentlyUsedPeriods = ? WHERE id = ?",
                    arrayOf(newCandidate, newRecently, id)
                )
            }
        }
    }
}


/**
 * Migration from version 2 → 3:
 *  Re-sanitizes ALL rows' `candidateTimePeriods` and `recentlyUsedPeriods` fields,
 *  converting any corrupt JSON fragment data (from the brief window when old split code
 *  ran against new JSON data) into the canonical pipe-delimited format.
 *  Rows already in pipe format are left untouched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT id, candidateTimePeriods, recentlyUsedPeriods FROM folder_configs")
        cursor.use {
            while (it.moveToNext()) {
                val id           = it.getLong(0)
                val candidateRaw = it.getString(1) ?: ""
                val recentlyRaw  = it.getString(2) ?: ""

                // Re-decode and re-encode to guarantee canonical pipe format
                val sanitizedCandidate = encodeTimePeriodPresets(decodeTimePeriodPresets(candidateRaw))
                val sanitizedRecently  = encodeTimePeriodPresets(decodeTimePeriodPresets(recentlyRaw))

                db.execSQL(
                    "UPDATE folder_configs SET candidateTimePeriods = ?, recentlyUsedPeriods = ? WHERE id = ?",
                    arrayOf(sanitizedCandidate, sanitizedRecently, id)
                )
            }
        }
    }
}

/**
 * Migration from version 3 → 4:
 *  - Adds `moveRuleEnabled` (INTEGER NOT NULL DEFAULT 0) to folder_configs
 *  - Adds `moveDestinationPath` (TEXT DEFAULT NULL) to folder_configs
 *  - Adds `moveShowKeep` (INTEGER NOT NULL DEFAULT 0) to folder_configs
 *  - Adds `destinationPath` (TEXT DEFAULT NULL) to activity_logs
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folder_configs ADD COLUMN moveRuleEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folder_configs ADD COLUMN moveDestinationPath TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE folder_configs ADD COLUMN moveShowKeep INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE activity_logs ADD COLUMN destinationPath TEXT DEFAULT NULL")
    }
}

/**
 * Migration from version 4 → 5:
 *  - Adds `errorDetails` (TEXT DEFAULT NULL) to activity_logs
 *    Populated when action == ERRORED; stores the exception message + brief stack trace.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE activity_logs ADD COLUMN errorDetails TEXT DEFAULT NULL")
    }
}

/**
 * Migration from version 5 → 6:
 *  - Adds `usage_daily_stats` table and unique index
 *  - Adds `app_launch_events` table and indices
 *  - Adds `unlock_events` table and index
 *  - Adds `timeline_events` table and index
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS usage_daily_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dateEpochDay INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                foregroundMillis INTEGER NOT NULL,
                launchCount INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_usage_daily_stats_dateEpochDay_packageName
            ON usage_daily_stats(dateEpochDay, packageName)
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_launch_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packageName TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_app_launch_events_timestamp
            ON app_launch_events(timestamp)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_app_launch_events_packageName
            ON app_launch_events(packageName)
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS unlock_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                type TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_unlock_events_timestamp
            ON unlock_events(timestamp)
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS timeline_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                eventType TEXT NOT NULL,
                durationMillis INTEGER
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_timeline_events_timestamp
            ON timeline_events(timestamp)
        """.trimIndent())
    }
}

/**
 * Migration from version 6 → 7:

 *  - Adds `block_groups` table
 *  - Adds `block_rules` table
 *  - Adds `block_interceptions` table and index
 *  - Adds `block_counters` table and unique index
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS block_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                packageNames TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS block_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                groupId INTEGER NOT NULL,
                type TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                daysOfWeekMask INTEGER NOT NULL,
                startMinuteOfDay INTEGER NOT NULL,
                endMinuteOfDay INTEGER NOT NULL,
                dailyQuotaMinutes INTEGER NOT NULL,
                maxLaunchesPerDay INTEGER NOT NULL,
                maxSessionMinutes INTEGER NOT NULL,
                cooldownMinutes INTEGER NOT NULL,
                goalPackageNames TEXT,
                goalRequiredMinutes INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS block_interceptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                ruleId INTEGER NOT NULL,
                ruleType TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_block_interceptions_timestamp
            ON block_interceptions(timestamp)
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS block_counters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dateEpochDay INTEGER NOT NULL,
                groupId INTEGER NOT NULL,
                usedForegroundMillis INTEGER NOT NULL,
                launchesUsed INTEGER NOT NULL,
                lockedUntil INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_block_counters_dateEpochDay_groupId
            ON block_counters(dateEpochDay, groupId)
        """.trimIndent())
    }
}

/**
 * Migration from version 7 → 8:
 *  - Adds `browsing_events` table and index
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS browsing_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                kind TEXT NOT NULL,
                value TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_browsing_events_timestamp
            ON browsing_events(timestamp)
        """.trimIndent())
    }
}

/**
 * Migration from version 8 → 9:
 *  - Adds `vaulted_notifications` table and index
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vaulted_notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packageName TEXT NOT NULL,
                title TEXT,
                text TEXT,
                postedAt INTEGER NOT NULL,
                delivered INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_vaulted_notifications_postedAt
            ON vaulted_notifications(postedAt)
        """.trimIndent())
    }
}

@Database(
    entities = [
        FolderConfig::class,
        PendingAction::class,
        ActivityLogEntry::class,
        UsageDailyStat::class,
        AppLaunchEvent::class,
        UnlockEvent::class,
        TimelineEvent::class,
        BlockGroup::class,
        BlockRule::class,
        BlockInterception::class,
        BlockCounter::class,
        BrowsingEvent::class,
        VaultedNotification::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun usageDao(): UsageDao
    abstract fun blockingDao(): BlockingDao
    abstract fun browsingDao(): BrowsingDao
    abstract fun notificationDao(): NotificationDao
}


