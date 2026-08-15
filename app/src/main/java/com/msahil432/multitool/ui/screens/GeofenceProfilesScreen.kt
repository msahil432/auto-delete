package com.msahil432.multitool.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.GeofenceProfile
import com.msahil432.multitool.data.GeofenceRepository
import com.msahil432.multitool.location.GeofenceManager
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.components.PermissionTile
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceProfilesScreen(
    geofenceRepository: GeofenceRepository,
    blockingRepository: BlockingRepository,
    onBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val geofenceManager = remember(context) { GeofenceManager(context) }

    val profiles by geofenceRepository.allProfiles().collectAsState(initial = null)
    val groups by blockingRepository.groups().collectAsState(initial = emptyList())

    var hasForegroundPermission by remember { mutableStateOf(geofenceManager.hasForegroundLocationPermission()) }
    var hasBackgroundPermission by remember { mutableStateOf(geofenceManager.hasBackgroundLocationPermission()) }
    var showBackgroundDisclosure by remember { mutableStateOf(false) }

    // Launcher for Foreground (Fine & Coarse) Location Permission
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasForegroundPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasForegroundPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundPermission) {
            showBackgroundDisclosure = true
        }
    }

    // Launcher for Background Location Permission
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundPermission = granted
        if (granted) {
            coroutineScope.launch { geofenceManager.reRegisterAll(geofenceRepository) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasForegroundPermission = geofenceManager.hasForegroundLocationPermission()
                hasBackgroundPermission = geofenceManager.hasBackgroundLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Profiles", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToEdit(0L) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New profile") }
            )
        }
    ) { paddingValues ->
        GeofenceProfilesContent(
            profiles = profiles,
            groups = groups,
            hasForegroundPermission = hasForegroundPermission,
            hasBackgroundPermission = hasBackgroundPermission,
            paddingValues = paddingValues,
            onRequestForegroundPermission = {
                foregroundPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onRequestBackgroundPermission = {
                showBackgroundDisclosure = true
            },
            onNavigateToEdit = onNavigateToEdit,
            onToggleProfileEnabled = { profile, enabled ->
                coroutineScope.launch {
                    val updated = profile.copy(enabled = enabled)
                    geofenceRepository.updateProfile(updated)
                    if (enabled) {
                        geofenceManager.registerGeofence(updated)
                    } else {
                        geofenceManager.unregisterGeofence(profile.id)
                    }
                }
            }
        )
    }

    if (showBackgroundDisclosure) {
        AlertDialog(
            onDismissRequest = { showBackgroundDisclosure = false },
            icon = { Icon(Icons.Default.Place, contentDescription = null) },
            title = { Text("Background Location Required") },
            text = {
                Text(
                    "MultiTool needs \"Allow all the time\" location access in the background so it can automatically activate focus profiles when you enter or leave designated places (like work or home).\n\nYour location is checked only on-device for geofencing and is never shared or stored remotely."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundDisclosure = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                ) {
                    Text("Continue to Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundDisclosure = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GeofenceProfilesContent(
    profiles: List<GeofenceProfile>?,
    groups: List<BlockGroup>,
    hasForegroundPermission: Boolean,
    hasBackgroundPermission: Boolean,
    paddingValues: PaddingValues,
    onRequestForegroundPermission: () -> Unit,
    onRequestBackgroundPermission: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onToggleProfileEnabled: (GeofenceProfile, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupsMap = remember(groups) { groups.associateBy { it.id } }

    when {
        profiles == null -> {
            LoadingState(modifier = modifier.padding(paddingValues))
        }
        else -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Permission warning tile if location permission missing
                if (!hasForegroundPermission) {
                    item(key = "perm_foreground") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PermissionTile(
                                title = "Location Access Required",
                                subtitle = "Location permission is required to detect when you enter or leave focus zones.",
                                granted = false,
                                icon = Icons.Default.LocationOn,
                                isRequired = true,
                                onGrant = onRequestForegroundPermission,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else if (!hasBackgroundPermission) {
                    item(key = "perm_background") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PermissionTile(
                                title = "Background Location Needed",
                                subtitle = "Set location permission to 'Allow all the time' to trigger profiles while the screen is off.",
                                granted = false,
                                icon = Icons.Default.MyLocation,
                                isRequired = true,
                                onGrant = onRequestBackgroundPermission,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                if (profiles.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyState(
                            icon = Icons.Default.Place,
                            title = "No location profiles yet",
                            message = "Create a profile to activate focus modes when arriving at places like work or home.",
                            actionLabel = "New profile",
                            onAction = { onNavigateToEdit(0L) },
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }
                } else {
                    items(profiles, key = { it.id }) { profile ->
                        GeofenceProfileCard(
                            profile = profile,
                            groupsMap = groupsMap,
                            onClick = { onNavigateToEdit(profile.id) },
                            onToggleEnabled = { enabled -> onToggleProfileEnabled(profile, enabled) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GeofenceProfileCard(
    profile: GeofenceProfile,
    groupsMap: Map<Long, BlockGroup>,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val enterGroupNames = remember(profile.onEnterGroupIds, groupsMap) {
        val ids = GeofenceRepository.parseGroupIds(profile.onEnterGroupIds)
        if (ids.isEmpty()) "None" else ids.mapNotNull { groupsMap[it]?.name }.joinToString(", ").ifEmpty { "None" }
    }

    val exitGroupNames = remember(profile.onExitGroupIds, groupsMap) {
        val ids = GeofenceRepository.parseGroupIds(profile.onExitGroupIds)
        if (ids.isEmpty()) "None" else ids.mapNotNull { groupsMap[it]?.name }.joinToString(", ").ifEmpty { "None" }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Radius: ${profile.radiusMeters.toInt()}m",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = profile.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle ${profile.name}"
                    }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "On Enter: $enterGroupNames",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "On Exit: $exitGroupNames",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "GeofenceProfilesScreen Light")
@Composable
private fun GeofenceProfilesScreenPreviewLight() {
    MultiToolTheme {
        GeofenceProfilesContent(
            profiles = listOf(
                GeofenceProfile(
                    id = 1,
                    name = "Work Office",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    radiusMeters = 150f,
                    onEnterGroupIds = "1;2",
                    onExitGroupIds = "",
                    enabled = true
                ),
                GeofenceProfile(
                    id = 2,
                    name = "Home Library",
                    latitude = 37.7849,
                    longitude = -122.4094,
                    radiusMeters = 80f,
                    onEnterGroupIds = "3",
                    onExitGroupIds = "1",
                    enabled = false
                )
            ),
            groups = listOf(
                BlockGroup(id = 1, name = "Social Media", packageNames = "com.ig", enabled = true),
                BlockGroup(id = 2, name = "Games", packageNames = "com.game", enabled = true),
                BlockGroup(id = 3, name = "Distractions", packageNames = "com.video", enabled = true)
            ),
            hasForegroundPermission = true,
            hasBackgroundPermission = true,
            paddingValues = PaddingValues(),
            onRequestForegroundPermission = {},
            onRequestBackgroundPermission = {},
            onNavigateToEdit = {},
            onToggleProfileEnabled = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "GeofenceProfilesScreen Dark")
@Composable
private fun GeofenceProfilesScreenPreviewDark() {
    MultiToolTheme {
        GeofenceProfilesContent(
            profiles = listOf(
                GeofenceProfile(
                    id = 1,
                    name = "Work Office",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    radiusMeters = 150f,
                    onEnterGroupIds = "1;2",
                    onExitGroupIds = "",
                    enabled = true
                )
            ),
            groups = listOf(
                BlockGroup(id = 1, name = "Social Media", packageNames = "com.ig", enabled = true),
                BlockGroup(id = 2, name = "Games", packageNames = "com.game", enabled = true)
            ),
            hasForegroundPermission = false,
            hasBackgroundPermission = false,
            paddingValues = PaddingValues(),
            onRequestForegroundPermission = {},
            onRequestBackgroundPermission = {},
            onNavigateToEdit = {},
            onToggleProfileEnabled = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "GeofenceProfilesScreen Empty")
@Composable
private fun GeofenceProfilesScreenEmptyPreview() {
    MultiToolTheme {
        GeofenceProfilesContent(
            profiles = emptyList(),
            groups = emptyList(),
            hasForegroundPermission = true,
            hasBackgroundPermission = true,
            paddingValues = PaddingValues(),
            onRequestForegroundPermission = {},
            onRequestBackgroundPermission = {},
            onNavigateToEdit = {},
            onToggleProfileEnabled = { _, _ -> }
        )
    }
}
