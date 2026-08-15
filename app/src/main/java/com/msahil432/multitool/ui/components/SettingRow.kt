package com.msahil432.multitool.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun SettingRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  leadingIcon: ImageVector? = null,
  onClick: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null
) {
  val rowModifier = modifier
    .fillMaxWidth()
    .defaultMinSize(minHeight = 56.dp)
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    .padding(horizontal = 16.dp, vertical = 12.dp)

  Row(
    modifier = rowModifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    if (leadingIcon != null) {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    if (trailing != null) {
      trailing()
    }
  }
}

@Preview(showBackground = true, name = "SettingRow Light")
@Composable
private fun SettingRowPreviewLight() {
  MultiToolTheme {
    Surface {
      Column {
        SettingRow(
          title = "Notifications",
          subtitle = "Allow app notifications",
          leadingIcon = Icons.Default.Notifications,
          trailing = { Switch(checked = true, onCheckedChange = {}) }
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingRow(
          title = "Theme",
          subtitle = "System default",
          leadingIcon = Icons.Default.ToggleOn,
          onClick = {}
        )
      }
    }
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "SettingRow Dark")
@Composable
private fun SettingRowPreviewDark() {
  MultiToolTheme {
    Surface {
      SettingRow(
        title = "Notifications",
        subtitle = "Allow app notifications",
        leadingIcon = Icons.Default.Notifications,
        trailing = { Switch(checked = false, onCheckedChange = {}) }
      )
    }
  }
}
