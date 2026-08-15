package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.theme.MultiToolTheme

/** Placeholder screen — real content will be added in 08-usage-ui.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageHomeScreen(innerPadding: PaddingValues) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Usage", style = MaterialTheme.typography.headlineSmall) }
      )
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )
    EmptyState(
      icon = Icons.Default.BarChart,
      title = "Usage tracking coming soon",
      message = "Screen-time stats and app usage dashboards will appear here.",
      modifier = Modifier.padding(combinedPadding)
    )
  }
}

@Preview(showBackground = true, name = "UsageHomeScreen Light")
@Composable
private fun UsageHomeScreenPreviewLight() {
  MultiToolTheme { UsageHomeScreen(innerPadding = PaddingValues()) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "UsageHomeScreen Dark")
@Composable
private fun UsageHomeScreenPreviewDark() {
  MultiToolTheme { UsageHomeScreen(innerPadding = PaddingValues()) }
}
