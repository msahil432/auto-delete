package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.AppDao
import com.example.data.DeletionMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(folderId: Long, appDao: AppDao, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var config by remember { mutableStateOf<com.example.data.FolderConfig?>(null) }
    
    LaunchedEffect(folderId) {
        config = appDao.getFolderConfigById(folderId).firstOrNull()
    }
    
    val currentConfig = config
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentConfig?.displayName ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (currentConfig == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Folder Path", style = MaterialTheme.typography.titleSmall)
                Text(currentConfig.path, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Deletion Mode", style = MaterialTheme.typography.titleMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = currentConfig.deletionMode == DeletionMode.TRASH,
                            onClick = {
                                val updated = currentConfig.copy(deletionMode = DeletionMode.TRASH)
                                config = updated
                                coroutineScope.launch { appDao.updateFolderConfig(updated) }
                            }
                        )
                        Text("Move to Trash")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = currentConfig.deletionMode == DeletionMode.DELETE,
                            onClick = {
                                val updated = currentConfig.copy(deletionMode = DeletionMode.DELETE)
                                config = updated
                                coroutineScope.launch { appDao.updateFolderConfig(updated) }
                            }
                        )
                        Text("Delete Permanently")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = currentConfig.deletionMode == DeletionMode.ASK_AGAIN,
                            onClick = {
                                val updated = currentConfig.copy(deletionMode = DeletionMode.ASK_AGAIN)
                                config = updated
                                coroutineScope.launch { appDao.updateFolderConfig(updated) }
                            }
                        )
                        Text("Ask Again")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Candidate Time Periods", style = MaterialTheme.typography.titleMedium)
                Text(currentConfig.candidateTimePeriods, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                // Here you would add a UI to add/remove custom time periods
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Recently Used", style = MaterialTheme.typography.titleMedium)
                Text(currentConfig.recentlyUsedPeriods, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
