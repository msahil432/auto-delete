package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.ActivityLogEntry
import com.msahil432.multitool.data.LogAction
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying the historical audit log of file actions (trashed, deleted, moved, kept, errored).
 */
@Composable
fun ActivityLogScreen(
    appDao: AppDao,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit
) {
    val logs by appDao.getAllActivityLogs().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    ActivityLogContent(
        logs = logs,
        innerPadding = innerPadding,
        onBack = onBack,
        onUndo = { log ->
            coroutineScope.launch {
                if (log.action == LogAction.TRASHED) {
                    appDao.updateActivityLog(log.copy(action = LogAction.RESTORED))
                }
            }
        }
    )
}

/**
 * Stateless content composable for [ActivityLogScreen], rendering the list of [ActivityLogEntry] items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogContent(
    logs: List<ActivityLogEntry>,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onUndo: (ActivityLogEntry) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val bottomNavPadding = maxOf(padding.calculateBottomPadding(), innerPadding.calculateBottomPadding())
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = bottomNavPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No activity yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = bottomNavPadding + 16.dp
                )
            ) {
                items(logs) { log ->
                    ActivityLogItem(log, onUndo = { onUndo(log) })
                }
            }
        }
    }
}

/**
 * Card displaying an individual [ActivityLogEntry] with timestamp and optional undo button.
 */
@Composable
fun ActivityLogItem(log: ActivityLogEntry, onUndo: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val dateString = formatter.format(Date(log.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(log.fileName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${log.action.name} - $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.action == LogAction.ERRORED)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (log.action == LogAction.ERRORED && !log.errorDetails.isNullOrBlank()) {
                    Text(
                        text = log.errorDetails,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (log.action == com.msahil432.multitool.data.LogAction.TRASHED) {
                TextButton(onClick = onUndo) {
                    Text("Undo")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "ActivityLogScreen Light")
@Composable
private fun ActivityLogScreenPreviewLight() {
    MultiToolTheme {
        ActivityLogContent(
            logs = listOf(
                ActivityLogEntry(
                    id = 1,
                    folderId = 1,
                    fileName = "screenshot_20260823.png",
                    fileUri = "/storage/emulated/0/Pictures/Screenshots/screenshot_20260823.png",
                    action = LogAction.TRASHED,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 10
                ),
                ActivityLogEntry(
                    id = 2,
                    folderId = 1,
                    fileName = "download_invoice.pdf",
                    fileUri = "/storage/emulated/0/Download/download_invoice.pdf",
                    action = LogAction.MOVED,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60
                ),
                ActivityLogEntry(
                    id = 3,
                    folderId = 1,
                    fileName = "corrupt_file.tmp",
                    fileUri = "/storage/emulated/0/Download/corrupt_file.tmp",
                    action = LogAction.ERRORED,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 120,
                    errorDetails = "Permission denied while moving file"
                )
            ),
            onBack = {},
            onUndo = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "ActivityLogScreen Dark"
)
@Composable
private fun ActivityLogScreenPreviewDark() {
    MultiToolTheme {
        ActivityLogContent(
            logs = listOf(
                ActivityLogEntry(
                    id = 1,
                    folderId = 1,
                    fileName = "screenshot_20260823.png",
                    fileUri = "/storage/emulated/0/Pictures/Screenshots/screenshot_20260823.png",
                    action = LogAction.TRASHED,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 10
                ),
                ActivityLogEntry(
                    id = 2,
                    folderId = 1,
                    fileName = "download_invoice.pdf",
                    fileUri = "/storage/emulated/0/Download/download_invoice.pdf",
                    action = LogAction.MOVED,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60
                )
            ),
            onBack = {},
            onUndo = {}
        )
    }
}

@Preview(showBackground = true, name = "ActivityLogScreen Empty")
@Composable
private fun ActivityLogScreenEmptyPreview() {
    MultiToolTheme {
        ActivityLogContent(
            logs = emptyList(),
            onBack = {},
            onUndo = {}
        )
    }
}

