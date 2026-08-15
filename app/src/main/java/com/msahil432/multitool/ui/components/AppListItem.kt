package com.msahil432.multitool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Android
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun AppListItem(
  appLabel: String,
  packageName: String,
  modifier: Modifier = Modifier,
  icon: Painter? = null,
  trailing: @Composable (() -> Unit)? = null
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // App icon
    Surface(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp)),
      color = MaterialTheme.colorScheme.surfaceVariant
    ) {
      if (icon != null) {
        Icon(
          painter = icon,
          contentDescription = null,
          modifier = Modifier.padding(8.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Icon(
          imageVector = Icons.Default.Android,
          contentDescription = null,
          modifier = Modifier.padding(8.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = appLabel,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = packageName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (trailing != null) {
      trailing()
    }
  }
}

@Preview(showBackground = true, name = "AppListItem Light")
@Composable
private fun AppListItemPreviewLight() {
  MultiToolTheme {
    Surface {
      Column {
        AppListItem(
          appLabel = "Instagram",
          packageName = "com.instagram.android",
          trailing = {
            Icon(
              Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        )
        HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
        AppListItem(
          appLabel = "YouTube",
          packageName = "com.google.android.youtube",
          trailing = { Switch(checked = true, onCheckedChange = {}) }
        )
      }
    }
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "AppListItem Dark")
@Composable
private fun AppListItemPreviewDark() {
  MultiToolTheme {
    Surface {
      AppListItem(
        appLabel = "TikTok",
        packageName = "com.zhiliaoapp.musically",
        icon = rememberVectorPainter(Icons.Default.Android),
        trailing = { Switch(checked = false, onCheckedChange = {}) }
      )
    }
  }
}
