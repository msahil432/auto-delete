package com.msahil432.multitool.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.ui.screens.AppSettingsScreen
import com.msahil432.multitool.ui.screens.BlockingHomeScreen
import com.msahil432.multitool.ui.screens.FilesHomeScreen
import com.msahil432.multitool.ui.screens.FolderDetailScreen
import com.msahil432.multitool.ui.screens.ActivityLogScreen
import com.msahil432.multitool.ui.screens.OnboardingScreen
import com.msahil432.multitool.ui.screens.PermissionCheckScreen
import com.msahil432.multitool.ui.screens.UsageHomeScreen
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.BrowsingRepository
import com.msahil432.multitool.data.GeofenceRepository
import com.msahil432.multitool.data.NotificationRepository
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.ui.screens.BlockGroupEditScreen
import com.msahil432.multitool.ui.screens.BrowsingHistoryScreen
import com.msahil432.multitool.ui.screens.GeofenceEditScreen
import com.msahil432.multitool.ui.screens.GeofenceProfilesScreen
import com.msahil432.multitool.ui.screens.NotificationVaultScreen
import com.msahil432.multitool.ui.screens.StrictModeScreen
import com.msahil432.multitool.ui.screens.UsageTimelineScreen
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BottomNavScaffold(
  settingsRepository: SettingsRepository,
  appDao: AppDao,
  usageRepository: UsageRepository,
  blockingRepository: BlockingRepository,
  browsingRepository: BrowsingRepository,
  notificationRepository: NotificationRepository,
  geofenceRepository: GeofenceRepository
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  // ── Module activation states ──────────────────────────────────────────────
  val isFileCleanupActive by settingsRepository.moduleFileCleanup.collectAsState(initial = false)
  val isUsageStatsActive by settingsRepository.moduleUsageStats.collectAsState(initial = false)
  val isAppFocusActive by settingsRepository.moduleAppFocus.collectAsState(initial = false)

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      NavigationBar {
        TopLevelDest.entries.forEach { dest ->
          val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
          NavigationBarItem(
            icon = {
              Icon(
                imageVector = dest.icon,
                contentDescription = dest.label
              )
            },
            label = { Text(dest.label) },
            selected = selected,
            onClick = {
              navController.navigate(dest.route) {
                // Pop up to the start destination to avoid building up a large back stack
                popUpTo(navController.graph.findStartDestination().id) {
                  saveState = true
                }
                launchSingleTop = true
                restoreState = true
              }
            }
          )
        }
      }
    }
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = TopLevelDest.FILES.route,
      modifier = Modifier.fillMaxSize()
    ) {
      // ── Files tab ────────────────────────────────────────────────────────────
      composable(TopLevelDest.FILES.route) {
        FilesHomeScreen(
          settingsRepository = settingsRepository,
          appDao = appDao,
          innerPadding = innerPadding,
          isModuleActive = isFileCleanupActive,
          onNavigateToFolder = { folderId -> navController.navigate("folder/$folderId") },
          onNavigateToActivityLog = { navController.navigate("activity_log") }
        )
      }
      composable("folder/{folderId}") { backStackEntry ->
        val folderId = backStackEntry.arguments?.getString("folderId")?.toLongOrNull()
          ?: return@composable
        FolderDetailScreen(
          folderId = folderId,
          appDao = appDao,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }
      composable("activity_log") {
        ActivityLogScreen(
          appDao = appDao,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Usage tab ────────────────────────────────────────────────────────────
      composable(TopLevelDest.USAGE.route) {
        UsageHomeScreen(
          usageRepository = usageRepository,
          innerPadding = innerPadding,
          isModuleActive = isUsageStatsActive,
          onNavigateToTimeline = { navController.navigate("usage_timeline") },
          onActivateModule = {
            CoroutineScope(Dispatchers.Main).launch {
              settingsRepository.setModuleUsageStats(true)
            }
          }
        )
      }
      composable("usage_timeline") {
        UsageTimelineScreen(
          usageRepository = usageRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Blocking tab ─────────────────────────────────────────────────────────
      composable(TopLevelDest.BLOCKING.route) {
        BlockingHomeScreen(
          blockingRepository = blockingRepository,
          innerPadding = innerPadding,
          isModuleActive = isAppFocusActive,
          onNavigateToGroup = { groupId -> navController.navigate("block_group/$groupId") },
          onNavigateToGeofences = { navController.navigate("geofence_profiles") },
          onNavigateToStrictMode = { navController.navigate("strict_mode") },
          onActivateModule = {
            CoroutineScope(Dispatchers.Main).launch {
              settingsRepository.setModuleAppFocus(true)
            }
          }
        )
      }
      composable("block_group/{groupId}") { backStackEntry ->
        val groupId = backStackEntry.arguments?.getString("groupId")?.toLongOrNull()
          ?: return@composable
        BlockGroupEditScreen(
          groupId = groupId,
          blockingRepository = blockingRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }
      composable("geofence_profiles") {
        GeofenceProfilesScreen(
          geofenceRepository = geofenceRepository,
          blockingRepository = blockingRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() },
          onNavigateToEdit = { profileId -> navController.navigate("geofence_edit/$profileId") }
        )
      }
      composable("geofence_edit/{profileId}") { backStackEntry ->
        val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull()
          ?: return@composable
        GeofenceEditScreen(
          profileId = profileId,
          geofenceRepository = geofenceRepository,
          blockingRepository = blockingRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }
      composable("strict_mode") {
        StrictModeScreen(
          settingsRepository = settingsRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Settings tab ─────────────────────────────────────────────────────────
      composable(TopLevelDest.SETTINGS.route) {
        AppSettingsScreen(
          settingsRepository = settingsRepository,
          innerPadding = innerPadding,
          isModuleFileCleanupActive = isFileCleanupActive,
          isModuleUsageStatsActive = isUsageStatsActive,
          isModuleAppFocusActive = isAppFocusActive,
          onNavigateToPermissions = { navController.navigate("permissions") },
          onNavigateToBrowsingHistory = { navController.navigate("browsing_history") },
          onNavigateToNotificationVault = { navController.navigate("notification_vault") },
          onNavigateToGeofences = { navController.navigate("geofence_profiles") },
          onNavigateToStrictMode = { navController.navigate("strict_mode") },
          onActivateAppFocus = {
            CoroutineScope(Dispatchers.Main).launch {
              settingsRepository.setModuleAppFocus(true)
              navController.navigate(TopLevelDest.BLOCKING.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            }
          }
        )
      }
      composable("permissions") {
        PermissionCheckScreen(
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }
      composable("browsing_history") {
        BrowsingHistoryScreen(
          browsingRepository = browsingRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }
      composable("notification_vault") {
        NotificationVaultScreen(
          notificationRepository = notificationRepository,
          innerPadding = innerPadding,
          onBack = { navController.popBackStack() }
        )
      }
    }
  }
}

@Preview(name = "BottomNavScaffold Light")
@Composable
private fun BottomNavScaffoldPreviewLight() {
  // Preview is structural only — data dependencies are not wired
  MultiToolTheme {
    Scaffold(
      bottomBar = {
        NavigationBar {
          TopLevelDest.entries.forEach { dest ->
            NavigationBarItem(
              icon = { Icon(dest.icon, contentDescription = dest.label) },
              label = { Text(dest.label) },
              selected = dest == TopLevelDest.FILES,
              onClick = {}
            )
          }
        }
      }
    ) { _ -> }
  }
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "BottomNavScaffold Dark")
@Composable
private fun BottomNavScaffoldPreviewDark() {
  MultiToolTheme {
    Scaffold(
      bottomBar = {
        NavigationBar {
          TopLevelDest.entries.forEach { dest ->
            NavigationBarItem(
              icon = { Icon(dest.icon, contentDescription = dest.label) },
              label = { Text(dest.label) },
              selected = dest == TopLevelDest.SETTINGS,
              onClick = {}
            )
          }
        }
      }
    ) { _ -> }
  }
}
