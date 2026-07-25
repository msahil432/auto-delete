package com.msahil432.autodelete.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_usages")
data class TimeUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val duration: Long,
    val usageCount: Int = 1
)
