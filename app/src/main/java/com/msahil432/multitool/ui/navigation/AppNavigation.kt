package com.msahil432.multitool.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.ui.screens.OnboardingScreen
import com.msahil432.multitool.ui.screens.SettingsScreen
import com.msahil432.multitool.ui.screens.FolderDetailScreen
import com.msahil432.multitool.ui.screens.ActivityLogScreen
import com.msahil432.multitool.ui.screens.PermissionCheckScreen

@Composable
fun AppNavigation(
    settingsRepository: SettingsRepository,
    appDao: AppDao
) {
    val navController = rememberNavController()
    val onboardingComplete by settingsRepository.onboardingComplete.collectAsState(initial = false)
    
    // We need to wait for initial state, but for simplicity we assume false if not loaded
    val startDestination = if (onboardingComplete) "settings" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                settingsRepository = settingsRepository,
                appDao = appDao,
                onComplete = {
                    navController.navigate("settings") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsRepository = settingsRepository,
                appDao = appDao,
                onNavigateToFolder = { folderId ->
                    navController.navigate("folder/$folderId")
                },
                onNavigateToActivityLog = {
                    navController.navigate("activity_log")
                },
                onNavigateToPermissions = {
                    navController.navigate("permissions")
                }
            )
        }
        composable("folder/{folderId}") { backStackEntry ->
            val folderIdStr = backStackEntry.arguments?.getString("folderId")
            val folderId = folderIdStr?.toLongOrNull() ?: return@composable
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
        composable("permissions") {
            PermissionCheckScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
