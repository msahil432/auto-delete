package com.msahil432.autodelete.data

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

@Database(
    entities = [FolderConfig::class, PendingAction::class, ActivityLogEntry::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
