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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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


// ─── Module definitions ──────────────────────────────────────────────────────

const val MODULE_FILE_CLEANUP = "file_cleanup"
const val MODULE_USAGE_STATS  = "usage_stats"
const val MODULE_APP_FOCUS    = "app_focus"

data class ModuleInfo(
    val key: String,
    val title: String,
    val tagline: String,
    val icon: ImageVector,
    val features: List<String>,
    /** Permission IDs required by this module */
    val permissionIds: Set<String>
)

fun buildModuleList(): List<ModuleInfo> = listOf(
    ModuleInfo(
        key = MODULE_FILE_CLEANUP,
        title = "File Cleanup",
        tagline = "Automatically delete or move files on a schedule you define.",
        icon = Icons.Default.FolderOpen,
        features = listOf(
            "Monitor Screenshots, Downloads, or any folder",
            "Delete or move files after a delay you choose",
            "Keep Google Photos safe with move-not-delete"
        ),
        permissionIds = setOf("notifications", "all_files", "overlay", "battery", "oem_autostart")
    ),
    ModuleInfo(
        key = MODULE_USAGE_STATS,
        title = "Usage Stats",
        tagline = "See exactly how much time you spend on each app every day.",
        icon = Icons.Default.BarChart,
        features = listOf(
            "Daily screen time and per-app breakdown",
            "App launch counts and device unlock frequency",
            "Chronological activity timeline"
        ),
        permissionIds = setOf("notifications", "usage_access", "battery", "oem_autostart")
    ),
    ModuleInfo(
        key = MODULE_APP_FOCUS,
        title = "App Tracking & Focus",
        tagline = "Block distracting apps, filter short-form video, and enforce focus sessions.",
        icon = Icons.Default.Block,
        features = listOf(
            "Block apps by schedule, quota, or session limit",
            "Filter YouTube Shorts, Instagram & Facebook Reels",
            "Track browser activity and silence notifications during focus",
            "Geofenced profiles and Strict Mode anti-bypass"
        ),
        permissionIds = setOf("notifications", "accessibility", "overlay", "notification_listener", "battery", "oem_autostart")
    )
)

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
        title = "Usage Access",
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

/**
 * Given the selected module keys, compute the deduplicated ordered list of permissions
 * that need to be shown in the onboarding flow.
 *
 * Order: required permissions first, then optional ones.
 * Each permission is included at most once regardless of how many modules need it.
 */
fun computePermissions(
    selectedModuleKeys: Set<String>,
    modules: List<ModuleInfo>,
    allPermissions: List<AppPermission>
): List<AppPermission> {
    val neededIds = modules
        .filter { it.key in selectedModuleKeys }
        .flatMap { it.permissionIds }
        .toSet()

    val filtered = allPermissions.filter { it.id in neededIds }
    // Required first, then optional — preserving original relative order within each group
    return filtered.filter { it.isRequired } + filtered.filter { !it.isRequired }
}

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

    // Module selection state
    val allModules = remember { buildModuleList() }
    val allPermissions = remember { buildPermissionList() }

    var selectedModules by remember { mutableStateOf(emptySet<String>()) }

    // Computed permissions based on selection; recalculated when selection changes
    var activePermissions by remember { mutableStateOf(emptyList<AppPermission>()) }

    // Step machine
    // Step 0 = Welcome, Step 1 = Module Selection
    // Steps 2..2+K-1 = Permission steps (K = activePermissions.size)
    // Step 2+K = DefaultConfig (only if file_cleanup selected)
    // Step 2+K+[0|1] = AllSet
    var step by remember { mutableIntStateOf(0) }

    val permStartStep = 2
    val defaultConfigStep = permStartStep + activePermissions.size
    val allSetStep = defaultConfigStep + if (MODULE_FILE_CLEANUP in selectedModules) 1 else 0
    val totalSteps = allSetStep + 1

    fun onModulesConfirmed(modules: Set<String>) {
        selectedModules = modules
        activePermissions = computePermissions(modules, allModules, allPermissions)
        step = permStartStep
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top progress bar ──────────────────────────────────────────────
            // Only show progress bar after module selection
            if (step >= permStartStep) {
                OnboardingProgressBar(
                    step = step - permStartStep,
                    total = totalSteps - permStartStep
                )
            }

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
                    when {
                        currentStep == 0 -> WelcomeStep(
                            modules = allModules,
                            onNext = { step = 1 }
                        )
                        currentStep == 1 -> ModuleSelectionStep(
                            modules = allModules,
                            preSelected = selectedModules,
                            onBack = { step = 0 },
                            onNext = { chosen -> onModulesConfirmed(chosen) }
                        )
                        currentStep in permStartStep until permStartStep + activePermissions.size -> {
                            val permIndex = currentStep - permStartStep
                            PermissionStep(
                                permission = activePermissions[permIndex],
                                stepIndex = currentStep,
                                onNext = { step++ }
                            )
                        }
                        currentStep == defaultConfigStep && MODULE_FILE_CLEANUP in selectedModules ->
                            DefaultConfigStep(
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
                                        step++
                                    }
                                }
                            )
                        else -> AllSetStep(
                            permissions = activePermissions,
                            onDone = {
                                coroutineScope.launch {
                                    // Persist selected modules
                                    settingsRepository.setModuleFileCleanup(MODULE_FILE_CLEANUP in selectedModules)
                                    settingsRepository.setModuleUsageStats(MODULE_USAGE_STATS in selectedModules)
                                    settingsRepository.setModuleAppFocus(MODULE_APP_FOCUS in selectedModules)
                                    settingsRepository.setOnboardingComplete(true)
                                    onComplete()
                                }
                            }
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
private fun WelcomeStep(
    modules: List<ModuleInfo>,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(vertical = 24.dp),
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
                Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "Welcome to Multi Tool",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "A suite of powerful on-device tools that keep your storage tidy, show you how you use your phone, and help you focus — all without a single byte leaving your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(4.dp))

        // Module overview cards
        modules.forEach { module ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        module.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        module.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        module.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Get Started", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        // Privacy note
        Text(
            "Everything runs 100% on-device. No account required.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Module selection step ────────────────────────────────────────────────────

@Composable
private fun ModuleSelectionStep(
    modules: List<ModuleInfo>,
    preSelected: Set<String>,
    onBack: () -> Unit,
    onNext: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(if (preSelected.isEmpty()) emptySet() else preSelected) }
    val allSelected = selected.size == modules.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Choose your tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select one or more modules to activate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Select All toggle
            TextButton(
                onClick = {
                    selected = if (allSelected) emptySet()
                    else modules.map { it.key }.toSet()
                }
            ) {
                Text(
                    if (allSelected) "Deselect all" else "Select all",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Module cards
        modules.forEach { module ->
            ModuleSelectionCard(
                module = module,
                isSelected = module.key in selected,
                onToggle = {
                    selected = if (module.key in selected) selected - module.key
                    else selected + module.key
                }
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { onNext(selected) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "Continue${if (selected.size > 1) " with ${selected.size} modules" else if (selected.size == 1) " with 1 module" else ""}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        AnimatedVisibility(visible = selected.isEmpty()) {
            Text(
                "Select at least one module to continue.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModuleSelectionCard(
    module: ModuleInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title row with checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    module.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    module.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        // Feature bullets
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            module.features.forEach { feature ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Text(
                        feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
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

    // For settings-based permissions we launch an activity and re-check on return
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        granted = permission.isGranted(context)
        if (granted) onNext()
    }

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
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
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
            "Multi Tool is ready. Here's a summary of your permission status:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Permission summary cards — only show permissions relevant to selected modules
        if (permissions.isNotEmpty()) {
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
fun PermissionCheckScreen(
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit
) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val bottomNavPadding = maxOf(padding.calculateBottomPadding(), innerPadding.calculateBottomPadding())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomNavPadding + 16.dp),
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
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grant in Settings", fontWeight = FontWeight.Medium)
            }
        }
    }
}
