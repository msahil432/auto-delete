package com.msahil432.multitool.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msahil432.multitool.data.DownloadRepository
import com.msahil432.multitool.data.MediaType
import com.msahil432.multitool.downloader.DetectionStrategy
import com.msahil432.multitool.downloader.DetectedMedia
import com.msahil432.multitool.downloader.PageLoadState
import com.msahil432.multitool.ui.components.DetectedMediaItem
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Main Video Downloader screen embedding a web browser view, URL navigation bar,
 * real-time detected media floating badge, and media selection bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloaderScreen(
    downloadRepository: DownloadRepository,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    initialUrl: String? = null
) {
    val context = LocalContext.current
    val viewModel: VideoDownloaderViewModel = viewModel(
        factory = VideoDownloaderViewModel.Factory(downloadRepository, context.applicationContext)
    )

    val currentUrl by viewModel.currentUrl.collectAsState()
    val detectedMedia by viewModel.detector.detectedMedia.collectAsState()
    val pageLoadingState by viewModel.detector.pageLoadingState.collectAsState()
    val showSelectionDialog by viewModel.showSelectionDialog.collectAsState()
    val selectedMedia by viewModel.selectedMedia.collectAsState()

    val webView = remember {
        WebView(context).apply {
            viewModel.detector.configure(this)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.onUrlChanged(initialUrl)
            viewModel.loadUrl(webView)
        }
    }

    BackHandler(enabled = true) {
        if (webView.canGoBack()) {
            webView.goBack()
        } else if (pageLoadingState != PageLoadState.IDLE) {
            viewModel.clearUrl(webView)
        } else {
            onBack()
        }
    }

    val clipboard = LocalClipboard.current
    var clipboardText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pageLoadingState, clipboard) {
        try {
            val clipEntry = clipboard.getClipEntry()
            val raw = if (clipEntry != null && clipEntry.clipData.itemCount > 0) {
                clipEntry.clipData.getItemAt(0)?.text?.toString()?.trim()
            } else {
                null
            }
            clipboardText = if (!raw.isNullOrBlank() && (raw.startsWith("http://") || raw.startsWith("https://") || raw.contains("."))) {
                raw
            } else {
                null
            }
        } catch (_: Exception) {
            clipboardText = null
        }
    }

    VideoDownloaderContent(
        currentUrl = currentUrl,
        pageLoadingState = pageLoadingState,
        detectedMedia = detectedMedia,
        showSelectionDialog = showSelectionDialog,
        selectedMedia = selectedMedia,
        innerPadding = innerPadding,
        clipboardUrl = clipboardText,
        onBack = {
            if (webView.canGoBack()) {
                webView.goBack()
            } else if (pageLoadingState != PageLoadState.IDLE) {
                viewModel.clearUrl(webView)
            } else {
                onBack()
            }
        },
        onNavigateToDownloads = onNavigateToDownloads,
        onUrlChanged = { viewModel.onUrlChanged(it) },
        onLoadUrl = { viewModel.loadUrl(webView) },
        onClearUrl = { viewModel.clearUrl(webView) },
        onShowMediaSelection = { viewModel.showMediaSelection() },
        onDismissMediaSelection = { viewModel.dismissMediaSelection() },
        onToggleMediaSelection = { viewModel.toggleMediaSelection(it) },
        onSelectAll = { viewModel.selectAll() },
        onDeselectAll = { viewModel.deselectAll() },
        onDownloadSelected = { viewModel.downloadSelected() },
        webViewComposable = {
            AndroidView(
                factory = {
                    webView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            )
        }
    )
}

/**
 * Stateless content composable for [VideoDownloaderScreen] providing previewability and layout isolation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloaderContent(
    currentUrl: String,
    pageLoadingState: PageLoadState,
    detectedMedia: List<DetectedMedia>,
    showSelectionDialog: Boolean,
    selectedMedia: Set<DetectedMedia>,
    innerPadding: PaddingValues = PaddingValues(),
    clipboardUrl: String? = null,
    onBack: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onLoadUrl: () -> Unit = {},
    onClearUrl: () -> Unit = {},
    onShowMediaSelection: () -> Unit = {},
    onDismissMediaSelection: () -> Unit = {},
    onToggleMediaSelection: (DetectedMedia) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onDownloadSelected: () -> Unit = {},
    webViewComposable: (@Composable () -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Downloader", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "Open Downloads List"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (detectedMedia.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onShowMediaSelection,
                    icon = {
                        BadgedBox(
                            badge = {
                                Badge { Text("${detectedMedia.size}") }
                            }
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null)
                        }
                    },
                    text = { Text("Available Media (${detectedMedia.size})") },
                    modifier = Modifier.semantics {
                        contentDescription = "${detectedMedia.size} downloadable media detected. Open selection sheet."
                    }
                )
            }
        }
    ) { paddingValues ->
        val bottomNavPadding = maxOf(paddingValues.calculateBottomPadding(), innerPadding.calculateBottomPadding())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = bottomNavPadding
                )
        ) {
            // URL Input Bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentUrl,
                        onValueChange = onUrlChanged,
                        placeholder = { Text("Enter webpage URL...") },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        },
                        trailingIcon = {
                            if (currentUrl.isNotEmpty()) {
                                IconButton(onClick = onClearUrl) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onLoadUrl() }
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onLoadUrl,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Load webpage"
                        )
                    }
                }
            }

            // Page Loading Indicator
            if (pageLoadingState == PageLoadState.LOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Content Area: Empty landing state when IDLE, or Embedded WebView when loading/loaded
            if (pageLoadingState == PageLoadState.IDLE) {
                EmptyState(
                    icon = Icons.Default.Language,
                    title = "Video & Media Downloader",
                    message = "Enter a web URL above to browse websites and detect downloadable videos, audio streams, and animated GIFs.",
                    actionLabel = if (!clipboardUrl.isNullOrBlank()) "Paste from Clipboard" else null,
                    onAction = if (!clipboardUrl.isNullOrBlank()) {
                        {
                            onUrlChanged(clipboardUrl)
                            onLoadUrl()
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                ) {
                    webViewComposable?.invoke()

                    if (pageLoadingState == PageLoadState.ERROR) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Failed to load webpage. Check URL and connection.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onLoadUrl) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Selection Bottom Sheet
    if (showSelectionDialog) {
        ModalBottomSheet(
            onDismissRequest = onDismissMediaSelection,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Media (${detectedMedia.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("Select All")
                        }
                        TextButton(onClick = onDeselectAll) {
                            Text("Deselect")
                        }
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp)
                ) {
                    items(detectedMedia, key = { it.url }) { media ->
                        val isSelected = selectedMedia.any { it.url == media.url }
                        DetectedMediaItem(
                            media = media,
                            isSelected = isSelected,
                            onToggle = { onToggleMediaSelection(media) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDownloadSelected,
                    enabled = selectedMedia.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (selectedMedia.isNotEmpty()) {
                            "Download Selected (${selectedMedia.size})"
                        } else {
                            "Select Media to Download"
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "VideoDownloader Light - Idle")
@Composable
private fun VideoDownloaderScreenPreviewLightIdle() {
    MultiToolTheme {
        VideoDownloaderContent(
            currentUrl = "",
            pageLoadingState = PageLoadState.IDLE,
            detectedMedia = emptyList(),
            showSelectionDialog = false,
            selectedMedia = emptySet()
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "VideoDownloader Dark - Idle")
@Composable
private fun VideoDownloaderScreenPreviewDarkIdle() {
    MultiToolTheme {
        VideoDownloaderContent(
            currentUrl = "",
            pageLoadingState = PageLoadState.IDLE,
            detectedMedia = emptyList(),
            showSelectionDialog = false,
            selectedMedia = emptySet()
        )
    }
}

@Preview(showBackground = true, name = "VideoDownloader Light - Loaded with Media")
@Composable
private fun VideoDownloaderScreenPreviewLightLoaded() {
    MultiToolTheme {
        VideoDownloaderContent(
            currentUrl = "https://example.com/videos",
            pageLoadingState = PageLoadState.LOADED,
            detectedMedia = listOf(
                DetectedMedia(
                    url = "https://example.com/video1.mp4",
                    mimeType = "video/mp4",
                    suggestedFileName = "video1.mp4",
                    mediaType = MediaType.VIDEO,
                    detectionStrategy = DetectionStrategy.DOM_ELEMENT
                )
            ),
            showSelectionDialog = false,
            selectedMedia = emptySet()
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "VideoDownloader Dark - Loaded with Media")
@Composable
private fun VideoDownloaderScreenPreviewDarkLoaded() {
    MultiToolTheme {
        VideoDownloaderContent(
            currentUrl = "https://example.com/videos",
            pageLoadingState = PageLoadState.LOADED,
            detectedMedia = listOf(
                DetectedMedia(
                    url = "https://example.com/video1.mp4",
                    mimeType = "video/mp4",
                    suggestedFileName = "video1.mp4",
                    mediaType = MediaType.VIDEO,
                    detectionStrategy = DetectionStrategy.DOM_ELEMENT
                )
            ),
            showSelectionDialog = false,
            selectedMedia = emptySet()
        )
    }
}
