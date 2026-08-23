package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a watched folder configuration for automatic file cleanup and movement rules.
 */
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

/**
 * Deletion behavior mode specifying whether files are sent to trash, permanently deleted, or re-prompted.
 */
enum class DeletionMode {
    TRASH, DELETE, ASK_AGAIN
}

/**
 * Entity representing a pending file action scheduled for future deletion or movement.
 */
@Entity(tableName = "pending_actions")
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val fileUri: String, // Or file path
    val scheduledAt: Long,
    val status: ActionStatus
)

/**
 * Lifecycle status of a scheduled [PendingAction].
 */
enum class ActionStatus {
    PENDING, TRASHED, DELETED, KEPT, CANCELLED, MOVED
}

/**
 * Entity representing an audit log entry for file cleanup operations (trash, delete, move, keep, error).
 */
@Entity(tableName = "activity_logs")
data class ActivityLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val fileName: String,
    val fileUri: String,
    val action: LogAction,
    val timestamp: Long,
    val destinationPath: String? = null,  // Populated when action == MOVED
    val errorDetails: String? = null       // Populated when action == ERRORED (message + brief stack trace)
)

/**
 * Type of action recorded in an [ActivityLogEntry].
 */
enum class LogAction {
    TRASHED, DELETED, KEPT, RESTORED, MOVED, ERRORED
}

