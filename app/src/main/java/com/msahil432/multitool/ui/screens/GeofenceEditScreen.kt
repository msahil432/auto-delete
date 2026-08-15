package com.msahil432.multitool.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.GeofenceProfile
import com.msahil432.multitool.data.GeofenceRepository
import com.msahil432.multitool.location.GeofenceManager
import com.msahil432.multitool.ui.components.ConfirmDialog
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.components.SectionHeader
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceEditScreen(
    profileId: Long,
    geofenceRepository: GeofenceRepository,
    blockingRepository: BlockingRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geofenceManager = remember(context) { GeofenceManager(context) }

    val allGroups by blockingRepository.groups().collectAsState(initial = emptyList())
    var initialProfileLoaded by remember { mutableStateOf(profileId == 0L) }
    var existingProfile by remember { mutableStateOf<GeofenceProfile?>(null) }

    var name by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var radiusMeters by remember { mutableFloatStateOf(150f) }
    var onEnterSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var onExitSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isEnabled by remember { mutableStateOf(true) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profileId) {
        if (profileId > 0L) {
            val p = geofenceRepository.getProfileById(profileId)
            if (p != null) {
                existingProfile = p
                name = p.name
                latitudeText = p.latitude.toString()
                longitudeText = p.longitude.toString()
                radiusMeters = p.radiusMeters
                onEnterSelectedIds = GeofenceRepository.parseGroupIds(p.onEnterGroupIds).toSet()
                onExitSelectedIds = GeofenceRepository.parseGroupIds(p.onExitGroupIds).toSet()
                isEnabled = p.enabled
            }
            initialProfileLoaded = true
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            errorMessage = "Location permission is required to fetch current location."
            return
        }

        isLocating = true
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                isLocating = false
                if (loc != null) {
                    latitudeText = "%.6f".format(loc.latitude)
                    longitudeText = "%.6f".format(loc.longitude)
                    errorMessage = null
                } else {
                    errorMessage = "Could not obtain current location. Ensure GPS is enabled."
                }
            }
            .addOnFailureListener {
                isLocating = false
                errorMessage = "Failed to fetch location: ${it.localizedMessage}"
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchCurrentLocation()
        } else {
            errorMessage = "Location permission was denied."
        }
    }

    fun handleUseCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fetchCurrentLocation()
        }
    }

    fun handleSave() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            errorMessage = "Profile name cannot be empty."
            return
        }
        val lat = latitudeText.toDoubleOrNull()
        if (lat == null || lat < -90.0 || lat > 90.0) {
            errorMessage = "Please enter a valid latitude (-90 to 90)."
            return
        }
        val lng = longitudeText.toDoubleOrNull()
        if (lng == null || lng < -180.0 || lng > 180.0) {
            errorMessage = "Please enter a valid longitude (-180 to 180)."
            return
        }

        coroutineScope.launch {
            val profile = GeofenceProfile(
                id = if (profileId > 0L) profileId else 0L,
                name = trimmedName,
                latitude = lat,
                longitude = lng,
                radiusMeters = radiusMeters,
                onEnterGroupIds = GeofenceRepository.formatGroupIds(onEnterSelectedIds),
                onExitGroupIds = GeofenceRepository.formatGroupIds(onExitSelectedIds),
                enabled = isEnabled
            )

            val savedId = geofenceRepository.upsertProfile(profile)
            val registeredProfile = profile.copy(id = if (profile.id == 0L) savedId else profile.id)
            if (registeredProfile.enabled) {
                geofenceManager.registerGeofence(registeredProfile)
            } else {
                geofenceManager.unregisterGeofence(registeredProfile.id)
            }
            onBack()
        }
    }

    fun handleDelete() {
        coroutineScope.launch {
            if (profileId > 0L) {
                geofenceManager.unregisterGeofence(profileId)
                geofenceRepository.deleteProfileById(profileId)
            }
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profileId == 0L) "New Location Profile" else "Edit Location Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (profileId > 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Profile",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!initialProfileLoaded) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else {
            GeofenceEditContent(
                name = name,
                onNameChange = {
                    name = it
                    errorMessage = null
                },
                latitudeText = latitudeText,
                onLatitudeChange = {
                    latitudeText = it
                    errorMessage = null
                },
                longitudeText = longitudeText,
                onLongitudeChange = {
                    longitudeText = it
                    errorMessage = null
                },
                radiusMeters = radiusMeters,
                onRadiusChange = { radiusMeters = it },
                onEnterSelectedIds = onEnterSelectedIds,
                onToggleEnterGroup = { id ->
                    onEnterSelectedIds = if (onEnterSelectedIds.contains(id)) {
                        onEnterSelectedIds - id
                    } else {
                        onEnterSelectedIds + id
                    }
                },
                onExitSelectedIds = onExitSelectedIds,
                onToggleExitGroup = { id ->
                    onExitSelectedIds = if (onExitSelectedIds.contains(id)) {
                        onExitSelectedIds - id
                    } else {
                        onExitSelectedIds + id
                    }
                },
                isEnabled = isEnabled,
                onEnabledChange = { isEnabled = it },
                allGroups = allGroups,
                isLocating = isLocating,
                errorMessage = errorMessage,
                onUseCurrentLocation = { handleUseCurrentLocation() },
                onSave = { handleSave() },
                onCancel = onBack,
                paddingValues = paddingValues
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete Profile?",
            text = "Are you sure you want to delete '$name'? This location profile will be removed and geofencing will be stopped.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                handleDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
fun GeofenceEditContent(
    name: String,
    onNameChange: (String) -> Unit,
    latitudeText: String,
    onLatitudeChange: (String) -> Unit,
    longitudeText: String,
    onLongitudeChange: (String) -> Unit,
    radiusMeters: Float,
    onRadiusChange: (Float) -> Unit,
    onEnterSelectedIds: Set<Long>,
    onToggleEnterGroup: (Long) -> Unit,
    onExitSelectedIds: Set<Long>,
    onToggleExitGroup: (Long) -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    allGroups: List<BlockGroup>,
    isLocating: Boolean,
    errorMessage: String?,
    onUseCurrentLocation: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Profile Name
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Profile Name") },
            placeholder = { Text("e.g. Work, Home, Library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Coordinates & Current Location
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Location Coordinates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onUseCurrentLocation,
                        enabled = !isLocating,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isLocating) "Locating..." else "Use Current")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = onLatitudeChange,
                        label = { Text("Latitude") },
                        placeholder = { Text("37.7749") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = onLongitudeChange,
                        label = { Text("Longitude") },
                        placeholder = { Text("-122.4194") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Radius Slider
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = "Geofence Radius",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${radiusMeters.toInt()} meters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = radiusMeters,
                    onValueChange = onRadiusChange,
                    valueRange = 50f..1000f,
                    steps = 18,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Geofence Radius: ${radiusMeters.toInt()} meters"
                        }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("50m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("500m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1000m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Block Groups on Enter
        SectionHeader(title = "Enable on ENTER")
        Text(
            text = "Select block groups to activate when entering this geofence zone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (allGroups.isEmpty()) {
            Text(
                text = "No block groups created yet. Create block groups in the Blocking tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    allGroups.forEach { group ->
                        val isChecked = onEnterSelectedIds.contains(group.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleEnterGroup(group.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(group.name, style = MaterialTheme.typography.bodyMedium)
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleEnterGroup(group.id) }
                            )
                        }
                    }
                }
            }
        }

        // Block Groups on Exit
        SectionHeader(title = "Enable on EXIT")
        Text(
            text = "Select block groups to activate when exiting this geofence zone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (allGroups.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    allGroups.forEach { group ->
                        val isChecked = onExitSelectedIds.contains(group.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleExitGroup(group.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(group.name, style = MaterialTheme.typography.bodyMedium)
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleExitGroup(group.id) }
                            )
                        }
                    }
                }
            }
        }

        // Enable profile toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Profile Enabled", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Geofence transitions trigger rules only when enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save & Cancel Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Discard")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "GeofenceEditScreen Light")
@Composable
private fun GeofenceEditScreenPreviewLight() {
    MultiToolTheme {
        GeofenceEditContent(
            name = "Office Work Zone",
            onNameChange = {},
            latitudeText = "37.7749",
            onLatitudeChange = {},
            longitudeText = "-122.4194",
            onLongitudeChange = {},
            radiusMeters = 200f,
            onRadiusChange = {},
            onEnterSelectedIds = setOf(1L),
            onToggleEnterGroup = {},
            onExitSelectedIds = setOf(2L),
            onToggleExitGroup = {},
            isEnabled = true,
            onEnabledChange = {},
            allGroups = listOf(
                BlockGroup(id = 1, name = "Social Media", packageNames = "com.ig", enabled = true),
                BlockGroup(id = 2, name = "Gaming", packageNames = "com.game", enabled = true)
            ),
            isLocating = false,
            errorMessage = null,
            onUseCurrentLocation = {},
            onSave = {},
            onCancel = {},
            paddingValues = PaddingValues()
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "GeofenceEditScreen Dark")
@Composable
private fun GeofenceEditScreenPreviewDark() {
    MultiToolTheme {
        GeofenceEditContent(
            name = "Office Work Zone",
            onNameChange = {},
            latitudeText = "37.7749",
            onLatitudeChange = {},
            longitudeText = "-122.4194",
            onLongitudeChange = {},
            radiusMeters = 200f,
            onRadiusChange = {},
            onEnterSelectedIds = setOf(1L),
            onToggleEnterGroup = {},
            onExitSelectedIds = emptySet(),
            onToggleExitGroup = {},
            isEnabled = true,
            onEnabledChange = {},
            allGroups = listOf(
                BlockGroup(id = 1, name = "Social Media", packageNames = "com.ig", enabled = true)
            ),
            isLocating = false,
            errorMessage = null,
            onUseCurrentLocation = {},
            onSave = {},
            onCancel = {},
            paddingValues = PaddingValues()
        )
    }
}
