package com.msahil432.multitool.blocking

import com.msahil432.multitool.data.BlockRuleType

/**
 * Metadata representing the active timer state for an open foreground application.
 *
 * @property packageName Package name of the foreground app.
 * @property appLabel Human-readable name of the application.
 * @property ruleType The active timer type ([BlockRuleType.DAILY_QUOTA] or [BlockRuleType.SESSION_LIMIT]).
 * @property remainingMillis Time in milliseconds until the app is blocked.
 * @property totalLimitMillis Total duration in milliseconds configured for this rule.
 * @property groupName Name of the block group enforcing this rule.
 */
data class AppTimerInfo(
    val packageName: String,
    val appLabel: String,
    val ruleType: BlockRuleType,
    val remainingMillis: Long,
    val totalLimitMillis: Long,
    val groupName: String
)
