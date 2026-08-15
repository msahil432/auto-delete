package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.ui.components.SectionHeader
import com.msahil432.multitool.ui.components.SettingRow

import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
  settingsRepository: SettingsRepository,
  innerPadding: PaddingValues,
  onNavigateToPermissions: () -> Unit,
  onNavigateToBrowsingHistory: (() -> Unit)? = null
) {
  val coroutineScope = rememberCoroutineScope()
  val globalDeletionMode by settingsRepository.globalDeletionMode.collectAsState(initial = "TRASH")
  val globalDefaultPool by settingsRepository.globalDefaultPool.collectAsState(initial = "")
  val blockYtShorts by settingsRepository.blockYtShorts.collectAsState(initial = false)
  val blockIgReels by settingsRepository.blockIgReels.collectAsState(initial = false)
  val blockFbReels by settingsRepository.blockFbReels.collectAsState(initial = false)
  val trackBrowserUrls by settingsRepository.trackBrowserUrls.collectAsState(initial = false)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
      )
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(combinedPadding)
        .verticalScroll(rememberScrollState())
    ) {
      // ── Permissions ────────────────────────────────────────────────────────
      SectionHeader(title = "Permissions")
      SettingRow(
        title = "Manage Permissions",
        subtitle = "Review and grant required app permissions",
        leadingIcon = Icons.Default.Security,
        onClick = onNavigateToPermissions
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

      // ── Short-Form Video Blocker ──────────────────────────────────────────
      SectionHeader(title = "Short-Form Video Blocker")
      Text(
        text = "Main feed and search stay available.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
      )
      SettingRow(
        title = "Block YouTube Shorts",
        subtitle = "Automatically dismisses YouTube Shorts",
        leadingIcon = Icons.Default.PlayCircleOutline,
        trailing = {
          Switch(
            checked = blockYtShorts,
            onCheckedChange = { checked ->
              coroutineScope.launch { settingsRepository.setBlockYtShorts(checked) }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Block YouTube Shorts"
            }
          )
        }
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
      SettingRow(
        title = "Block Instagram Reels",
        subtitle = "Automatically dismisses Instagram Reels",
        leadingIcon = Icons.Default.PlayCircleOutline,
        trailing = {
          Switch(
            checked = blockIgReels,
            onCheckedChange = { checked ->
              coroutineScope.launch { settingsRepository.setBlockIgReels(checked) }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Block Instagram Reels"
            }
          )
        }
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
      SettingRow(
        title = "Block Facebook Reels",
        subtitle = "Automatically dismisses Facebook Reels",
        leadingIcon = Icons.Default.PlayCircleOutline,
        trailing = {
          Switch(
            checked = blockFbReels,
            onCheckedChange = { checked ->
              coroutineScope.launch { settingsRepository.setBlockFbReels(checked) }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Block Facebook Reels"
            }
          )
        }
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

      // ── Browser URL & Search Tracker ─────────────────────────────────────
      SectionHeader(title = "Browser Activity Tracker")
      Text(
        text = "Stored only on this device. Passwords are never recorded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
      )
      SettingRow(
        title = "Track browser URLs & searches",
        subtitle = "Logs visited domains and search queries from supported browsers",
        leadingIcon = Icons.Default.Public,
        trailing = {
          Switch(
            checked = trackBrowserUrls,
            onCheckedChange = { checked ->
              coroutineScope.launch { settingsRepository.setTrackBrowserUrls(checked) }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Track browser URLs & searches"
            }
          )
        }
      )
      if (onNavigateToBrowsingHistory != null) {
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingRow(
          title = "Browsing Activity Log",
          subtitle = "View recorded domains and searches",
          leadingIcon = Icons.Default.Language,
          onClick = onNavigateToBrowsingHistory
        )
      }
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

      // ── Global defaults (read-only placeholders — editable in future specs) ──
      SectionHeader(title = "Global Defaults")
      SettingRow(
        title = "Default Deletion Mode",
        subtitle = globalDeletionMode.lowercase().replaceFirstChar { it.uppercase() },
        leadingIcon = Icons.Default.DeleteForever
        // No onClick — read-only until a future spec adds the editor
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
      SettingRow(
        title = "Default Time Pool",
        subtitle = if (globalDefaultPool.isNotEmpty()) globalDefaultPool else "Not configured",
        leadingIcon = Icons.Default.Layers
        // No onClick — read-only until a future spec adds the editor
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
