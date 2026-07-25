package com.msahil432.autodelete.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.msahil432.autodelete.Settings as SettingsRoute

@Composable
fun OnboardingScreen(onNavigate: (NavKey) -> Unit) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome to Sahil's Auto Delete")
        Spacer(modifier = Modifier.height(16.dp))
        Text("We need permission to display the deletion prompt over other apps.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (!overlayGranted) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                overlayLauncher.launch(intent)
            }
        }) {
            Text(if (overlayGranted) "Overlay Permission Granted" else "Grant Overlay Permission")
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (overlayGranted) {
                    onNavigate(SettingsRoute)
                }
            },
            enabled = overlayGranted
        ) {
            Text("Continue")
        }
    }
}
