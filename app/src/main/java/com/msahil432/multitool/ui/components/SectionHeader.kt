package com.msahil432.multitool.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.ui.theme.MultiToolTheme

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
  )
}

@Preview(showBackground = true, name = "SectionHeader Light")
@Composable
private fun SectionHeaderPreviewLight() {
  MultiToolTheme { SectionHeader(title = "Monitored Folders") }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "SectionHeader Dark")
@Composable
private fun SectionHeaderPreviewDark() {
  MultiToolTheme { SectionHeader(title = "Monitored Folders") }
}
