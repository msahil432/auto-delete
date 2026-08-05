package com.msahil432.autodelete.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.msahil432.autodelete.data.AppDao
import com.msahil432.autodelete.data.FolderConfig
import com.msahil432.autodelete.data.DeletionMode
import com.msahil432.autodelete.data.SettingsRepository
import com.msahil432.autodelete.data.DEFAULT_EXCLUSION_RULES
import com.msahil432.autodelete.data.DEFAULT_TIME_PRESETS
import com.msahil432.autodelete.data.encodeFilterRules
import com.msahil432.autodelete.data.encodeTimePeriodPresets
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    appDao: AppDao,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(1) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentStep) {
                1 -> WelcomeStep { currentStep = 2 }
                2 -> NotificationPermissionStep { currentStep = 3 }
                3 -> AllFilesAccessStep(context) { currentStep = 4 }
                4 -> OverlayPermissionStep(context) { currentStep = 5 }
                5 -> ScopedStorageStep(context) { currentStep = 6 }
                6 -> BatteryExemptionStep(context) { currentStep = 7 }
                7 -> DefaultConfigStep(
                    onNext = { mode, keepAction ->
                        coroutineScope.launch {
                            val defaultPresets = encodeTimePeriodPresets(DEFAULT_TIME_PRESETS)
                            settingsRepository.setGlobalDeletionMode(mode.name)
                            settingsRepository.setGlobalDefaultPool(defaultPresets)

                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val screenshotsDir = "${picturesDir.absolutePath}/Screenshots"

                            appDao.insertFolderConfig(
                                FolderConfig(
                                    path = screenshotsDir,
                                    displayName = "Screenshots",
                                    isDefaultScreenshotsFolder = true,
                                    enabled = true,
                                    deletionMode = mode,
                                    defaultActionOnIgnore = keepAction,
                                    candidateTimePeriods = defaultPresets,
                                    recentlyUsedPeriods = defaultPresets,
                                    fileTypeExcludeList = encodeFilterRules(DEFAULT_EXCLUSION_RULES),
                                    fileTypeIncludeList = null,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            settingsRepository.setOnboardingComplete(true)
                            onComplete()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Text("Welcome to Auto Delete", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text("Detects new files in folders you choose and cleans them up automatically.")
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onNext) { Text("Get Started") }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationPermissionStep(onNext: () -> Unit) {
    Text("Notifications", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text("We need notifications to show you a prompt when a new file is detected, and to keep the app running in the background reliably.")
    Spacer(modifier = Modifier.height(32.dp))
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        Button(onClick = {
            if (permissionState.status.isGranted) {
                onNext()
            } else {
                permissionState.launchPermissionRequest()
            }
        }) {
            Text(if (permissionState.status.isGranted) "Continue" else "Grant Permission")
        }
        if (!permissionState.status.isGranted) {
            TextButton(onClick = onNext) { Text("Skip for now") }
        }
    } else {
        Button(onClick = onNext) { Text("Continue") }
    }
}

@Composable
fun AllFilesAccessStep(context: Context, onNext: () -> Unit) {
    Text("All Files Access", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text("To monitor any folder and clean up files across your device, we need 'All Files Access'.")
    Spacer(modifier = Modifier.height(32.dp))
    
    val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true // Handled by standard read/write permissions on older Android versions usually, but we target 36
    }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            onNext()
        }
    }

    Button(onClick = {
        if (isGranted) {
            onNext()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                launcher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                launcher.launch(intent)
            }
        } else {
            onNext()
        }
    }) {
        Text(if (isGranted) "Continue" else "Grant Permission")
    }
    
    if (!isGranted) {
        TextButton(onClick = onNext) { Text("Skip for now (App will not work on custom folders)") }
    }
}

@Composable
fun OverlayPermissionStep(context: Context, onNext: () -> Unit) {
    Text("Floating Overlay (Optional)", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text("Allowing 'Display over other apps' lets us show a quick pop-up overlay when a file is created, instead of just a notification.")
    Spacer(modifier = Modifier.height(32.dp))
    
    val isGranted = Settings.canDrawOverlays(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) {
            onNext()
        }
    }

    Button(onClick = {
        if (isGranted) {
            onNext()
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            launcher.launch(intent)
        }
    }) {
        Text(if (isGranted) "Continue" else "Grant Permission")
    }
    
    if (!isGranted) {
        TextButton(onClick = onNext) { Text("Skip (Use notifications)") }
    }
}

@Composable
fun ScopedStorageStep(context: Context, onNext: () -> Unit) {
    Text("Folder Access (Optional)", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "To monitor custom folders (beyond Screenshots), grant folder access via the system file browser. " +
        "This lets you pick any folder and gives the app persistent access to it."
    )
    Spacer(modifier = Modifier.height(32.dp))

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        // Always continue regardless of whether a folder was picked here — they can do it later
        onNext()
    }

    Button(onClick = { launcher.launch(null) }) {
        Text("Grant Folder Access")
    }
    TextButton(onClick = onNext) {
        Text("Skip (You can grant this per-folder later)")
    }
}

@Composable
fun BatteryExemptionStep(context: Context, onNext: () -> Unit) {
    Text("Battery Optimization", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text("To ensure scheduled deletions run reliably and the file monitor isn't killed, please exempt this app from battery optimizations. Uses slightly more battery.")
    Spacer(modifier = Modifier.height(32.dp))
    
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val isGranted = pm.isIgnoringBatteryOptimizations(context.packageName)
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onNext()
    }

    Button(onClick = {
        if (isGranted) {
            onNext()
        } else {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            launcher.launch(intent)
        }
    }) {
        Text(if (isGranted) "Continue" else "Request Exemption")
    }
    
    if (!isGranted) {
        TextButton(onClick = onNext) { Text("Skip (May cause unreliable deletions)") }
    }
}

@Composable
fun DefaultConfigStep(onNext: (DeletionMode, String) -> Unit) {
    var mode by remember { mutableStateOf(DeletionMode.TRASH) }
    
    Text("Setup Default Folder", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text("We will monitor the Screenshots folder by default. What should happen when a timer expires?")
    Spacer(modifier = Modifier.height(16.dp))
    
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = mode == DeletionMode.TRASH, onClick = { mode = DeletionMode.TRASH })
            Text("Move to Trash (Recoverable)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = mode == DeletionMode.DELETE, onClick = { mode = DeletionMode.DELETE })
            Text("Delete Permanently")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = mode == DeletionMode.ASK_AGAIN, onClick = { mode = DeletionMode.ASK_AGAIN })
            Text("Ask Again")
        }
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = { onNext(mode, "KEEP") }) { Text("Finish Setup") }
}
