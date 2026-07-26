package com.example.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDeletionMode(value: DeletionMode): String = value.name

    @TypeConverter
    fun toDeletionMode(value: String): DeletionMode = DeletionMode.valueOf(value)

    @TypeConverter
    fun fromActionStatus(value: ActionStatus): String = value.name

    @TypeConverter
    fun toActionStatus(value: String): ActionStatus = ActionStatus.valueOf(value)

    @TypeConverter
    fun fromLogAction(value: LogAction): String = value.name

    @TypeConverter
    fun toLogAction(value: String): LogAction = LogAction.valueOf(value)
}
