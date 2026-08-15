package com.msahil432.multitool.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.msahil432.multitool.accessibility.AccessibilityUtil

import com.msahil432.multitool.data.AppDao
import com.msahil432.multitool.data.DEFAULT_EXCLUSION_RULES
import com.msahil432.multitool.data.DEFAULT_TIME_PRESETS
import com.msahil432.multitool.data.DeletionMode
import com.msahil432.multitool.data.FolderConfig
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.encodeFilterRules
import com.msahil432.multitool.data.encodeTimePeriodPresets
import com.msahil432.multitool.ui.components.ConfirmDialog
import com.msahil432.multitool.util.BatteryOptimization
import com.msahil432.multitool.util.OemAutostart
import com.msahil432.multitool.util.UsageAccess
import kotlinx.coroutines.launch


// ─── Permission model ────────────────────────────────────────────────────────

data class AppPermission(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isRequired: Boolean,
    val isGranted: (Context) -> Boolean,
    val grant: (Context, () -> Unit) -> Unit   // (context, onResult) — opens system UI
)

fun buildPermissionList(): List<AppPermission> = listOf(
    AppPermission(
        id = "notifications",
        title = "Notifications",
        subtitle = "Show prompts and keep the monitor service alive in the background.",
        icon = Icons.Default.Notifications,
        isRequired = true,
        isGranted = { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        },
        grant = { _, _ -> /* handled via rememberPermissionState in the step */ }
    ),
    AppPermission(
        id = "all_files",
        title = "All Files Access",
        subtitle = "Required to monitor folders and move/delete files anywhere on the device.",
        icon = Icons.Default.FolderOpen,
        isRequired = true,
        isGranted = { _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true
        },
        grant = { ctx, _ ->
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                ctx.startActivity(intent)
            } catch (_: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    ),
    AppPermission(
        id = "usage_access",
        title = "Usage access",
        subtitle = "Lets Multi Tool measure your screen time, app launches, and build your activity timeline. Data stays on your device.",
        icon = Icons.Default.BarChart,
        isRequired = false,
        isGranted = { ctx -> UsageAccess.isGranted(ctx) },
        grant = { ctx, _ -> UsageAccess.openSettings(ctx) }
    ),
    AppPermission(
        id = "accessibility",
        title = "Accessibility Service",
        subtitle = "Detects which app is open to enforce focus blocks, session limits, and video filters. Data never leaves your device.",
        icon = Icons.Default.AccessibilityNew,
        isRequired = false,
        isGranted = { ctx -> com.msahil432.multitool.accessibility.AccessibilityUtil.isEnabled(ctx) },
        grant = { ctx, _ -> com.msahil432.multitool.accessibility.AccessibilityUtil.openSettings(ctx) }
    ),
    AppPermission(
        id = "overlay",
        title = "Display Over Other Apps",
        subtitle = "Show a floating prompt when a new file is detected — faster than a notification.",
        icon = Icons.Default.Layers,
        isRequired = false,
        isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
        grant = { ctx, _ ->
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}"))
            )
        }
    ),

    AppPermission(
        id = "notification_listener",
        title = "Notification Listener",
        subtitle = "Silences and vaults notifications from restricted apps during active focus schedules.",
        icon = Icons.Default.NotificationsPaused,
        isRequired = false,
        isGranted = { ctx -> com.msahil432.multitool.util.NotificationAccess.isGranted(ctx) },
        grant = { ctx, _ -> com.msahil432.multitool.util.NotificationAccess.openSettings(ctx) }
    ),

    AppPermission(
        id = "battery",
        title = "Battery Optimization Exemption",
        subtitle = "Prevent Android from killing the monitor service to save battery.",
        icon = Icons.Default.BatteryFull,
        isRequired = false,
        isGranted = { ctx -> BatteryOptimization.isIgnoring(ctx) },
        grant = { ctx, _ -> BatteryOptimization.requestIgnore(ctx) }
    ),

    AppPermission(
        id = "oem_autostart",
        title = "Auto-Start & Background",
        subtitle = "Enable auto-start on ${OemAutostart.detectOem().displayName}: ${OemAutostart.getInstructions()}",
        icon = Icons.Default.PowerSettingsNew,
        isRequired = false,
        isGranted = { false },
        grant = { ctx, _ -> OemAutostart.open(ctx) }
    )
)

// ─── Onboarding screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    appDao: AppDao,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    val permissions = remember { buildPermissionList() }
    val totalSteps = permissions.size + 3 // Welcome + N permissions + DefaultConfig + Done

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top progress bar ──────────────────────────────────────────────
            OnboardingProgressBar(step = step, total = totalSteps)

            // ── Step content ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(300)) { it * dir } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(300)) { -it * dir } + fadeOut(tween(200)))
                    },
                    label = "onboarding_step"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> WelcomeStep(onNext = { step = 1 })
                        in 1..permissions.size -> PermissionStep(
                            permission = permissions[currentStep - 1],
                            stepIndex = currentStep,
                            onNext = { step++ }
                        )
                        permissions.size + 1 -> DefaultConfigStep(
                            onNext = { mode, keepAction ->
                                coroutineScope.launch {
                                    val defaultPresets = encodeTimePeriodPresets(DEFAULT_TIME_PRESETS)
                                    settingsRepository.setGlobalDeletionMode(mode.name)
                                    settingsRepository.setGlobalDefaultPool(defaultPresets)

                                    val picturesDir = Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_PICTURES
                                    )
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
                                    step = permissions.size + 2
                                }
                            }
                        )
                        else -> AllSetStep(
                            permissions = permissions,
                            onDone = onComplete
                        )
                    }
                }
            }
        }
    }
}

// ─── Progress bar ────────────────────────────────────────────────────────────

@Composable
private fun OnboardingProgressBar(step: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Setup",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${minOf(step + 1, total)} / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (step.toFloat() + 1f) / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ─── Welcome step ────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoDelete,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Welcome to Auto Delete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Automatically detects new files in folders you choose and schedules them for cleanup — so your storage stays tidy without thinking about it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(16.dp))

        // Feature highlights
        listOf(
            Triple(Icons.Default.FolderOpen, "Monitor any folder", "Screenshots, Downloads, or custom directories"),
            Triple(Icons.Default.Timer, "Scheduled cleanup", "Delete or move files after a time delay you choose"),
            Triple(Icons.Default.DriveFileMove, "Google Photos safe", "Move to a backup folder instead of deleting")
        ).forEach { (icon, title, sub) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Get Started", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

// ─── Permission step ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionStep(
    permission: AppPermission,
    stepIndex: Int,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    // Re-check status every time this composable is recomposed (e.g. after returning from Settings)
    var granted by remember { mutableStateOf(permission.isGranted(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showUsageDisclosure by remember { mutableStateOf(false) }
    var showAllFilesDisclosure by remember { mutableStateOf(false) }
    var showNotificationDisclosure by remember { mutableStateOf(false) }

    if (showAccessibilityDisclosure) {
        ConfirmDialog(
            title = "Accessibility Disclosure",
            text = "Multi Tool uses Accessibility to detect which app is open so it can enforce your focus blocks and short-form video filters. It does not collect the contents of your screen or send data off your device.",
            confirmLabel = "Continue to Settings",
            onConfirm = {
                showAccessibilityDisclosure = false
                AccessibilityUtil.openSettings(context)
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }

    if (showUsageDisclosure) {
        ConfirmDialog(
            title = "Usage Access Disclosure",
            text = "Multi Tool uses Usage Access to measure your screen time, count app launches, and build your activity timeline. All usage statistics are stored and processed entirely on your device and are never sent off the device.",
            confirmLabel = "Continue to Settings",
            onConfirm = {
                showUsageDisclosure = false
                try {
                    settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    UsageAccess.openSettings(context)
                }
            },
            onDismiss = { showUsageDisclosure = false }
        )
    }

    if (showAllFilesDisclosure) {
        ConfirmDialog(
            title = "All Files Access Disclosure",
            text = "Multi Tool requires All Files Access to monitor folders (such as Screenshots and Downloads) and automatically move or delete files based on your configured cleanup schedules. Your files are processed entirely on-device and are never uploaded or shared.",
            confirmLabel = "Continue to Settings",
            onConfirm = {
                showAllFilesDisclosure = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    } catch (_: Exception) {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                }
            },
            onDismiss = { showAllFilesDisclosure = false }
        )
    }

    if (showNotificationDisclosure) {
        ConfirmDialog(
            title = "Notification Access Disclosure",
            text = "Multi Tool needs notification listener access to silence and vault notifications from restricted apps during active focus schedules. All notification titles and previews are stored exclusively on your device and are never sent anywhere.",
            confirmLabel = "Continue to Settings",
            onConfirm = {
                showNotificationDisclosure = false
                com.msahil432.multitool.util.NotificationAccess.openSettings(context)
            },
            onDismiss = { showNotificationDisclosure = false }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = permission.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // For the notification permission we need the accompanist launcher path
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (isGranted) onNext()
    }

    // For settings-based permissions we launch an activity and re-check on return
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        granted = permission.isGranted(context)
        if (granted) onNext()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Status circle
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    if (granted) MaterialTheme.colorScheme.tertiaryContainer
                    else if (permission.isRequired) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                permission.icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = if (granted) MaterialTheme.colorScheme.tertiary
                       else if (permission.isRequired) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.secondary
            )
        }

        // Required / Optional badge
        Surface(
            shape = RoundedCornerShape(50),
            color = if (permission.isRequired) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (permission.isRequired) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                if (permission.isRequired) "Required" else "Optional",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }

        // Title and description
        Text(
            permission.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            permission.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        // Status chip
        AnimatedVisibility(visible = true) {
            PermissionStatusChip(granted = granted)
        }

        Spacer(Modifier.height(4.dp))

        if (granted) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = {
                    when (permission.id) {
                        "notifications" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onNext()
                            }
                        }
                        "all_files" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                showAllFilesDisclosure = true
                            } else onNext()
                        }
                        "usage_access" -> {
                            showUsageDisclosure = true
                        }
                        "accessibility" -> {
                            showAccessibilityDisclosure = true
                        }
                        "overlay" -> settingsLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"))
                        )
                        "notification_listener" -> {
                            showNotificationDisclosure = true
                        }
                        "battery" -> {
                            try {
                                settingsLauncher.launch(BatteryOptimization.createRequestIntent(context))
                            } catch (_: Exception) {
                                BatteryOptimization.requestIgnore(context)
                            }
                        }
                        "oem_autostart" -> {
                            OemAutostart.open(context)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant Permission", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            if (!permission.isRequired) {
                TextButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Skip for now",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStatusChip(granted: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (granted) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (granted) MaterialTheme.colorScheme.tertiary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (granted) "Granted" else "Not granted",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Default config step ─────────────────────────────────────────────────────

@Composable
private fun DefaultConfigStep(onNext: (DeletionMode, String) -> Unit) {
    var mode by remember { mutableStateOf(DeletionMode.TRASH) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            "Default Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "We'll monitor your Screenshots folder automatically. What should happen when a scheduled timer fires?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple(DeletionMode.TRASH, "Move to Trash", "Recoverable from the system trash"),
                Triple(DeletionMode.DELETE, "Delete Permanently", "Cannot be recovered"),
                Triple(DeletionMode.ASK_AGAIN, "Ask Again", "Re-prompt when the timer fires")
            ).forEach { (m, label, sub) ->
                val selected = mode == m
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { mode = m },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Column {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { onNext(mode, "KEEP") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Finish Setup", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

// ─── All Set step ────────────────────────────────────────────────────────────

@Composable
private fun AllSetStep(
    permissions: List<AppPermission>,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        Text(
            "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Auto Delete is ready. Here's a summary of your permission status:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Permission summary cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val grantedStates = remember(refreshKey) {
                permissions.map { it.isGranted(context) }
            }
            permissions.forEachIndexed { idx, perm ->
                val granted = grantedStates[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (granted) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            else if (perm.isRequired) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.tertiary
                               else if (perm.isRequired) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            perm.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (granted) "Granted" else if (perm.isRequired) "Not granted — required!" else "Not granted — optional",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (granted) MaterialTheme.colorScheme.tertiary
                                    else if (perm.isRequired) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Open App", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

// ─── Permissions check screen (post-onboarding, launched from Settings) ──────

@Composable
fun PermissionCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { buildPermissionList() }

    // Trigger recomposition when returning from a Settings screen
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Permissions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Force re-evaluation of isGranted on each refresh
            val grantedStates = remember(refreshKey) {
                permissions.map { it.isGranted(context) }
            }

            val allRequired = permissions.filter { it.isRequired }
                .all { it.isGranted(context) }

            if (allRequired) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "All required permissions are granted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Some required permissions are missing. The app may not function correctly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            var showAccessibilityDisclosure by remember { mutableStateOf(false) }
            var showUsageDisclosure by remember { mutableStateOf(false) }
            var showAllFilesDisclosure by remember { mutableStateOf(false) }
            var showNotificationDisclosure by remember { mutableStateOf(false) }

            if (showAccessibilityDisclosure) {
                ConfirmDialog(
                    title = "Accessibility Disclosure",
                    text = "Multi Tool uses Accessibility to detect which app is open so it can enforce your focus blocks and short-form video filters. It does not collect the contents of your screen or send data off your device.",
                    confirmLabel = "Continue to Settings",
                    onConfirm = {
                        showAccessibilityDisclosure = false
                        AccessibilityUtil.openSettings(context)
                    },
                    onDismiss = { showAccessibilityDisclosure = false }
                )
            }

            if (showUsageDisclosure) {
                ConfirmDialog(
                    title = "Usage Access Disclosure",
                    text = "Multi Tool uses Usage Access to measure your screen time, count app launches, and build your activity timeline. All usage statistics are stored and processed entirely on your device and are never sent off the device.",
                    confirmLabel = "Continue to Settings",
                    onConfirm = {
                        showUsageDisclosure = false
                        try {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            )
                        } catch (_: Exception) {
                            UsageAccess.openSettings(context)
                        }
                    },
                    onDismiss = { showUsageDisclosure = false }
                )
            }

            if (showAllFilesDisclosure) {
                ConfirmDialog(
                    title = "All Files Access Disclosure",
                    text = "Multi Tool requires All Files Access to monitor folders (such as Screenshots and Downloads) and automatically move or delete files based on your configured cleanup schedules. Your files are processed entirely on-device and are never uploaded or shared.",
                    confirmLabel = "Continue to Settings",
                    onConfirm = {
                        showAllFilesDisclosure = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                settingsLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                )
                            } catch (_: Exception) {
                                settingsLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                )
                            }
                        }
                    },
                    onDismiss = { showAllFilesDisclosure = false }
                )
            }

            if (showNotificationDisclosure) {
                ConfirmDialog(
                    title = "Notification Access Disclosure",
                    text = "Multi Tool needs notification listener access to silence and vault notifications from restricted apps during active focus schedules. All notification titles and previews are stored exclusively on your device and are never sent anywhere.",
                    confirmLabel = "Continue to Settings",
                    onConfirm = {
                        showNotificationDisclosure = false
                        com.msahil432.multitool.util.NotificationAccess.openSettings(context)
                    },
                    onDismiss = { showNotificationDisclosure = false }
                )
            }

            permissions.forEachIndexed { idx, perm ->
                val granted = grantedStates[idx]
                PermissionCard(
                    permission = perm,
                    granted = granted,
                    onGrant = {
                        when (perm.id) {
                            "notifications" -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            "all_files" -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    showAllFilesDisclosure = true
                                }
                            }
                            "usage_access" -> {
                                showUsageDisclosure = true
                            }
                            "accessibility" -> {
                                showAccessibilityDisclosure = true
                            }
                            "overlay" -> settingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"))
                            )
                            "notification_listener" -> {
                                showNotificationDisclosure = true
                            }
                            "battery" -> {
                                try {
                                    settingsLauncher.launch(BatteryOptimization.createRequestIntent(context))
                                } catch (_: Exception) {
                                    BatteryOptimization.requestIgnore(context)
                                }
                            }
                            "oem_autostart" -> {
                                OemAutostart.open(context)
                            }
                        }
                    }
                )
            }
        }
    }
}


@Composable
private fun PermissionCard(
    permission: AppPermission,
    granted: Boolean,
    onGrant: () -> Unit
) {
    val containerColor = when {
        granted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        permission.isRequired -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val borderColor = when {
        granted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
        permission.isRequired -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                permission.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when {
                    granted -> MaterialTheme.colorScheme.tertiary
                    permission.isRequired -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        permission.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (permission.isRequired)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (permission.isRequired)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            if (permission.isRequired) "Required" else "Optional",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    permission.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PermissionStatusChip(granted = granted)
        }

        if (!granted) {
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = if (permission.isRequired)
                    ButtonDefaults.buttonColors()
                else
                    ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grant in Settings", fontWeight = FontWeight.Medium)
            }
        }
    }
}
