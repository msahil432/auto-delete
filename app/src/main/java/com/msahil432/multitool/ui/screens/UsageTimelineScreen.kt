package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msahil432.multitool.data.TimelineEvent
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.theme.MultiToolTheme
import com.msahil432.multitool.util.toHms
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageTimelineScreen(
  usageRepository: UsageRepository,
  onBack: () -> Unit,
  viewModel: UsageViewModel = viewModel(
    factory = UsageViewModel.Factory(usageRepository, LocalContext.current.applicationContext)
  )
) {
  val timeline by viewModel.timeline.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val appMetaCache by viewModel.appMetaCache.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Timeline", style = MaterialTheme.typography.headlineSmall) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        }
      )
    }
  ) { paddingValues ->
    UsageTimelineScreenContent(
      timeline = timeline,
      isLoading = isLoading,
      appMetaCache = appMetaCache,
      modifier = Modifier.padding(paddingValues)
    )
  }
}

@Composable
fun UsageTimelineScreenContent(
  timeline: List<TimelineEvent>,
  isLoading: Boolean,
  appMetaCache: Map<String, AppMeta>,
  modifier: Modifier = Modifier
) {
  when {
    isLoading -> {
      LoadingState(modifier = modifier)
    }
    timeline.isEmpty() -> {
      EmptyState(
        icon = Icons.Default.History,
        title = "No timeline events",
        message = "Activity will appear here as you use your device.",
        modifier = modifier
      )
    }
    else -> {
      val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
      val hourFormat = remember { SimpleDateFormat("h:00 a", Locale.getDefault()) }

      // Group timeline events reverse-chronologically by hour bucket
      val groupedEvents = remember(timeline) {
        val groups = linkedMapOf<String, MutableList<TimelineEvent>>()
        for (event in timeline) {
          val hourKey = hourFormat.format(Date(event.timestamp))
          groups.getOrPut(hourKey) { mutableListOf() }.add(event)
        }
        groups
      }

      LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
      ) {
        groupedEvents.forEach { (hourKey, eventsInHour) ->
          item(key = "header_$hourKey") {
            TimelineHourHeader(hour = hourKey)
          }

          items(eventsInHour, key = { it.id }) { event ->
            val meta = appMetaCache[event.packageName]
            val appLabel = when (event.eventType) {
              TimelineEventType.UNLOCK -> "Device Unlocked"
              TimelineEventType.GEOFENCE_ENTER -> "Entered Geofence: ${event.packageName}"
              TimelineEventType.GEOFENCE_EXIT -> "Exited Geofence: ${event.packageName}"
              else -> meta?.label ?: event.packageName
            }
            val eventTime = timeFormat.format(Date(event.timestamp))
            val durationText = event.durationMillis?.toHms()

            TimelineEventRow(
              time = eventTime,
              label = appLabel,
              eventType = event.eventType,
              duration = durationText
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun TimelineHourHeader(hour: String) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth()
  ) {
    Text(
      text = hour,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
  }
}

@Composable
private fun TimelineEventRow(
  time: String,
  label: String,
  eventType: TimelineEventType,
  duration: String?,
  modifier: Modifier = Modifier
) {
  val (icon, tint, eventDesc) = when (eventType) {
    TimelineEventType.APP_FOREGROUND -> Triple(
      Icons.Default.PlayArrow,
      MaterialTheme.colorScheme.primary,
      "App opened"
    )
    TimelineEventType.APP_BACKGROUND -> Triple(
      Icons.Default.Pause,
      MaterialTheme.colorScheme.secondary,
      "App closed"
    )
    TimelineEventType.UNLOCK -> Triple(
      Icons.Default.LockOpen,
      MaterialTheme.colorScheme.tertiary,
      "Screen unlocked"
    )
    TimelineEventType.BLOCK_INTERCEPT -> Triple(
      Icons.Default.Block,
      MaterialTheme.colorScheme.error,
      "Block intercepted"
    )
    TimelineEventType.GEOFENCE_ENTER -> Triple(
      Icons.Default.LocationOn,
      MaterialTheme.colorScheme.primary,
      "Geofence entered"
    )
    TimelineEventType.GEOFENCE_EXIT -> Triple(
      Icons.Default.LocationOff,
      MaterialTheme.colorScheme.outline,
      "Geofence exited"
    )
  }

  val semanticsText = buildString {
    append(time)
    append(", ")
    append(label)
    append(", ")
    append(eventDesc)
    if (duration != null) {
      append(", duration ")
      append(duration)
    }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .semantics(mergeDescendants = true) {
        contentDescription = semanticsText
      },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Timestamp
    Text(
      text = time,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(44.dp)
    )

    // Event Icon badge
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(tint.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = eventDesc,
        tint = tint,
        modifier = Modifier.size(20.dp)
      )
    }

    // App / Event label
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = eventDesc,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Duration badge if present
    if (duration != null) {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(start = 4.dp)
      ) {
        Text(
          text = duration,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }
  }
}

@Preview(showBackground = true, name = "UsageTimelineScreen Content Light")
@Composable
private fun UsageTimelineScreenPreviewLight() {
  MultiToolTheme {
    UsageTimelineScreenContent(
      timeline = listOf(
        TimelineEvent(
          id = 1,
          timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
          packageName = "com.google.android.youtube",
          eventType = TimelineEventType.APP_FOREGROUND,
          durationMillis = 300000L
        ),
        TimelineEvent(
          id = 2,
          timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
          packageName = "device.unlock",
          eventType = TimelineEventType.UNLOCK,
          durationMillis = null
        ),
        TimelineEvent(
          id = 3,
          timestamp = System.currentTimeMillis() - 1000 * 60 * 30,
          packageName = "com.instagram.android",
          eventType = TimelineEventType.BLOCK_INTERCEPT,
          durationMillis = null
        )
      ),
      isLoading = false,
      appMetaCache = mapOf(
        "com.google.android.youtube" to AppMeta(label = "YouTube"),
        "com.instagram.android" to AppMeta(label = "Instagram")
      )
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "UsageTimelineScreen Content Dark")
@Composable
private fun UsageTimelineScreenPreviewDark() {
  MultiToolTheme {
    UsageTimelineScreenContent(
      timeline = listOf(
        TimelineEvent(
          id = 1,
          timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
          packageName = "com.google.android.youtube",
          eventType = TimelineEventType.APP_FOREGROUND,
          durationMillis = 300000L
        )
      ),
      isLoading = false,
      appMetaCache = mapOf(
        "com.google.android.youtube" to AppMeta(label = "YouTube")
      )
    )
  }
}

@Preview(showBackground = true, name = "UsageTimelineScreen Empty")
@Composable
private fun UsageTimelineScreenEmptyPreview() {
  MultiToolTheme {
    UsageTimelineScreenContent(
      timeline = emptyList(),
      isLoading = false,
      appMetaCache = emptyMap()
    )
  }
}
