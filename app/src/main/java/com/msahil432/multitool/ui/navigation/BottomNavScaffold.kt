package com.msahil432.multitool.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
          onBack = { navController.popBackStack() }
        )
      }
      composable("activity_log") {
        ActivityLogScreen(
          appDao = appDao,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Usage tab ────────────────────────────────────────────────────────────
      composable(TopLevelDest.USAGE.route) {
        UsageHomeScreen(
          usageRepository = usageRepository,
          innerPadding = innerPadding,
          onNavigateToTimeline = { navController.navigate("usage_timeline") }
        )
      }
      composable("usage_timeline") {
        UsageTimelineScreen(
          usageRepository = usageRepository,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Blocking tab ─────────────────────────────────────────────────────────
      composable(TopLevelDest.BLOCKING.route) {
        BlockingHomeScreen(
          blockingRepository = blockingRepository,
          innerPadding = innerPadding,
          onNavigateToGroup = { groupId -> navController.navigate("block_group/$groupId") },
          onNavigateToGeofences = { navController.navigate("geofence_profiles") },
          onNavigateToStrictMode = { navController.navigate("strict_mode") }
        )
      }
      composable("block_group/{groupId}") { backStackEntry ->
        val groupId = backStackEntry.arguments?.getString("groupId")?.toLongOrNull()
          ?: return@composable
        BlockGroupEditScreen(
          groupId = groupId,
          blockingRepository = blockingRepository,
          onBack = { navController.popBackStack() }
        )
      }
      composable("geofence_profiles") {
        GeofenceProfilesScreen(
          geofenceRepository = geofenceRepository,
          blockingRepository = blockingRepository,
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
          onBack = { navController.popBackStack() }
        )
      }
      composable("strict_mode") {
        StrictModeScreen(
          settingsRepository = settingsRepository,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Settings tab ─────────────────────────────────────────────────────────
      composable(TopLevelDest.SETTINGS.route) {
        AppSettingsScreen(
          settingsRepository = settingsRepository,
          innerPadding = innerPadding,
          onNavigateToPermissions = { navController.navigate("permissions") },
          onNavigateToBrowsingHistory = { navController.navigate("browsing_history") },
          onNavigateToNotificationVault = { navController.navigate("notification_vault") },
          onNavigateToGeofences = { navController.navigate("geofence_profiles") },
          onNavigateToStrictMode = { navController.navigate("strict_mode") }
        )
      }
      composable("permissions") {
        PermissionCheckScreen(onBack = { navController.popBackStack() })
      }
      composable("browsing_history") {
        BrowsingHistoryScreen(
          browsingRepository = browsingRepository,
          onBack = { navController.popBackStack() }
        )
      }
      composable("notification_vault") {
        NotificationVaultScreen(
          notificationRepository = notificationRepository,
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
