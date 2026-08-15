package com.msahil432.multitool.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun ConfirmDialog(
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  dismissLabel: String = "Cancel"
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(confirmLabel) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(dismissLabel) }
    }
  )
}

@Preview(showBackground = true, name = "ConfirmDialog Light")
@Composable
private fun ConfirmDialogPreviewLight() {
  MultiToolTheme {
    ConfirmDialog(
      title = "Delete rule?",
      text = "This action cannot be undone. The blocking rule will be permanently removed.",
      confirmLabel = "Delete",
      onConfirm = {},
      onDismiss = {}
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "ConfirmDialog Dark")
@Composable
private fun ConfirmDialogPreviewDark() {
  MultiToolTheme {
    ConfirmDialog(
      title = "Disable Strict Mode?",
      text = "Disabling strict mode will remove all active blocks immediately.",
      confirmLabel = "Disable",
      onConfirm = {},
      onDismiss = {}
    )
  }
}
