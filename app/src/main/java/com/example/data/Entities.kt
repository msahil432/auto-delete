package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_configs")
data class FolderConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val displayName: String,
    val isDefaultScreenshotsFolder: Boolean,
    val enabled: Boolean,
    val deletionMode: DeletionMode,
    val defaultActionOnIgnore: String, // TimePeriod or KEEP
    val candidateTimePeriods: String, // comma separated or json
    val recentlyUsedPeriods: String, // comma separated or json
    val fileTypeExcludeList: String?,
    val createdAt: Long
)

enum class DeletionMode {
    TRASH, DELETE, ASK_AGAIN
}

@Entity(tableName = "pending_actions")
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val fileUri: String, // Or file path
    val scheduledAt: Long,
    val status: ActionStatus
)

enum class ActionStatus {
    PENDING, TRASHED, DELETED, KEPT, CANCELLED
}

@Entity(tableName = "activity_logs")
data class ActivityLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val fileName: String,
    val fileUri: String,
    val action: LogAction,
    val timestamp: Long
)

enum class LogAction {
    TRASHED, DELETED, KEPT, RESTORED
}
