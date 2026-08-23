package com.msahil432.multitool.data

import androidx.room.TypeConverter

/**
 * Room type converters for serializing domain enum types to and from their String representation in SQLite.
 */
class Converters {
    /** Converts [DeletionMode] enum to String. */
    @TypeConverter
    fun fromDeletionMode(value: DeletionMode): String = value.name

    /** Converts String to [DeletionMode] enum. */
    @TypeConverter
    fun toDeletionMode(value: String): DeletionMode = DeletionMode.valueOf(value)

    /** Converts [ActionStatus] enum to String. */
    @TypeConverter
    fun fromActionStatus(value: ActionStatus): String = value.name

    /** Converts String to [ActionStatus] enum. */
    @TypeConverter
    fun toActionStatus(value: String): ActionStatus = ActionStatus.valueOf(value)

    /** Converts [LogAction] enum to String. */
    @TypeConverter
    fun fromLogAction(value: LogAction): String = value.name

    /** Converts String to [LogAction] enum. */
    @TypeConverter
    fun toLogAction(value: String): LogAction = LogAction.valueOf(value)

    /** Converts [UnlockType] enum to String. */
    @TypeConverter
    fun fromUnlockType(value: UnlockType): String = value.name

    /** Converts String to [UnlockType] enum. */
    @TypeConverter
    fun toUnlockType(value: String): UnlockType = UnlockType.valueOf(value)

    /** Converts [TimelineEventType] enum to String. */
    @TypeConverter
    fun fromTimelineEventType(value: TimelineEventType): String = value.name

    /** Converts String to [TimelineEventType] enum. */
    @TypeConverter
    fun toTimelineEventType(value: String): TimelineEventType = TimelineEventType.valueOf(value)

    /** Converts [BlockRuleType] enum to String. */
    @TypeConverter
    fun fromBlockRuleType(value: BlockRuleType): String = value.name

    /** Converts String to [BlockRuleType] enum. */
    @TypeConverter
    fun toBlockRuleType(value: String): BlockRuleType = BlockRuleType.valueOf(value)

    /** Converts [BrowsingKind] enum to String. */
    @TypeConverter
    fun fromBrowsingKind(value: BrowsingKind): String = value.name

    /** Converts String to [BrowsingKind] enum. */
    @TypeConverter
    fun toBrowsingKind(value: String): BrowsingKind = BrowsingKind.valueOf(value)
}


