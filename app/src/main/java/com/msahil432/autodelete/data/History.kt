package com.msahil432.autodelete.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val folderPath: String,
    val fileSize: Long,
    val createdTime: Long,
    val scheduledTime: Long,
    val deletedTime: Long?,
    val status: String // "Scheduled", "Deleted", "Failed", "Skipped"
)
