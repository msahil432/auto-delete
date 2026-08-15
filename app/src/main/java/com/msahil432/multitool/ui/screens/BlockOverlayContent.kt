package com.msahil432.multitool.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.blocking.BlockOverlayManager
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Full-screen blocking overlay UI displaying why an app is blocked,
 * with primary "Go back" action and optional "Unlock anyway" friction challenge.
 */
@Composable
fun BlockOverlayContent(
    info: BlockOverlayManager.BlockInfo,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    onUnlockAnyway: (() -> Unit)? = null
) {
    // Intercept back gesture / press to navigate home
    BackHandler(enabled = true) {
        onGoBack()
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live countdown timer if endsAtMillis is provided
    val endsAtMillis = info.endsAtMillis
    if (endsAtMillis != null && endsAtMillis > nowMillis) {
        LaunchedEffect(endsAtMillis) {
            while (true) {
                delay(1000L)
                nowMillis = System.currentTimeMillis()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Centered large blocked icon
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "App Blocked Icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(80.dp)
                    .semantics {
                        contentDescription = "Application blocked"
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App Label
            Text(
                text = info.appLabel.ifBlank { info.packageName },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reason for block with accessibility live region announcement
            Text(
                text = info.reason,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                }
            )

            // Optional Quota Progress Card
            if (info.usedSeconds != null && info.limitSeconds != null && info.limitSeconds > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                val progress = (info.usedSeconds.toFloat() / info.limitSeconds.toFloat()).coerceIn(0f, 1f)
                QuotaProgressCard(
                    usedSeconds = info.usedSeconds,
                    limitSeconds = info.limitSeconds,
                    progress = progress
                )
            }

            // Optional Countdown Card
            if (endsAtMillis != null && endsAtMillis > nowMillis) {
                Spacer(modifier = Modifier.height(16.dp))
                val remainingSeconds = ((endsAtMillis - nowMillis) / 1000L).coerceAtLeast(0L)
                CountdownCard(remainingSeconds = remainingSeconds)
            }

            Spacer(modifier = Modifier.height(36.dp))
            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Primary Action Button: "Go back"
            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = "Go back",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Secondary Action Button: "Unlock anyway" (Friction Challenge)
            if (info.allowFriction && onUnlockAnyway != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUnlockAnyway,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onErrorContainer),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = "Unlock anyway",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaProgressCard(
    usedSeconds: Long,
    limitSeconds: Long,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Usage vs Daily Quota",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(usedSeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Limit: ${formatDuration(limitSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun CountdownCard(
    remainingSeconds: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = "Block ends in",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCountdown(remainingSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val totalMins = seconds / 60
    val hours = totalMins / 60
    val mins = totalMins % 60
    return if (hours > 0) {
        "${hours}h ${mins}m"
    } else {
        "${mins}m"
    }
}

private fun formatCountdown(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}

@Preview(showBackground = true, name = "Block Overlay Light - Simple")
@Composable
private fun BlockOverlayContentPreviewLight() {
    MultiToolTheme {
        BlockOverlayContent(
            info = BlockOverlayManager.BlockInfo(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                reason = "Blocked by Work Hours schedule",
                allowFriction = true
            ),
            onGoBack = {},
            onUnlockAnyway = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Block Overlay Dark - Quota & Timer")
@Composable
private fun BlockOverlayContentPreviewDark() {
    MultiToolTheme {
        BlockOverlayContent(
            info = BlockOverlayManager.BlockInfo(
                packageName = "com.zhiliaoapp.musically",
                appLabel = "TikTok",
                reason = "Daily limit reached",
                allowFriction = false,
                usedSeconds = 3600L,
                limitSeconds = 3600L,
                endsAtMillis = System.currentTimeMillis() + 1800_000L
            ),
            onGoBack = {}
        )
    }
}
