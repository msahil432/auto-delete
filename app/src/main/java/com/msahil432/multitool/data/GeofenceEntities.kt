package com.msahil432.multitool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofence_profiles")
data class GeofenceProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val onEnterGroupIds: String,   // delimited BlockGroup ids to enable on ENTER (e.g. "1;2")
    val onExitGroupIds: String,    // groups to enable on EXIT (or clear)
    val enabled: Boolean = true
)
