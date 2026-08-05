package com.msahil432.autodelete.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msahil432.autodelete.data.AppDao
import com.msahil432.autodelete.data.FolderConfig
import com.msahil432.autodelete.data.SettingsRepository
import com.msahil432.autodelete.data.DEFAULT_EXCLUSION_RULES
import com.msahil432.autodelete.data.DEFAULT_TIME_PRESETS
import com.msahil432.autodelete.data.encodeFilterRules
import com.msahil432.autodelete.data.encodeTimePeriodPresets
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    appDao: AppDao,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToActivityLog: () -> Unit
) {
    val folderConfigs by appDao.getAllFolderConfigs().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Delete") },
                actions = {
                    IconButton(onClick = onNavigateToActivityLog) {
                        Icon(Icons.Default.History, contentDescription = "Activity Log")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Here we'd show a folder picker, but for simplicity we can just navigate to a new folder setup
                // To do: Add Folder Picker
                coroutineScope.launch {
                    val defaultMode = settingsRepository.globalDeletionMode.firstOrNull() ?: "TRASH"
                    val defaultPresets = encodeTimePeriodPresets(DEFAULT_TIME_PRESETS)
                    val newId = appDao.insertFolderConfig(
                        FolderConfig(
                            path = "/storage/emulated/0/Download", // Placeholder — user can change in detail screen
                            displayName = "New Folder",
                            isDefaultScreenshotsFolder = false,
                            enabled = true,
                            deletionMode = com.msahil432.autodelete.data.DeletionMode.valueOf(defaultMode),
                            defaultActionOnIgnore = "KEEP",
                            candidateTimePeriods = defaultPresets,
                            recentlyUsedPeriods = defaultPresets,
                            fileTypeExcludeList = encodeFilterRules(DEFAULT_EXCLUSION_RULES),
                            fileTypeIncludeList = null,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    onNavigateToFolder(newId)
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "Monitored Folders",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(folderConfigs) { config ->
                FolderConfigItem(
                    config = config,
                    onClick = { onNavigateToFolder(config.id) },
                    onToggle = { enabled ->
                        coroutineScope.launch {
                            appDao.updateFolderConfig(config.copy(enabled = enabled))
                        }
                    }
                )
            }
            // Optional: Add Permissions status here
        }
    }
}

@Composable
fun FolderConfigItem(
    config: FolderConfig,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.displayName, style = MaterialTheme.typography.titleMedium)
                Text(config.path, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Action: ${config.deletionMode}", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
