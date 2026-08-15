package com.msahil432.multitool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Stat card used by usage screens. Progress colour follows design-system semantics:
 *   < 0.8  → primary (success)
 *   ≥ 0.8  → tertiary (warning)
 *   ≥ 1.0  → error (blocked)
 */
@Composable
fun StatCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  progress: Float? = null
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (icon != null) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (progress != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val progressColor: Color = when {
          progress >= 1.0f -> MaterialTheme.colorScheme.error
          progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
          else -> MaterialTheme.colorScheme.primary
        }
        LinearProgressIndicator(
          progress = { progress.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth(),
          color = progressColor,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }
    }
  }
}

@Preview(showBackground = true, name = "StatCard Light")
@Composable
private fun StatCardPreviewLight() {
  MultiToolTheme {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StatCard(label = "Screen Time", value = "2h 34m", icon = Icons.Default.Timer, progress = 0.43f)
      StatCard(label = "Near Limit", value = "4h 12m", icon = Icons.Default.Timer, progress = 0.84f)
      StatCard(label = "Limit Reached", value = "6h 00m", icon = Icons.Default.Timer, progress = 1.0f)
    }
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "StatCard Dark")
@Composable
private fun StatCardPreviewDark() {
  MultiToolTheme {
    StatCard(
      label = "Screen Time Today",
      value = "3h 15m",
      icon = Icons.Default.Timer,
      progress = 0.65f,
      modifier = Modifier.padding(16.dp)
    )
  }
}
