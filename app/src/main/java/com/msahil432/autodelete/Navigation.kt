package com.msahil432.autodelete

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.msahil432.autodelete.data.AppDatabase
import com.msahil432.autodelete.data.DefaultDataRepository
import com.msahil432.autodelete.ui.history.HistoryScreen
import com.msahil432.autodelete.ui.main.MainScreen
import com.msahil432.autodelete.ui.main.MainScreenViewModel
import com.msahil432.autodelete.ui.onboarding.OnboardingScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val startRoute = if (android.provider.Settings.canDrawOverlays(context)) Settings else Onboarding
  val backStack = rememberNavBackStack(startRoute)
  
  val database = AppDatabase.getDatabase(context)
  val repository = DefaultDataRepository(database.appDao())
  
  val factory = object : ViewModelProvider.Factory {
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
          @Suppress("UNCHECKED_CAST")
          return MainScreenViewModel(repository) as T
      }
  }
  
  val mainViewModel: MainScreenViewModel = viewModel(factory = factory)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Onboarding> {
          OnboardingScreen(onNavigate = { navKey -> 
              backStack.removeLastOrNull()
              backStack.add(navKey) 
          })
        }
        entry<Settings> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, viewModel = mainViewModel, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<HistoryRoute> {
          HistoryScreen()
        }
      },
  )
}

