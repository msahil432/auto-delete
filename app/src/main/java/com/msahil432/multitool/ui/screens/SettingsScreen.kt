package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.FolderConfig

// NOTE: SettingsScreen composable was replaced by FilesHomeScreen (02-navigation-hub.md).
// This file now only contains shared helper composables used by FilesHomeScreen.

@Composable
fun FolderConfigItem(
    config: FolderConfig,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.displayName, style = MaterialTheme.typography.titleMedium)
                Text(config.path, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Action: ${config.deletionMode}", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

// ─── Permission health banner ─────────────────────────────────────────────────

@Composable
fun PermissionHealthBanner(
    onFixPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissions = remember { buildPermissionList() }

    // Re-evaluate every time the composable enters composition (e.g. after returning from Settings)
    val missingRequired = remember {
        derivedStateOf { permissions.filter { it.isRequired && !it.isGranted(context) } }
    }.value
    val missingOptional = remember {
        derivedStateOf { permissions.filter { !it.isRequired && !it.isGranted(context) } }
    }.value

    if (missingRequired.isEmpty() && missingOptional.isEmpty()) {
        // All clear — subtle green chip
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onFixPermissions)
                .then(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "All permissions granted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    // Warning banner
    val containerColor = if (missingRequired.isNotEmpty())
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    val contentColor = if (missingRequired.isNotEmpty())
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.secondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onFixPermissions)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (missingRequired.isNotEmpty()) Icons.Default.Warning else Icons.Default.Security,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (missingRequired.isNotEmpty())
                    "${missingRequired.size} required permission${if (missingRequired.size > 1) "s" else ""} missing"
                else
                    "${missingOptional.size} optional permission${if (missingOptional.size > 1) "s" else ""} not granted",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                "Tap to review and fix",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}


