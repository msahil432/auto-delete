package com.msahil432.multitool.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msahil432.multitool.data.DownloadItem
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.DownloadStatus
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.ui.components.DownloadItemCard
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.theme.MultiToolTheme
import io.sentry.Sentry
import java.io.File
import java.time.ZoneId

/**
 * Screen presenting persistent download history with progress, completion status,
 * file opening, retrying failed transfers, and deletion actions.
 */
@Composable
fun DownloadListScreen(
    downloadRepository: DownloadRepository,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onReloadSourcePage: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: VideoDownloaderViewModel = viewModel(
        factory = VideoDownloaderViewModel.Factory(downloadRepository, context.applicationContext)
    )

    val downloads by viewModel.downloads.collectAsState()
    val groupedDownloads by viewModel.groupedDownloads.collectAsState()
    val selectedStatusFilters by viewModel.selectedStatusFilters.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.triggerTranscodeForExistingIfNeeded()
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    DownloadListContent(
        totalDownloadsCount = downloads.size,
        groupedDownloads = groupedDownloads,
        selectedStatusFilters = selectedStatusFilters,
        innerPadding = innerPadding,
        onBack = onBack,
        onToggleStatusFilter = { viewModel.toggleStatusFilter(it) },
        onClearStatusFilters = { viewModel.clearStatusFilters() },
        onToggleHourGroup = { viewModel.toggleHourGroupExpansion(it) },
        onClearCompleted = { viewModel.clearCompleted() },
        onClearAll = { viewModel.clearAll() },
        onDeleteAll = { viewModel.deleteAll() },
        onRetry = { viewModel.retryDownload(it) },
        onCancel = { viewModel.cancelDownload(it) },
        onDelete = { viewModel.deleteDownload(it) },
        onOpen = { path, mime -> openDownloadedFile(context, path, mime) },
        onReloadSourcePage = onReloadSourcePage,
        onRetryTranscode = { viewModel.retryTranscode(it) }
    )
}

/**
 * Header row for a collapsible 1-hour download group.
 */
@Composable
fun DownloadHourGroupHeader(
    group: DownloadHourGroup,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = "${group.title}, ${group.items.size} ${if (group.items.size == 1) "item" else "items"}, ${if (group.isExpanded) "expanded" else "collapsed"}"
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${group.items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                imageVector = if (group.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (group.isExpanded) "Collapse ${group.title}" else "Expand ${group.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Stateless content composable for [DownloadListScreen] with collapsible hour groups and multi-status filtering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListContent(
    totalDownloadsCount: Int,
    groupedDownloads: List<DownloadHourGroup>,
    selectedStatusFilters: Set<DownloadFilterStatus> = emptySet(),
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {},
    onToggleStatusFilter: (DownloadFilterStatus) -> Unit = {},
    onClearStatusFilters: () -> Unit = {},
    onToggleHourGroup: (Long) -> Unit = {},
    onClearCompleted: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onRetry: (Long) -> Unit = {},
    onCancel: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onOpen: (String?, String?) -> Unit = { _, _ -> },
    onReloadSourcePage: (String) -> Unit = {},
    onRetryTranscode: (Long) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (totalDownloadsCount > 0) {
                        IconButton(onClick = { showFilterMenu = true }) {
                            if (selectedStatusFilters.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text("${selectedStatusFilters.size}")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filter by status (${selectedStatusFilters.size} active)",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter by status"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DownloadFilterStatus.entries.forEach { status ->
                                val isChecked = selectedStatusFilters.contains(status)
                                DropdownMenuItem(
                                    text = { Text(status.displayName) },
                                    leadingIcon = {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null
                                        )
                                    },
                                    onClick = {
                                        onToggleStatusFilter(status)
                                    }
                                )
                            }
                            if (selectedStatusFilters.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Reset Filters") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    },
                                    onClick = {
                                        showFilterMenu = false
                                        onClearStatusFilters()
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Completed") },
                                onClick = {
                                    showMenu = false
                                    onClearCompleted()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All") },
                                onClick = {
                                    showMenu = false
                                    onClearAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete All", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteAllConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showDeleteAllConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirm = false },
                title = { Text("Delete All Downloads?") },
                text = { Text("This will permanently delete all downloaded files from your device. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteAllConfirm = false
                        onDeleteAll()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        val bottomNavPadding = maxOf(paddingValues.calculateBottomPadding(), innerPadding.calculateBottomPadding())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = bottomNavPadding
                )
        ) {
            if (totalDownloadsCount == 0) {
                EmptyState(
                    icon = Icons.Default.DownloadDone,
                    title = "No downloads yet",
                    message = "Downloaded videos and GIFs will appear here."
                )
            } else if (groupedDownloads.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FilterList,
                    title = "No matching downloads",
                    message = "No downloads match the selected status filters.",
                    actionLabel = "Reset Filters",
                    onAction = onClearStatusFilters
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    groupedDownloads.forEach { group ->
                        item(key = "header_${group.hourStartEpochMillis}") {
                            DownloadHourGroupHeader(
                                group = group,
                                onToggle = { onToggleHourGroup(group.hourStartEpochMillis) }
                            )
                        }
                        if (group.isExpanded) {
                            items(
                                items = group.items,
                                key = { it.id }
                            ) { item ->
                                DownloadItemCard(
                                    item = item,
                                    onRetry = { onRetry(item.id) },
                                    onCancel = { onCancel(item.id) },
                                    onDelete = { onDelete(item.id) },
                                    onOpen = { onOpen(item.localFilePath, item.mimeType) },
                                    onReloadSourcePage = { onReloadSourcePage(item.sourcePageUrl) },
                                    onRetryTranscode = { onRetryTranscode(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible overload of [DownloadListContent] accepting a plain list of [DownloadItem]s.
 */
@Composable
fun DownloadListContent(
    downloads: List<DownloadItem>,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {},
    onClearCompleted: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onRetry: (Long) -> Unit = {},
    onCancel: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onOpen: (String?, String?) -> Unit = { _, _ -> },
    onReloadSourcePage: (String) -> Unit = {},
    onRetryTranscode: (Long) -> Unit = {}
) {
    val groups = remember(downloads) {
        buildHourGroups(
            downloads = downloads,
            statusFilters = emptySet(),
            userToggledGroups = emptyMap(),
            zoneId = ZoneId.systemDefault(),
            clock = { System.currentTimeMillis() }
        )
    }
    DownloadListContent(
        totalDownloadsCount = downloads.size,
        groupedDownloads = groups,
        selectedStatusFilters = emptySet(),
        innerPadding = innerPadding,
        onBack = onBack,
        onToggleStatusFilter = {},
        onClearStatusFilters = {},
        onToggleHourGroup = {},
        onClearCompleted = onClearCompleted,
        onClearAll = onClearAll,
        onDeleteAll = onDeleteAll,
        onRetry = onRetry,
        onCancel = onCancel,
        onDelete = onDelete,
        onOpen = onOpen,
        onReloadSourcePage = onReloadSourcePage,
        onRetryTranscode = onRetryTranscode
    )
}

/**
 * Dispatches an [Intent.ACTION_VIEW] to an external media player using [FileProvider].
 */
private fun openDownloadedFile(context: Context, filePath: String?, mimeType: String?) {
    if (filePath == null) return
    val file = File(filePath)
    if (!file.exists()) return

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open media with").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Sentry.captureException(e)
    }
}

@Preview(showBackground = true, name = "DownloadListScreen Empty Light")
@Composable
private fun DownloadListEmptyPreviewLight() {
    MultiToolTheme {
        DownloadListContent(downloads = emptyList())
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DownloadListScreen Empty Dark")
@Composable
private fun DownloadListEmptyPreviewDark() {
    MultiToolTheme {
        DownloadListContent(downloads = emptyList())
    }
}

@Preview(showBackground = true, name = "DownloadListScreen Items Light")
@Composable
private fun DownloadListItemsPreviewLight() {
    MultiToolTheme {
        val item1 = DownloadItem(
            id = 1,
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/1.mp4",
            fileName = "trailer.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            status = DownloadStatus.COMPLETED,
            progressPercent = 100,
            totalBytes = 12_000_000,
            downloadedBytes = 12_000_000,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val item2 = DownloadItem(
            id = 2,
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/2.mp4",
            fileName = "clip.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            status = DownloadStatus.DOWNLOADING,
            progressPercent = 45,
            totalBytes = 20_000_000,
            downloadedBytes = 9_000_000,
            createdAt = 2000L,
            updatedAt = 2000L
        )
        DownloadListContent(
            totalDownloadsCount = 2,
            groupedDownloads = listOf(
                DownloadHourGroup(
                    hourStartEpochMillis = 0L,
                    title = "Today, 7:00 PM - 8:00 PM",
                    items = listOf(item1, item2),
                    isExpanded = true
                )
            ),
            selectedStatusFilters = emptySet()
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DownloadListScreen Items Dark")
@Composable
private fun DownloadListItemsPreviewDark() {
    MultiToolTheme {
        val item = DownloadItem(
            id = 1,
            sourcePageUrl = "https://example.com",
            mediaUrl = "https://example.com/1.mp4",
            fileName = "trailer.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            status = DownloadStatus.COMPLETED,
            progressPercent = 100,
            totalBytes = 12_000_000,
            downloadedBytes = 12_000_000,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        DownloadListContent(
            totalDownloadsCount = 1,
            groupedDownloads = listOf(
                DownloadHourGroup(
                    hourStartEpochMillis = 0L,
                    title = "Today, 7:00 PM - 8:00 PM",
                    items = listOf(item),
                    isExpanded = true
                )
            ),
            selectedStatusFilters = setOf(DownloadFilterStatus.COMPLETED)
        )
    }
}

