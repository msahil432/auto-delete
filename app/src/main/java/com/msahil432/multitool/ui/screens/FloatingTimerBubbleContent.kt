package com.msahil432.multitool.ui.screens

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.blocking.AppTimerInfo
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Composable rendering the floating timer bubble UI on top of restricted foreground apps.
 *
 * Supports compact time ring mode and expanded card mode with countdown progress and color urgency.
 *
 * @param info Timer metadata including remaining duration, total quota, and rule type.
 * @param isExpanded Whether the detailed card view is currently expanded.
 * @param onToggleExpand Callback invoked when the user taps the bubble to toggle expanded state.
 * @param modifier Optional modifier for outer layout constraints.
 */
@Composable
fun FloatingTimerBubbleContent(
    info: AppTimerInfo,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingMillis = info.remainingMillis.coerceAtLeast(0L)
    val totalLimitMillis = info.totalLimitMillis.coerceAtLeast(1L)
    val progress = (remainingMillis.toFloat() / totalLimitMillis.toFloat()).coerceIn(0f, 1f)

    // Color urgency mapping
    val isCritical = remainingMillis < 60_000L // Under 1 minute
    val isWarning = !isCritical && remainingMillis < 300_000L // Under 5 minutes

    val targetContainerColor = when {
        isCritical -> MaterialTheme.colorScheme.errorContainer
        isWarning -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val targetContentColor = when {
        isCritical -> MaterialTheme.colorScheme.onErrorContainer
        isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val targetAccentColor = when {
        isCritical -> MaterialTheme.colorScheme.error
        isWarning -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor by animateColorAsState(targetContainerColor, label = "containerColor")
    val contentColor by animateColorAsState(targetContentColor, label = "contentColor")
    val accentColor by animateColorAsState(targetAccentColor, label = "accentColor")

    // Subtle pulsing animation when time is critical (< 1 min)
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by if (isCritical) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Surface(
        modifier = modifier
            .scale(pulseScale)
            .clip(if (isExpanded) RoundedCornerShape(20.dp) else CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleExpand
            )
            .semantics {
                contentDescription = if (isExpanded) {
                    "${info.appLabel} timer: ${formatRemainingDetailed(remainingMillis)} remaining, tap to collapse"
                } else {
                    "${info.appLabel} timer: ${formatRemainingCompact(remainingMillis)} remaining, tap to expand"
                }
            },
        shape = if (isExpanded) RoundedCornerShape(20.dp) else CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 8.dp,
        tonalElevation = 6.dp
    ) {
        AnimatedContent(
            targetState = isExpanded,
            label = "bubbleExpandTransition"
        ) { expanded ->
            if (expanded) {
                ExpandedTimerView(
                    info = info,
                    remainingMillis = remainingMillis,
                    progress = progress,
                    accentColor = accentColor,
                    contentColor = contentColor
                )
            } else {
                CompactTimerView(
                    remainingMillis = remainingMillis,
                    progress = progress,
                    accentColor = accentColor,
                    contentColor = contentColor
                )
            }
        }
    }
}

/**
 * Compact circular time ring view for minimal screen obstruction.
 */
@Composable
private fun CompactTimerView(
    remainingMillis: Long,
    progress: Float,
    accentColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.2f),
            strokeWidth = 3.5.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatRemainingCompact(remainingMillis),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Expanded pill card view displaying detailed information about the app and active rule.
 */
@Composable
private fun ExpandedTimerView(
    info: AppTimerInfo,
    remainingMillis: Long,
    progress: Float,
    accentColor: Color,
    contentColor: Color
) {
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 240.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = info.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.UnfoldLess,
                contentDescription = "Collapse timer",
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = formatRemainingDetailed(remainingMillis),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )

            val ruleLabel = when (info.ruleType) {
                BlockRuleType.DAILY_QUOTA -> "Daily Quota"
                BlockRuleType.SESSION_LIMIT -> "Session Limit"
                else -> "Limit"
            }
            Text(
                text = ruleLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f)
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.2f)
        )
    }
}

/** Formats milliseconds into compact label (e.g., "1h 15m", "14m", "45s"). */
fun formatRemainingCompact(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

/** Formats milliseconds into detailed countdown (e.g., "1:15:30" or "14:05"). */
fun formatRemainingDetailed(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

// ── Composable Previews ────────────────────────────────────────────────────────

@Preview(name = "Compact Normal Light", showBackground = true)
@Composable
private fun CompactNormalPreviewLight() {
    MultiToolTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingTimerBubbleContent(
                info = AppTimerInfo(
                    packageName = "com.instagram.android",
                    appLabel = "Instagram",
                    ruleType = BlockRuleType.DAILY_QUOTA,
                    remainingMillis = 15 * 60_000L,
                    totalLimitMillis = 30 * 60_000L,
                    groupName = "Social"
                ),
                isExpanded = false,
                onToggleExpand = {}
            )
        }
    }
}

@Preview(name = "Compact Warning Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CompactWarningPreviewDark() {
    MultiToolTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingTimerBubbleContent(
                info = AppTimerInfo(
                    packageName = "com.instagram.android",
                    appLabel = "Instagram",
                    ruleType = BlockRuleType.SESSION_LIMIT,
                    remainingMillis = 3 * 60_000L,
                    totalLimitMillis = 15 * 60_000L,
                    groupName = "Social"
                ),
                isExpanded = false,
                onToggleExpand = {}
            )
        }
    }
}

@Preview(name = "Expanded Normal Light", showBackground = true)
@Composable
private fun ExpandedNormalPreviewLight() {
    MultiToolTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingTimerBubbleContent(
                info = AppTimerInfo(
                    packageName = "com.instagram.android",
                    appLabel = "Instagram",
                    ruleType = BlockRuleType.DAILY_QUOTA,
                    remainingMillis = 14 * 60_000L + 25_000L,
                    totalLimitMillis = 30 * 60_000L,
                    groupName = "Social"
                ),
                isExpanded = true,
                onToggleExpand = {}
            )
        }
    }
}

@Preview(name = "Expanded Critical Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpandedCriticalPreviewDark() {
    MultiToolTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingTimerBubbleContent(
                info = AppTimerInfo(
                    packageName = "com.instagram.android",
                    appLabel = "Instagram",
                    ruleType = BlockRuleType.DAILY_QUOTA,
                    remainingMillis = 42_000L,
                    totalLimitMillis = 30 * 60_000L,
                    groupName = "Social"
                ),
                isExpanded = true,
                onToggleExpand = {}
            )
        }
    }
}
