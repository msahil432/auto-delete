package com.msahil432.autodelete.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("History")
        Spacer(modifier = Modifier.height(16.dp))
        Text("No history yet.")
    }
}
