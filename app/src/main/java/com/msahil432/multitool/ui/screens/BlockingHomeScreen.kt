package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.theme.MultiToolTheme

/** Placeholder screen — real content will be added in 10-blocking-rules-ui.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingHomeScreen(innerPadding: PaddingValues) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Blocking", style = MaterialTheme.typography.headlineSmall) }
      )
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )
    EmptyState(
      icon = Icons.Default.Block,
      title = "App blocking coming soon",
      message = "Schedules, time limits, and site blocks will appear here.",
      modifier = Modifier.padding(combinedPadding)
    )
  }
}

@Preview(showBackground = true, name = "BlockingHomeScreen Light")
@Composable
private fun BlockingHomeScreenPreviewLight() {
  MultiToolTheme { BlockingHomeScreen(innerPadding = PaddingValues()) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "BlockingHomeScreen Dark")
@Composable
private fun BlockingHomeScreenPreviewDark() {
  MultiToolTheme { BlockingHomeScreen(innerPadding = PaddingValues()) }
}
