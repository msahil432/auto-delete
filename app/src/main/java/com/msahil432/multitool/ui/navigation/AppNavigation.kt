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

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_HUB = "hub"

@Composable
fun AppNavigation(
  settingsRepository: SettingsRepository,
  appDao: AppDao
) {
  val navController = rememberNavController()
  val onboardingComplete by settingsRepository.onboardingComplete.collectAsState(initial = false)

  val startDestination = if (onboardingComplete) ROUTE_HUB else ROUTE_ONBOARDING

  NavHost(navController = navController, startDestination = startDestination) {
    // Onboarding — shown full-screen; bottom nav is NOT visible here
    composable(ROUTE_ONBOARDING) {
      OnboardingScreen(
        settingsRepository = settingsRepository,
        appDao = appDao,
        onComplete = {
          navController.navigate(ROUTE_HUB) {
            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
          }
        }
      )
    }

    // Hub — the bottom-nav scaffold that hosts all four tabs
    composable(ROUTE_HUB) {
      BottomNavScaffold(
        settingsRepository = settingsRepository,
        appDao = appDao
      )
    }
  }
}
