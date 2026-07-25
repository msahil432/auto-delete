package com.msahil432.autodelete.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.msahil432.autodelete.data.Folder
import com.msahil432.autodelete.theme.AutoDeleteTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  viewModel: MainScreenViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Column(modifier = modifier) {
    androidx.compose.material3.Button(onClick = { onItemClick(com.msahil432.autodelete.HistoryRoute) }) {
      Text("View History")
    }
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
    when (state) {
      MainScreenUiState.Loading -> {
        Text("Loading...")
      }
      is MainScreenUiState.Success -> {
        MainScreen(data = (state as MainScreenUiState.Success).data)
      }
      is MainScreenUiState.Error -> {
        Text("Error loading data: ${(state as MainScreenUiState.Error).throwable.message}")
      }
    }
  }
}

@Composable
internal fun MainScreen(data: List<Folder>, modifier: Modifier = Modifier) {
  Column(modifier) { data.forEach { FolderItem(it) } }
}

@Composable
fun FolderItem(folder: Folder, modifier: Modifier = Modifier) {
  Text(text = "Folder: ${folder.path}", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  AutoDeleteTheme { MainScreen(listOf(Folder(path = "Pictures/Screenshots", defaultPeriods = "3600", deletionMode = "Permanent"))) }
}

