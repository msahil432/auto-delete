package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.DeletionMode
import com.msahil432.multitool.data.FolderConfig
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.DEFAULT_EXCLUSION_RULES
import com.msahil432.multitool.data.DEFAULT_TIME_PRESETS
import com.msahil432.multitool.data.encodeFilterRules
import com.msahil432.multitool.data.encodeTimePeriodPresets
import com.msahil432.multitool.ui.components.EmptyState
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesHomeScreen(
  settingsRepository: SettingsRepository,
  appDao: AppDao,
  innerPadding: PaddingValues,
  onNavigateToFolder: (Long) -> Unit,
  onNavigateToActivityLog: () -> Unit
) {
  val folderConfigs by appDao.getAllFolderConfigs().collectAsState(initial = emptyList())
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Files", style = MaterialTheme.typography.headlineSmall) },
        actions = {
          IconButton(onClick = onNavigateToActivityLog) {
            Icon(Icons.Default.History, contentDescription = "Activity Log")
          }
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = {
        coroutineScope.launch {
          val defaultMode = settingsRepository.globalDeletionMode.firstOrNull() ?: "TRASH"
          val defaultPresets = encodeTimePeriodPresets(DEFAULT_TIME_PRESETS)
          val newId = appDao.insertFolderConfig(
            FolderConfig(
              path = "/storage/emulated/0/Download",
              displayName = "New Folder",
              isDefaultScreenshotsFolder = false,
              enabled = true,
              deletionMode = DeletionMode.valueOf(defaultMode),
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
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = combinedPadding
    ) {
      item {
        PermissionHealthBanner(
          onFixPermissions = { /* settings tab handles this via its own permissions route */ },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
      }
      if (folderConfigs.isEmpty()) {
        item {
          EmptyState(
            icon = Icons.Default.Add,
            title = "No folders monitored",
            message = "Tap + to add a folder and start auto-deleting files on a schedule."
          )
        }
      } else {
        items(folderConfigs) { config ->
          FolderConfigItem(
            config = config,
            onClick = { onNavigateToFolder(config.id) },
            onToggle = { enabled ->
              coroutineScope.launch { appDao.updateFolderConfig(config.copy(enabled = enabled)) }
            }
          )
        }
      }
    }
  }
}
