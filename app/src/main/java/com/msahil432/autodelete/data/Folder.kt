package com.msahil432.autodelete.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val defaultPeriods: String, // Comma separated ms, e.g., "3600000,86400000,604800000,-1"
    val deletionMode: String, // "Permanent", "Trash", "Confirm"
    val createdAt: Long = System.currentTimeMillis()
)
