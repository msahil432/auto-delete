package com.msahil432.multitool.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.data.TranscodeStatus
import com.msahil432.multitool.downloader.formatFileSize
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Card composable presenting an individual download's progress, status, and contextual actions.
 */
@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onReloadSourcePage: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryTranscode: () -> Unit = {}
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                contentDescription = "Download ${item.fileName}, status ${item.status.name}"
            },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (item.status) {
                        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                        DownloadStatus.FAILED -> Icons.Default.Error
                        DownloadStatus.DOWNLOADING -> Icons.Default.Download
                        DownloadStatus.QUEUED -> Icons.Default.Schedule
                        DownloadStatus.CANCELLED -> Icons.Default.Cancel
                    },
                    contentDescription = null,
                    tint = when (item.status) {
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatSubtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isTranscoded = item.status == DownloadStatus.COMPLETED &&
                    (item.transcodeStatus == TranscodeStatus.COMPLETED || item.transcodeStatus == TranscodeStatus.ALREADY_H265)

                val chipLabel = when {
                    isTranscoded -> "TRANSCODED"
                    item.status == DownloadStatus.COMPLETED && item.transcodeStatus == TranscodeStatus.TRANSCODING -> "TRANSCODING"
                    else -> item.status.name
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = chipLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = when {
                            chipLabel == "TRANSCODED" || item.status == DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            item.status == DownloadStatus.FAILED || item.transcodeStatus == TranscodeStatus.FAILED -> MaterialTheme.colorScheme.error
                            item.status == DownloadStatus.DOWNLOADING || item.transcodeStatus == TranscodeStatus.TRANSCODING -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }

            // Progress or Error state
            when {
                item.status == DownloadStatus.DOWNLOADING -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (item.progressPercent >= 0) {
                        LinearProgressIndicator(
                            progress = { item.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (item.progressPercent >= 0) "${item.progressPercent}%" else "Downloading stream...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.transcodeStatus == TranscodeStatus.TRANSCODING -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Converting to H.265...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                item.status == DownloadStatus.FAILED -> {
                    if (!item.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item.transcodeStatus == TranscodeStatus.FAILED && !item.errorMessage.isNullOrBlank() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> { /* No progress bar for completed/queued/cancelled */ }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contextual Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onReloadSourcePage) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload Source Page")
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onReloadSourcePage) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload Source Page")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete File")
                        }
                        if (item.transcodeStatus == TranscodeStatus.FAILED) {
                            OutlinedButton(
                                onClick = onRetryTranscode,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry H.265")
                            }
                        }
                        Button(onClick = onOpen) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onReloadSourcePage) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload Source Page")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Record")
                        }
                        Button(onClick = onRetry) {
                            Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                    DownloadStatus.CANCELLED -> {
                        IconButton(onClick = onReloadSourcePage) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload Source Page")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Record")
                        }
                        OutlinedButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

private fun formatSubtitle(item: DownloadItem): String {
    val sizeText = when {
        item.transcodeStatus == TranscodeStatus.COMPLETED && item.originalSizeBytes > 0 && item.compressedSizeBytes > 0 ->
            "${formatFileSize(item.originalSizeBytes)} → ${formatFileSize(item.compressedSizeBytes)} (h265)"
        item.transcodeStatus == TranscodeStatus.ALREADY_H265 && item.totalBytes > 0 ->
            "${formatFileSize(item.totalBytes)} (Already H.265)"
        item.transcodeStatus == TranscodeStatus.SKIPPED_LARGER && item.originalSizeBytes > 0 && item.compressedSizeBytes > 0 ->
            "${formatFileSize(item.originalSizeBytes)} (Kept original, H.265 was larger: ${formatFileSize(item.compressedSizeBytes)})"
        item.transcodeStatus == TranscodeStatus.SKIPPED_LARGER && item.totalBytes > 0 ->
            "${formatFileSize(item.totalBytes)} (Kept original, H.265 was larger)"
        item.transcodeStatus == TranscodeStatus.TRANSCODING ->
            "${formatFileSize(item.totalBytes)} (transcoding…)"
        item.transcodeStatus == TranscodeStatus.FAILED && item.totalBytes > 0 ->
            "${formatFileSize(item.totalBytes)} (H.265 failed, kept original)"
        item.totalBytes > 0 -> formatFileSize(item.totalBytes)
        item.downloadedBytes > 0 -> formatFileSize(item.downloadedBytes)
        else -> item.mediaType.name.replace("_", " ")
    }
    return "$sizeText • ${item.mediaType.name}"
}

@Preview(showBackground = true, name = "DownloadItemCard Light")
@Composable
private fun DownloadItemCardPreviewLight() {
    MultiToolTheme {
        DownloadItemCard(
            item = DownloadItem(
                id = 1,
                sourcePageUrl = "https://example.com",
                mediaUrl = "https://example.com/video.mp4",
                fileName = "sample_clip.mp4",
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                status = DownloadStatus.DOWNLOADING,
                progressPercent = 64,
                totalBytes = 15_000_000,
                downloadedBytes = 9_600_000,
                createdAt = 1000L,
                updatedAt = 1000L
            ),
            onRetry = {},
            onCancel = {},
            onDelete = {},
            onOpen = {},
            onReloadSourcePage = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DownloadItemCard Dark")
@Composable
private fun DownloadItemCardPreviewDark() {
    MultiToolTheme {
        DownloadItemCard(
            item = DownloadItem(
                id = 2,
                sourcePageUrl = "https://example.com",
                mediaUrl = "https://example.com/video.mp4",
                fileName = "completed_video.mp4",
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                status = DownloadStatus.COMPLETED,
                progressPercent = 100,
                totalBytes = 25_000_000,
                downloadedBytes = 25_000_000,
                createdAt = 1000L,
                updatedAt = 1000L
            ),
            onRetry = {},
            onCancel = {},
            onDelete = {},
            onOpen = {},
            onReloadSourcePage = {}
        )
    }
}
