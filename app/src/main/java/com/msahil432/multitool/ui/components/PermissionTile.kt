package com.msahil432.multitool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Visual tile mirroring the permission language from OnboardingScreen.
 * Shows an icon circle, required/optional badge, title, subtitle, and a grant/granted button.
 */
@Composable
fun PermissionTile(
  title: String,
  subtitle: String,
  granted: Boolean,
  icon: ImageVector,
  isRequired: Boolean = true,
  onGrant: () -> Unit,
  modifier: Modifier = Modifier
) {
  val iconBg = when {
    granted -> MaterialTheme.colorScheme.tertiaryContainer
    isRequired -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
  }
  val iconTint = when {
    granted -> MaterialTheme.colorScheme.tertiary
    isRequired -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Icon circle
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(iconBg),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (granted) Icons.Default.Check else icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(36.dp)
      )
    }

    // Required / Optional badge
    Surface(
      shape = RoundedCornerShape(50),
      color = if (isRequired) MaterialTheme.colorScheme.errorContainer
              else MaterialTheme.colorScheme.surfaceVariant,
      contentColor = if (isRequired) MaterialTheme.colorScheme.error
                     else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
      Text(
        text = if (isRequired) "Required" else "Optional",
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
      )
    }

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (granted) {
      OutlinedButton(
        onClick = onGrant,
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.tertiary
        )
      ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Granted")
      }
    } else {
      Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
        Text(if (isRequired) "Grant permission" else "Enable (optional)")
      }
    }
  }
}

@Preview(showBackground = true, name = "PermissionTile — Not Granted")
@Composable
private fun PermissionTileNotGrantedPreview() {
  MultiToolTheme {
    Surface(modifier = Modifier.padding(16.dp)) {
      PermissionTile(
        title = "All Files Access",
        subtitle = "Required to monitor folders and move/delete files.",
        granted = false,
        icon = Icons.Default.FolderOpen,
        isRequired = true,
        onGrant = {}
      )
    }
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "PermissionTile — Granted Dark")
@Composable
private fun PermissionTileGrantedDarkPreview() {
  MultiToolTheme {
    Surface(modifier = Modifier.padding(16.dp)) {
      PermissionTile(
        title = "All Files Access",
        subtitle = "Required to monitor folders and move/delete files.",
        granted = true,
        icon = Icons.Default.FolderOpen,
        isRequired = true,
        onGrant = {}
      )
    }
  }
}
