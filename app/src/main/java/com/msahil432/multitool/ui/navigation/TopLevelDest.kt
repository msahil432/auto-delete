package com.msahil432.multitool.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDest(
  val route: String,
  val label: String,
  val icon: ImageVector
) {
  FILES("files", "Files", Icons.Default.Folder),
  // D3: BarChart used instead of QueryStats (avoids material-icons-extended dependency)
  USAGE("usage", "Usage", Icons.Default.BarChart),
  BLOCKING("blocking", "Blocking", Icons.Default.Block),
  SETTINGS("settings", "Settings", Icons.Default.Settings),
}
