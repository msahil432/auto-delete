package com.msahil432.autodelete.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_deletions")
data class ScheduledDeletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val folderId: Long,
    val scheduledTime: Long,
    val createdTime: Long = System.currentTimeMillis(),
    val deletionMode: String
)
