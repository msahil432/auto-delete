package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.admin.DeviceAdminHelper
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.ui.components.SectionHeader
import com.msahil432.multitool.ui.components.SettingRow

import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Lock
import com.msahil432.multitool.blocking.StrictModeController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
  settingsRepository: SettingsRepository,
  innerPadding: PaddingValues,
  onNavigateToPermissions: () -> Unit,
  onNavigateToBrowsingHistory: (() -> Unit)? = null,
  onNavigateToNotificationVault: (() -> Unit)? = null,
  onNavigateToGeofences: (() -> Unit)? = null,
  onNavigateToStrictMode: (() -> Unit)? = null
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()
  val isStrictModeActive by StrictModeController.isActive.collectAsState()
  val globalDeletionMode by settingsRepository.globalDeletionMode.collectAsState(initial = "TRASH")
  val globalDefaultPool by settingsRepository.globalDefaultPool.collectAsState(initial = "")
  val blockYtShorts by settingsRepository.blockYtShorts.collectAsState(initial = false)
  val blockIgReels by settingsRepository.blockIgReels.collectAsState(initial = false)
  val blockFbReels by settingsRepository.blockFbReels.collectAsState(initial = false)
  val trackBrowserUrls by settingsRepository.trackBrowserUrls.collectAsState(initial = false)
  val notificationVaultEnabled by settingsRepository.notificationVaultEnabled.collectAsState(initial = false)
  val notificationBlockedPackages by settingsRepository.notificationBlockedPackages.collectAsState(initial = emptySet())
  val tamperAlarmEnabled by settingsRepository.tamperAlarmEnabled.collectAsState(initial = false)

  var isListenerGranted by remember { mutableStateOf(com.msahil432.multitool.util.NotificationAccess.isGranted(context)) }
  var isAdminActive by remember { mutableStateOf(DeviceAdminHelper.isActive(context)) }
  var showPermissionDisclosure by remember { mutableStateOf(false) }
  var showAdminDisclosure by remember { mutableStateOf(false) }
  var showAdminDeactivateConfirm by remember { mutableStateOf(false) }
  var showAppPicker by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        isListenerGranted = com.msahil432.multitool.util.NotificationAccess.isGranted(context)
        isAdminActive = DeviceAdminHelper.isActive(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

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

      if (onNavigateToGeofences != null) {
        SettingRow(
          title = "Location Profiles",
          subtitle = "Configure geofences to toggle focus profiles by location",
          leadingIcon = androidx.compose.material.icons.filled.Place,
          onClick = onNavigateToGeofences
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
      }

      // ── Notification Interception & Vault ─────────────────────────────────
      SectionHeader(title = "Notification Interception & Vault")
      Text(
        text = "Silence notifications from restricted apps during active focus schedules and deliver a consolidated digest when restriction ends.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
      )

      if (!isListenerGranted) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
          com.msahil432.multitool.ui.components.PermissionTile(
            title = "Notification Access Required",
            description = "Notification Listener permission is needed to intercept and vault notifications during focus sessions.",
            isGranted = false,
            onGrantClick = { showPermissionDisclosure = true }
          )
        }
      }

      SettingRow(
        title = "Enable Notification Vault",
        subtitle = if (notificationVaultEnabled) "Silencing restricted apps during focus" else "Disabled",
        leadingIcon = androidx.compose.material.icons.filled.NotificationsActive,
        trailing = {
          Switch(
            checked = notificationVaultEnabled,
            onCheckedChange = { checked ->
              if (checked && !isListenerGranted) {
                showPermissionDisclosure = true
              } else {
                coroutineScope.launch { settingsRepository.setNotificationVaultEnabled(checked) }
              }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Notification Vault"
            }
          )
        }
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

      SettingRow(
        title = "Restricted Notification Apps",
        subtitle = if (notificationBlockedPackages.isEmpty()) {
          "Applies to all active block groups"
        } else {
          "${notificationBlockedPackages.size} apps explicitly restricted"
        },
        leadingIcon = androidx.compose.material.icons.filled.Apps,
        onClick = { showAppPicker = true }
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

      if (onNavigateToNotificationVault != null) {
        SettingRow(
          title = "View Notification Vault",
          subtitle = "Review held notifications and delivery digest",
          leadingIcon = androidx.compose.material.icons.filled.Inbox,
          onClick = onNavigateToNotificationVault
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
      }

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

      // ── Strict Mode & Anti-Uninstall Protection ───────────────────────────
      SectionHeader(title = "Strict Mode & Protection")
      Text(
        text = "Prevents weakening or deleting focus rules and prevents uninstalling the app during active focus sessions.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
      )
      if (onNavigateToStrictMode != null) {
        SettingRow(
          title = "Strict Mode",
          subtitle = if (isStrictModeActive) "Active — focus rules are locked" else "Inactive — tap to configure asymmetric lock-in",
          leadingIcon = Icons.Default.Lock,
          onClick = onNavigateToStrictMode
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
      }
      SettingRow(
        title = "Anti-uninstall protection",
        subtitle = if (isAdminActive) "Active (Device Admin enabled)" else "Inactive — tap to enable",
        leadingIcon = Icons.Default.AdminPanelSettings,
        trailing = {
          Switch(
            checked = isAdminActive,
            onCheckedChange = { checked ->
              if (checked) {
                showAdminDisclosure = true
              } else {
                showAdminDeactivateConfirm = true
              }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Anti-uninstall protection"
            }
          )
        },
        onClick = {
          if (!isAdminActive) {
            showAdminDisclosure = true
          } else {
            showAdminDeactivateConfirm = true
          }
        }
      )
      HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

      SettingRow(
        title = "Tamper alarm",
        subtitle = if (tamperAlarmEnabled) {
          "Sounds an audible siren if protected system settings are opened during strict mode"
        } else {
          "Disabled — sounds siren if tampering is detected during strict mode"
        },
        leadingIcon = androidx.compose.material.icons.filled.Warning,
        trailing = {
          Switch(
            checked = tamperAlarmEnabled,
            onCheckedChange = { checked ->
              coroutineScope.launch { settingsRepository.setTamperAlarmEnabled(checked) }
            },
            modifier = Modifier.semantics {
              contentDescription = "Toggle Tamper alarm"
            }
          )
        }
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

  if (showPermissionDisclosure) {
    AlertDialog(
      onDismissRequest = { showPermissionDisclosure = false },
      title = { Text("Notification Access Required") },
      text = {
        Text("Multi Tool needs notification listener access to silence and vault notifications from restricted apps during active focus schedules.\n\nAll notification titles and previews are stored exclusively on your device and are never sent anywhere.")
      },
      confirmButton = {
        Button(
          onClick = {
            showPermissionDisclosure = false
            com.msahil432.multitool.util.NotificationAccess.openSettings(context)
          }
        ) {
          Text("Open Settings")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPermissionDisclosure = false }) {
          Text("Cancel")
        }
      }
    )
  }

  if (showAdminDisclosure) {
    AlertDialog(
      onDismissRequest = { showAdminDisclosure = false },
      title = { Text("Anti-Uninstall Protection") },
      text = {
        Text("Multi Tool uses Device Administrator privileges solely to prevent the app from being uninstalled during an active strict-mode focus session.\n\nNo other device management features or policies are used. All settings and data stay completely on your device.")
      },
      confirmButton = {
        Button(
          onClick = {
            showAdminDisclosure = false
            DeviceAdminHelper.requestActivation(context)
          }
        ) {
          Text("Continue")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAdminDisclosure = false }) {
          Text("Cancel")
        }
      }
    )
  }

  if (showAdminDeactivateConfirm) {
    AlertDialog(
      onDismissRequest = { showAdminDeactivateConfirm = false },
      title = { Text("Deactivate Protection?") },
      text = {
        Text("Deactivating Device Admin will allow the app to be uninstalled even during active strict-mode focus sessions.")
      },
      confirmButton = {
        Button(
          onClick = {
            showAdminDeactivateConfirm = false
            DeviceAdminHelper.deactivate(context)
            isAdminActive = DeviceAdminHelper.isActive(context)
          }
        ) {
          Text("Deactivate")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAdminDeactivateConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }

  if (showAppPicker) {
    com.msahil432.multitool.ui.components.AppPicker(
      initialSelectedPackages = notificationBlockedPackages,
      title = "Restricted Notification Apps",
      onDismiss = { showAppPicker = false },
      onConfirm = { selected ->
        showAppPicker = false
        coroutineScope.launch {
          settingsRepository.setNotificationBlockedPackages(selected)
        }
      }
    )
  }
}

