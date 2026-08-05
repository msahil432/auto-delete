package com.msahil432.autodelete.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.msahil432.autodelete.data.*
import kotlinx.coroutines.launch

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(folderId: Long, appDao: AppDao, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var config by remember { mutableStateOf<FolderConfig?>(null) }

    LaunchedEffect(folderId) {
        appDao.getFolderConfigById(folderId).collect { config = it }
    }

    val currentConfig = config

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentConfig?.displayName ?: "Loading…",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (currentConfig == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── 1. Folder Path ──
                FolderPathSection(
                    config = currentConfig,
                    onConfigUpdated = { updated ->
                        config = updated
                        coroutineScope.launch { appDao.updateFolderConfig(updated) }
                    }
                )

                SectionDivider()

                // ── 2. Deletion Mode ──
                DeletionModeSection(
                    config = currentConfig,
                    onConfigUpdated = { updated ->
                        config = updated
                        coroutineScope.launch { appDao.updateFolderConfig(updated) }
                    }
                )

                SectionDivider()

                // ── 3. Time Period Presets ──
                TimePeriodPresetsSection(
                    config = currentConfig,
                    onConfigUpdated = { updated ->
                        config = updated
                        coroutineScope.launch { appDao.updateFolderConfig(updated) }
                    }
                )

                SectionDivider()

                // ── 4. File Filters ──
                FileFiltersSection(
                    config = currentConfig,
                    onConfigUpdated = { updated ->
                        config = updated
                        coroutineScope.launch { appDao.updateFolderConfig(updated) }
                    }
                )
            }
        }
    }
}

// ─── Section: Folder Path ─────────────────────────────────────────────────────

@Composable
fun FolderPathSection(
    config: FolderConfig,
    onConfigUpdated: (FolderConfig) -> Unit
) {
    val context = LocalContext.current
    var editedPath by remember(config.path) { mutableStateOf(config.path) }
    var editedName by remember(config.displayName) { mutableStateOf(config.displayName) }
    var isEditing by remember { mutableStateOf(false) }

    // SAF folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist permission across reboots
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)

            // Convert URI to a usable display path
            val uriPath = uri.path ?: uri.toString()
            val friendlyPath = uriPath
                .removePrefix("/tree/primary:")
                .removePrefix("/tree/")
                .let { if (!it.startsWith("/")) "/storage/emulated/0/$it" else it }

            val folderName = friendlyPath.substringAfterLast('/')
                .ifBlank { editedName }

            editedPath = friendlyPath
            editedName = folderName
            onConfigUpdated(config.copy(path = friendlyPath, displayName = folderName))
        }
    }

    SectionContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Monitored Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Pick folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isEditing) "Save" else "Edit path",
                        tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editedPath,
                onValueChange = { editedPath = it },
                label = { Text("Folder Path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        isEditing = false
                        onConfigUpdated(config.copy(path = editedPath, displayName = editedName))
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = editedName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = editedPath,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Hint about SAF
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "Use the folder icon to pick via system browser, or edit the path manually.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ─── Section: Deletion Mode ───────────────────────────────────────────────────

@Composable
fun DeletionModeSection(
    config: FolderConfig,
    onConfigUpdated: (FolderConfig) -> Unit
) {
    SectionContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Deletion Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        DeletionMode.entries.forEach { mode ->
            val (label, description, icon) = when (mode) {
                DeletionMode.TRASH    -> Triple("Move to Trash", "Recoverable from system trash", Icons.Default.Delete)
                DeletionMode.DELETE   -> Triple("Delete Permanently", "Cannot be recovered", Icons.Default.DeleteForever)
                DeletionMode.ASK_AGAIN -> Triple("Ask Again", "Remind me to decide later", Icons.Default.HelpOutline)
            }
            DeletionModeRow(
                label = label,
                description = description,
                icon = icon,
                selected = config.deletionMode == mode,
                onClick = { onConfigUpdated(config.copy(deletionMode = mode)) }
            )
        }
    }
}

@Composable
fun DeletionModeRow(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ─── Section: Time Period Presets ─────────────────────────────────────────────

@Composable
fun TimePeriodPresetsSection(
    config: FolderConfig,
    onConfigUpdated: (FolderConfig) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val presets = remember(config.candidateTimePeriods) {
        decodeTimePeriodPresets(config.candidateTimePeriods)
    }

    SectionContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Time Period Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledTonalIconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add preset", modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "These appear as options when Auto Delete asks what to do with a file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (presets.isEmpty()) {
            Text(
                "No presets configured. Tap + to add one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Wrap-layout using FlowRow emulated with a Column of Rows
            FlowRowChips(
                items = presets,
                onRemove = { preset ->
                    if (presets.size > 1) {
                        val updated = presets.filter { it != preset }
                        onConfigUpdated(config.copy(candidateTimePeriods = encodeTimePeriodPresets(updated)))
                    }
                }
            )
        }
    }

    if (showAddDialog) {
        AddTimePeriodDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newPreset ->
                val existing = presets.toMutableList()
                // Avoid exact duplicates
                if (existing.none { it.millis == newPreset.millis }) {
                    existing.add(newPreset)
                    existing.sortBy { it.millis }
                    onConfigUpdated(config.copy(candidateTimePeriods = encodeTimePeriodPresets(existing)))
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FlowRowChips(
    items: List<TimePeriodPreset>,
    onRemove: (TimePeriodPreset) -> Unit
) {
    // Wrap chips across multiple rows manually
    val rows = mutableListOf<MutableList<TimePeriodPreset>>()
    var currentRow = mutableListOf<TimePeriodPreset>()
    items.forEach { preset ->
        currentRow.add(preset)
        if (currentRow.size == 3) {
            rows.add(currentRow)
            currentRow = mutableListOf()
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    TimePeriodChip(preset = preset, onRemove = { onRemove(preset) })
                }
            }
        }
    }
}

@Composable
fun TimePeriodChip(preset: TimePeriodPreset, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = {},
        label = {
            Text(preset.label, style = MaterialTheme.typography.labelMedium)
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove ${preset.label}",
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove)
            )
        },
        shape = RoundedCornerShape(50)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimePeriodDialog(
    onDismiss: () -> Unit,
    onAdd: (TimePeriodPreset) -> Unit
) {
    var valueText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(TimeUnit.MINUTES) }
    var expanded by remember { mutableStateOf(false) }
    val isValid = valueText.toLongOrNull()?.let { it > 0 } == true

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add Time Preset",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { if (it.length <= 6) valueText = it.filter { c -> c.isDigit() } },
                        label = { Text("Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = valueText.isNotEmpty() && !isValid
                    )

                    // Unit dropdown
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedUnit.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            TimeUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.displayName) },
                                    onClick = {
                                        selectedUnit = unit
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Preview
                if (isValid) {
                    val preview = TimePeriodPreset.from(valueText.toLong(), selectedUnit)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Will appear as: \"${preview.label}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val v = valueText.toLongOrNull() ?: return@Button
                            onAdd(TimePeriodPreset.from(v, selectedUnit))
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Preset")
                    }
                }
            }
        }
    }
}

// ─── Section: File Filters ────────────────────────────────────────────────────

@Composable
fun FileFiltersSection(
    config: FolderConfig,
    onConfigUpdated: (FolderConfig) -> Unit
) {
    var showAddDialog by remember { mutableStateOf<FilterListType?>(null) }
    var excludeExpanded by remember { mutableStateOf(true) }
    var includeExpanded by remember { mutableStateOf(false) }

    val excludeRules = remember(config.fileTypeExcludeList) {
        decodeFilterRules(config.fileTypeExcludeList)
    }
    val includeRules = remember(config.fileTypeIncludeList) {
        decodeFilterRules(config.fileTypeIncludeList)
    }

    SectionContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "File Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Control which files are monitored based on filename patterns.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // ── Exclusion list ──
        FilterListCard(
            title = "Exclude Files",
            subtitle = "Always skip files matching these patterns",
            icon = Icons.Default.Block,
            iconTint = MaterialTheme.colorScheme.error,
            rules = excludeRules,
            expanded = excludeExpanded,
            onToggleExpanded = { excludeExpanded = !excludeExpanded },
            onRemoveRule = { rule ->
                val updated = excludeRules.filter { it != rule }
                onConfigUpdated(config.copy(fileTypeExcludeList = encodeFilterRules(updated).takeIf { updated.isNotEmpty() }))
            },
            onAddRule = { showAddDialog = FilterListType.EXCLUDE }
        )

        Spacer(Modifier.height(12.dp))

        // ── Inclusion list ──
        FilterListCard(
            title = "Include Only",
            subtitle = "When set, only monitor files matching these patterns (leave empty to watch all)",
            icon = Icons.Default.CheckCircleOutline,
            iconTint = MaterialTheme.colorScheme.tertiary,
            rules = includeRules,
            expanded = includeExpanded,
            onToggleExpanded = { includeExpanded = !includeExpanded },
            onRemoveRule = { rule ->
                val updated = includeRules.filter { it != rule }
                onConfigUpdated(config.copy(fileTypeIncludeList = encodeFilterRules(updated).takeIf { updated.isNotEmpty() }))
            },
            onAddRule = { showAddDialog = FilterListType.INCLUDE }
        )
    }

    showAddDialog?.let { listType ->
        AddFilterRuleDialog(
            listType = listType,
            onDismiss = { showAddDialog = null },
            onAdd = { rule ->
                when (listType) {
                    FilterListType.EXCLUDE -> {
                        val updated = (excludeRules + rule).distinctBy { it.pattern + it.matchType }
                        onConfigUpdated(config.copy(fileTypeExcludeList = encodeFilterRules(updated)))
                    }
                    FilterListType.INCLUDE -> {
                        val updated = (includeRules + rule).distinctBy { it.pattern + it.matchType }
                        onConfigUpdated(config.copy(fileTypeIncludeList = encodeFilterRules(updated)))
                    }
                }
                showAddDialog = null
            }
        )
    }
}

enum class FilterListType { EXCLUDE, INCLUDE }

@Composable
fun FilterListCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    rules: List<FilterRule>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRemoveRule: (FilterRule) -> Unit,
    onAddRule: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (rules.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(iconTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    rules.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAddRule, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add rule", modifier = Modifier.size(18.dp), tint = iconTint)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                if (rules.isEmpty()) {
                    Text(
                        "No rules configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    rules.forEach { rule ->
                        FilterRuleRow(rule = rule, onRemove = { onRemoveRule(rule) })
                    }
                }
            }
        }
    }
}

@Composable
fun FilterRuleRow(rule: FilterRule, onRemove: () -> Unit) {
    val (matchLabel, matchColor) = when (rule.matchType) {
        FilterMatchType.PREFIX   -> "prefix" to MaterialTheme.colorScheme.secondary
        FilterMatchType.SUFFIX   -> "suffix" to MaterialTheme.colorScheme.tertiary
        FilterMatchType.CONTAINS -> "contains" to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Match type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(matchColor.copy(alpha = 0.15f))
                    .border(1.dp, matchColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    matchLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = matchColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove rule",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterRuleDialog(
    listType: FilterListType,
    onDismiss: () -> Unit,
    onAdd: (FilterRule) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var selectedMatchType by remember { mutableStateOf(FilterMatchType.PREFIX) }
    var expanded by remember { mutableStateOf(false) }
    val isValid = pattern.isNotBlank()

    val titleText = if (listType == FilterListType.EXCLUDE) "Add Exclusion Rule" else "Add Inclusion Rule"
    val hintExamples = when (selectedMatchType) {
        FilterMatchType.PREFIX   -> "e.g. \".trash\", \".pending\", \"temp_\""
        FilterMatchType.SUFFIX   -> "e.g. \".tmp\", \".partial\", \"_backup\""
        FilterMatchType.CONTAINS -> "e.g. \"cache\", \"thumb\", \"preview\""
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(titleText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Match type selector
                Text("Match Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterMatchType.entries.forEach { type ->
                        val label = when (type) {
                            FilterMatchType.PREFIX   -> "Prefix"
                            FilterMatchType.SUFFIX   -> "Suffix"
                            FilterMatchType.CONTAINS -> "Contains"
                        }
                        FilterChip(
                            selected = selectedMatchType == type,
                            onClick = { selectedMatchType = type },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    placeholder = { Text(hintExamples, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = pattern.isNotBlank() && pattern.length < 1
                )

                // Preview
                if (isValid) {
                    val previewText = when (selectedMatchType) {
                        FilterMatchType.PREFIX   -> "Files whose name starts with \"$pattern\""
                        FilterMatchType.SUFFIX   -> "Files whose name ends with \"$pattern\""
                        FilterMatchType.CONTAINS -> "Files whose name contains \"$pattern\""
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Preview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { if (isValid) onAdd(FilterRule(pattern.trim(), selectedMatchType)) },
                        enabled = isValid,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Rule")
                    }
                }
            }
        }
    }
}

// ─── Shared layout helpers ────────────────────────────────────────────────────

@Composable
private fun SectionContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(4.dp))
}
