package com.msahil432.multitool.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for folder configurations, pending actions, and activity logs.
 */
@Dao
interface AppDao {
    /** Returns a [Flow] of all configured folders. */
    @Query("SELECT * FROM folder_configs")
    fun getAllFolderConfigs(): Flow<List<FolderConfig>>

    /** Returns a [Flow] of all enabled folder configurations. */
    @Query("SELECT * FROM folder_configs WHERE enabled = 1")
    fun getEnabledFolderConfigs(): Flow<List<FolderConfig>>

    /** Synchronously retrieves all enabled folder configurations. */
    @Query("SELECT * FROM folder_configs WHERE enabled = 1")
    suspend fun getEnabledFolderConfigsSync(): List<FolderConfig>

    /** Returns a reactive [Flow] for the folder configuration matching [id]. */
    @Query("SELECT * FROM folder_configs WHERE id = :id")
    fun getFolderConfigById(id: Long): Flow<FolderConfig?>

    /** Inserts or replaces a folder configuration and returns its row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderConfig(config: FolderConfig): Long

    /** Updates an existing folder configuration. */
    @Update
    suspend fun updateFolderConfig(config: FolderConfig)

    /** Deletes a folder configuration. */
    @Delete
    suspend fun deleteFolderConfig(config: FolderConfig)

    /** Returns a [Flow] of all pending cleanup actions. */
    @Query("SELECT * FROM pending_actions")
    fun getAllPendingActions(): Flow<List<PendingAction>>

    /** Synchronously retrieves a pending action for a specific [uri] if status is PENDING. */
    @Query("SELECT * FROM pending_actions WHERE fileUri = :uri AND status = 'PENDING'")
    suspend fun getPendingActionByUri(uri: String): PendingAction?

    /** Inserts or replaces a pending action and returns its row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingAction(action: PendingAction): Long

    /** Updates an existing pending action. */
    @Update
    suspend fun updatePendingAction(action: PendingAction)

    /** Returns a [Flow] of all activity log entries ordered newest first. */
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLogEntry>>

    /** Inserts or replaces an activity log entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntry)

    /** Updates an existing activity log entry. */
    @Update
    suspend fun updateActivityLog(log: ActivityLogEntry)

    /** Synchronously retrieves an activity log entry matching [id]. */
    @Query("SELECT * FROM activity_logs WHERE id = :id")
    suspend fun getActivityLogById(id: Long): ActivityLogEntry?
}

/**
 * Main Room database holding entities across files, usage, blocking, browsing, notifications, and geofencing.
 */
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
        VaultedNotification::class,
        GeofenceProfile::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /** Returns the DAO for folder configurations, pending actions, and activity logs. */
    abstract fun appDao(): AppDao

    /** Returns the DAO for usage tracking and daily statistics. */
    abstract fun usageDao(): UsageDao

    /** Returns the DAO for app blocking rules, groups, counters, and interceptions. */
    abstract fun blockingDao(): BlockingDao

    /** Returns the DAO for web browsing history. */
    abstract fun browsingDao(): BrowsingDao

    /** Returns the DAO for vaulted notifications. */
    abstract fun notificationDao(): NotificationDao

    /** Returns the DAO for geofence profiles. */
    abstract fun geofenceDao(): GeofenceDao
}




