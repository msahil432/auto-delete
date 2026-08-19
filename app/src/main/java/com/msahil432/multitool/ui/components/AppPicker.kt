package com.msahil432.multitool.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
  val packageName: String,
  val label: String,
  val icon: Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(
  initialSelectedPackages: Set<String>,
  onDismiss: () -> Unit,
  onConfirm: (Set<String>) -> Unit,
  modifier: Modifier = Modifier,
  title: String = "Select Apps",
  singleSelect: Boolean = false
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedPackages by remember { mutableStateOf(initialSelectedPackages) }
  var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    val apps = withContext(Dispatchers.IO) {
      loadInstalledApps(context)
    }
    installedApps = apps
    isLoading = false
  }

  val filteredApps = remember(installedApps, searchQuery) {
    if (searchQuery.isBlank()) {
      installedApps
    } else {
      val q = searchQuery.trim().lowercase()
      installedApps.filter {
        it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.88f)
        .clip(RoundedCornerShape(24.dp)),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(top = 20.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
      ) {
        // ── Title & Counter ──
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          if (!singleSelect) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer
            ) {
              Text(
                text = "${selectedPackages.size} selected",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Search Field ──
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("Search apps…") },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        // ── Quick Select / Deselect actions (if multi-select) ──
        if (!singleSelect && !isLoading && filteredApps.isNotEmpty()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            TextButton(
              onClick = {
                val allFiltered = filteredApps.map { it.packageName }.toSet()
                selectedPackages = selectedPackages + allFiltered
              }
            ) {
              Text("Select All", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
              onClick = {
                val allFiltered = filteredApps.map { it.packageName }.toSet()
                selectedPackages = selectedPackages - allFiltered
              }
            ) {
              Text("Deselect All", style = MaterialTheme.typography.labelMedium)
            }
          }
        } else {
          Spacer(modifier = Modifier.height(8.dp))
        }

        // ── App List ──
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          if (isLoading) {
            LoadingState()
          } else if (filteredApps.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (searchQuery.isBlank()) "No installed apps found" else "No matching apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              items(filteredApps, key = { it.packageName }) { app ->
                val isSelected = selectedPackages.contains(app.packageName)
                val toggleSelection = {
                  if (singleSelect) {
                    selectedPackages = setOf(app.packageName)
                  } else {
                    selectedPackages = if (isSelected) {
                      selectedPackages - app.packageName
                    } else {
                      selectedPackages + app.packageName
                    }
                  }
                }

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Checkbox, onClick = toggleSelection)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  // App Icon
                  Surface(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(RoundedCornerShape(10.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                  ) {
                    if (app.icon != null) {
                      Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                          .fillMaxSize()
                          .padding(4.dp)
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

                  // Label & Package
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = app.label,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = app.packageName,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  // Selection Control
                  if (singleSelect) {
                    RadioButton(
                      selected = isSelected,
                      onClick = toggleSelection
                    )
                  } else {
                    Checkbox(
                      checked = isSelected,
                      onCheckedChange = { toggleSelection() }
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Action Buttons ──
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Button(
            onClick = { onConfirm(selectedPackages) }
          ) {
            Text(if (singleSelect) "Select" else "Done (${selectedPackages.size})")
          }
        }
      }
    }
  }
}

private fun loadInstalledApps(context: Context): List<InstalledAppItem> {
  return try {
    val pm = context.packageManager
    val myPackage = context.packageName
    val seen = mutableSetOf<String>()
    val result = mutableListOf<InstalledAppItem>()

    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      PackageManager.MATCH_ALL
    } else {
      0
    }

    // 1. Query standard launcher activities
    val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
      addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val launcherResolves = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcherIntent, flags)
      }
    } catch (_: Exception) {
      emptyList()
    }

    // 2. Query Leanback launcher activities (for TV / specialized launcher apps)
    val tvIntent = Intent(Intent.ACTION_MAIN, null).apply {
      addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
    val tvResolves = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(tvIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(tvIntent, flags)
      }
    } catch (_: Exception) {
      emptyList()
    }

    for (info in (launcherResolves + tvResolves)) {
      val pkg = info.activityInfo?.packageName ?: continue
      if (pkg == myPackage || seen.contains(pkg)) continue
      seen.add(pkg)

      val label = try {
        val l = info.loadLabel(pm).toString()
        if (l.isNotBlank()) l else info.activityInfo.applicationInfo.loadLabel(pm).toString()
      } catch (_: Exception) {
        pkg
      }

      val icon = try {
        info.loadIcon(pm).toBitmapOrNull()
      } catch (_: Exception) {
        null
      }

      result.add(InstalledAppItem(packageName = pkg, label = label, icon = icon))
    }

    // 3. Fallback: Query all installed applications to catch any launchable or user apps missed by activity filters
    val installedApps = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
      }
    } catch (_: Exception) {
      emptyList()
    }

    for (appInfo in installedApps) {
      val pkg = appInfo.packageName
      if (pkg == myPackage || seen.contains(pkg)) continue

      val hasLaunchIntent = pm.getLaunchIntentForPackage(pkg) != null
      val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0

      if (hasLaunchIntent || isUserApp) {
        seen.add(pkg)
        val label = try {
          val l = appInfo.loadLabel(pm).toString()
          if (l.isNotBlank()) l else pkg
        } catch (_: Exception) {
          pkg
        }
        val icon = try {
          appInfo.loadIcon(pm).toBitmapOrNull()
        } catch (_: Exception) {
          null
        }
        result.add(InstalledAppItem(packageName = pkg, label = label, icon = icon))
      }
    }

    result.sortBy { it.label.lowercase() }
    result
  } catch (_: Exception) {
    emptyList()
  }
}

private fun Drawable.toBitmapOrNull(): Bitmap? {
  return try {
    if (this is BitmapDrawable && this.bitmap != null) {
      this.bitmap
    } else {
      val width = if (intrinsicWidth > 0) intrinsicWidth.coerceIn(48, 192) else 96
      val height = if (intrinsicHeight > 0) intrinsicHeight.coerceIn(48, 192) else 96
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      setBounds(0, 0, canvas.width, canvas.height)
      draw(canvas)
      bitmap
    }
  } catch (_: Exception) {
    null
  }
}

@Preview(showBackground = true, name = "AppPicker Light")
@Composable
private fun AppPickerPreviewLight() {
  MultiToolTheme {
    AppPicker(
      initialSelectedPackages = setOf("com.instagram.android"),
      onDismiss = {},
      onConfirm = {}
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "AppPicker Dark")
@Composable
private fun AppPickerPreviewDark() {
  MultiToolTheme {
    AppPicker(
      initialSelectedPackages = setOf("com.google.android.youtube", "com.instagram.android"),
      onDismiss = {},
      onConfirm = {}
    )
  }
}
