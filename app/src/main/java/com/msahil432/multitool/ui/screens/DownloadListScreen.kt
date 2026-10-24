package com.msahil432.multitool.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    LaunchedEffect(Unit) {
        viewModel.triggerTranscodeForExistingIfNeeded()
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    DownloadListContent(
        downloads = downloads,
        innerPadding = innerPadding,
        onBack = onBack,
        onClearCompleted = { viewModel.clearCompleted() },
        onClearAll = { viewModel.clearAll() },
        onDeleteAll = { viewModel.deleteAll() },
        onRetry = { viewModel.retryDownload(it) },
        onCancel = { viewModel.cancelDownload(it) },
        onDelete = { viewModel.deleteDownload(it) },
        onOpen = { path, mime -> openDownloadedFile(context, path, mime) },
        onReloadSourcePage = onReloadSourcePage
    )
}

/**
 * Stateless content composable for [DownloadListScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onReloadSourcePage: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
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
                    if (downloads.isNotEmpty()) {
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
            if (downloads.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.DownloadDone,
                    title = "No downloads yet",
                    message = "Downloaded videos and GIFs will appear here."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = downloads,
                        key = { it.id }
                    ) { item ->
                        DownloadItemCard(
                            item = item,
                            onRetry = { onRetry(item.id) },
                            onCancel = { onCancel(item.id) },
                            onDelete = { onDelete(item.id) },
                            onOpen = { onOpen(item.localFilePath, item.mimeType) },
                            onReloadSourcePage = { onReloadSourcePage(item.sourcePageUrl) }
                        )
                    }
                }
            }
        }
    }
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
        DownloadListContent(
            downloads = listOf(
                DownloadItem(
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
                ),
                DownloadItem(
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
            )
        )
    }
}
