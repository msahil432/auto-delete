package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an intercepted and vaulted notification that was silenced or queued for digest delivery.
 */
@Entity(tableName = "vaulted_notifications", indices = [Index("postedAt")])
data class VaultedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val delivered: Boolean = false
)

