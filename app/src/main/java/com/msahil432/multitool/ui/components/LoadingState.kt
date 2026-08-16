package com.msahil432.multitool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    CircularProgressIndicator()
  }
}

@Preview(showBackground = true, name = "LoadingState Light")
@Composable
private fun LoadingStatePreviewLight() {
  MultiToolTheme { LoadingState() }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "LoadingState Dark")
@Composable
private fun LoadingStatePreviewDark() {
  MultiToolTheme { LoadingState() }
}
