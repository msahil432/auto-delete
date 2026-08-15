package com.msahil432.multitool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun ErrorState(
  message: String,
  modifier: Modifier = Modifier,
  onRetry: (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = Icons.Default.ErrorOutline,
      contentDescription = null,
      modifier = Modifier.size(56.dp),
      tint = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    if (onRetry != null) {
      Spacer(modifier = Modifier.height(20.dp))
      OutlinedButton(onClick = onRetry) {
        Text("Retry")
      }
    }
  }
}

@Preview(showBackground = true, name = "ErrorState Light")
@Composable
private fun ErrorStatePreviewLight() {
  MultiToolTheme {
    ErrorState(
      message = "Failed to load data. Check your permissions and try again.",
      onRetry = {}
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "ErrorState Dark")
@Composable
private fun ErrorStatePreviewDark() {
  MultiToolTheme {
    ErrorState(message = "Permission denied. Grant All Files Access to continue.")
  }
}
