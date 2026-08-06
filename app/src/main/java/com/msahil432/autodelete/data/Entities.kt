package com.msahil432.autodelete.data

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
    val defaultActionOnIgnore: String, // TimePeriod label or KEEP
    val candidateTimePeriods: String,  // JSON array of TimePeriodPreset; legacy CSV accepted
    val recentlyUsedPeriods: String,   // JSON array of TimePeriodPreset labels; legacy CSV accepted
    val fileTypeExcludeList: String?,  // JSON array of FilterRule — always skip matching files
    val fileTypeIncludeList: String?,  // JSON array of FilterRule — only watch matching files (null/empty = watch all)
    val createdAt: Long,
    // ── Move Rule ──────────────────────────────────────────────────────────────
    val moveRuleEnabled: Boolean = false,           // Move files instead of deleting
    val moveDestinationPath: String? = null,        // SAF URI string of the destination folder
    val moveShowKeep: Boolean = false               // Show 'Keep' button in prompt when move is on
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
    PENDING, TRASHED, DELETED, KEPT, CANCELLED, MOVED
}

@Entity(tableName = "activity_logs")
data class ActivityLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val fileName: String,
    val fileUri: String,
    val action: LogAction,
    val timestamp: Long,
    val destinationPath: String? = null  // Populated when action == MOVED
)

enum class LogAction {
    TRASHED, DELETED, KEPT, RESTORED, MOVED
}
