package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.DeletionMode
import com.msahil432.multitool.data.FolderConfig
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.DEFAULT_EXCLUSION_RULES
import com.msahil432.multitool.data.DEFAULT_TIME_PRESETS
import com.msahil432.multitool.data.encodeFilterRules
import com.msahil432.multitool.data.encodeTimePeriodPresets
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.components.ModuleActivationCard
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesHomeScreen(
  settingsRepository: SettingsRepository,
  appDao: AppDao,
  innerPadding: PaddingValues,
  isModuleActive: Boolean,
  onNavigateToFolder: (Long) -> Unit,
  onNavigateToActivityLog: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val folderConfigs by appDao.getAllFolderConfigs().collectAsState(initial = emptyList())

  val addNewFolder: () -> Unit = {
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
  }

  // ── Module activation flow ────────────────────────────────────────────────
  // Tracks whether the All Files permission was granted during inline activation
  var allFilesGranted by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
      else true
    )
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
          Environment.isExternalStorageManager()
        else true
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Files", style = MaterialTheme.typography.headlineSmall) },
        actions = {
          if (isModuleActive) {
            IconButton(onClick = addNewFolder) {
              Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
            IconButton(onClick = onNavigateToActivityLog) {
              Icon(Icons.Default.History, contentDescription = "Activity Log")
            }
          }
        }
      )
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )

    if (!isModuleActive) {
      // ── Module not activated yet ─────────────────────────────────────────
      ModuleActivationCard(
        icon = Icons.Default.FolderOpen,
        title = "File Cleanup",
        tagline = "Automatically delete or move files on a schedule you define.",
        features = listOf(
          Icons.Default.FolderOpen to "Monitor Screenshots, Downloads, or any folder",
          Icons.Default.Timer to "Delete or move files after a delay you choose",
          Icons.Default.MoveToInbox to "Keep Google Photos safe with move-not-delete"
        ),
        ctaLabel = "Activate File Cleanup",
        onActivate = {
          coroutineScope.launch {
            settingsRepository.setModuleFileCleanup(true)
            // Seed the default Screenshots folder if All Files access is already granted
            if (allFilesGranted) {
              val defaultMode = settingsRepository.globalDeletionMode.firstOrNull() ?: "TRASH"
              val defaultPresets = encodeTimePeriodPresets(DEFAULT_TIME_PRESETS)
              val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
              )
              val screenshotsDir = "${picturesDir.absolutePath}/Screenshots"
              appDao.insertFolderConfig(
                FolderConfig(
                  path = screenshotsDir,
                  displayName = "Screenshots",
                  isDefaultScreenshotsFolder = true,
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
            } else {
              // Request All Files permission first
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                  context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                      .setData(Uri.parse("package:${context.packageName}"))
                  )
                } catch (_: Exception) {
                  context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
              }
            }
          }
        },
        modifier = Modifier.padding(combinedPadding)
      )
    } else {
      // ── Normal content ───────────────────────────────────────────────────
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
              icon = Icons.Default.FolderOpen,
              title = "No folders monitored",
              message = "Add a folder to start auto-deleting files on a schedule.",
              actionLabel = "Add Folder",
              onAction = addNewFolder
            )
          }
        } else {
          items(folderConfigs, key = { it.id }) { config ->
            FolderConfigItem(
              config = config,
              onClick = { onNavigateToFolder(config.id) },
              onToggle = { enabled ->
                coroutineScope.launch { appDao.updateFolderConfig(config.copy(enabled = enabled)) }
              }
            )
          }
          item {
            AddFolderCard(onClick = addNewFolder)
          }
        }
      }
    }
  }
}

@Composable
fun AddFolderCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  OutlinedCard(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    shape = RoundedCornerShape(12.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Add Folder",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Configure a new folder to monitor",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
