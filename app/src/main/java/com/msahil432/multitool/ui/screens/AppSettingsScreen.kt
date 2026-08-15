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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
  settingsRepository: SettingsRepository,
  innerPadding: PaddingValues,
  onNavigateToPermissions: () -> Unit
) {
  val globalDeletionMode by settingsRepository.globalDeletionMode.collectAsState(initial = "TRASH")
  val globalDefaultPool by settingsRepository.globalDefaultPool.collectAsState(initial = "")

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
