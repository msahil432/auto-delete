package com.msahil432.multitool.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.downloader.DetectedMedia
import com.msahil432.multitool.downloader.DetectionStrategy
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Composable representing a single detected media resource candidate within
 * the selection sheet.
 *
 * @param media The detected media item.
 * @param isSelected Whether the item is checked for download.
 * @param onToggle Selection toggle callback.
 * @param modifier Optional [Modifier].
 */
@Composable
fun DetectedMediaItem(
    media: DetectedMedia,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                contentDescription = "Detected media item: ${media.suggestedFileName}"
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics {
                    contentDescription = if (isSelected) "Deselect ${media.suggestedFileName}" else "Select ${media.suggestedFileName}"
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getMediaIcon(media.mediaType),
                        contentDescription = media.mediaType.name,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.suggestedFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = media.mediaType.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )

                    if (media.width > 0 && media.height > 0) {
                        Text(
                            text = "${media.width}x${media.height}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (media.mimeType != null) {
                        Text(
                            text = media.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getMediaIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.GIF, MediaType.ANIMATED_WEBP -> Icons.Default.Animation
        MediaType.HLS_STREAM, MediaType.DASH_STREAM -> Icons.Default.Stream
        MediaType.VIDEO -> Icons.Default.Movie
    }
}

@Preview(showBackground = true, name = "DetectedMediaItem Light")
@Composable
private fun DetectedMediaItemPreviewLight() {
    MultiToolTheme {
        DetectedMediaItem(
            media = DetectedMedia(
                url = "https://example.com/demo.mp4",
                mimeType = "video/mp4",
                suggestedFileName = "tutorial_presentation.mp4",
                mediaType = MediaType.VIDEO,
                detectionStrategy = DetectionStrategy.DOM_ELEMENT,
                width = 1920,
                height = 1080
            ),
            isSelected = true,
            onToggle = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DetectedMediaItem Dark")
@Composable
private fun DetectedMediaItemPreviewDark() {
    MultiToolTheme {
        DetectedMediaItem(
            media = DetectedMedia(
                url = "https://example.com/anim.gif",
                mimeType = "image/gif",
                suggestedFileName = "reaction.gif",
                mediaType = MediaType.GIF,
                detectionStrategy = DetectionStrategy.DOM_ELEMENT
            ),
            isSelected = false,
            onToggle = {}
        )
    }
}
