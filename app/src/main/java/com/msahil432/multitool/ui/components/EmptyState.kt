package com.msahil432.multitool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    if (actionLabel != null && onAction != null) {
      Spacer(modifier = Modifier.height(24.dp))
      Button(onClick = onAction) {
        Text(actionLabel)
      }
    }
  }
}

@Preview(showBackground = true, name = "EmptyState Light")
@Composable
private fun EmptyStatePreviewLight() {
  MultiToolTheme {
    EmptyState(
      icon = Icons.Default.Folder,
      title = "No folders yet",
      message = "Tap + to add a folder to monitor.",
      actionLabel = "Add folder",
      onAction = {}
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "EmptyState Dark")
@Composable
private fun EmptyStatePreviewDark() {
  MultiToolTheme {
    EmptyState(
      icon = Icons.Default.Folder,
      title = "No folders yet",
      message = "Tap + to add a folder to monitor."
    )
  }
}
