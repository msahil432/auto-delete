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
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.ActivityLogEntry
import com.msahil432.multitool.data.LogAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(appDao: AppDao, onBack: () -> Unit) {
    val logs by appDao.getAllActivityLogs().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    
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
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No activity yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(logs) { log ->
                    ActivityLogItem(log, onUndo = {
                        coroutineScope.launch {
                            // Only undo TRASHED items (restore from trash)
                            if (log.action == com.msahil432.multitool.data.LogAction.TRASHED) {
                                // Implement MediaStore un-trash here if needed
                                // Mark as restored
                                appDao.updateActivityLog(log.copy(action = com.msahil432.multitool.data.LogAction.RESTORED))
                            }
                        }
                    })
                }
            }
        }
    }
}

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
