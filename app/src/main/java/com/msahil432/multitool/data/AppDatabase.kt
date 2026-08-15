package com.msahil432.multitool.data

import androidx.room.*
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
    abstract fun appDao(): AppDao
    abstract fun usageDao(): UsageDao
    abstract fun blockingDao(): BlockingDao
    abstract fun browsingDao(): BrowsingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun geofenceDao(): GeofenceDao
}



