package com.msahil432.multitool.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.msahil432.multitool.downloader.PageLoadState
import com.msahil432.multitool.ui.components.DetectedMediaItem

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
                    onClick = { viewModel.showMediaSelection() },
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
                        onValueChange = { viewModel.onUrlChanged(it) },
                        placeholder = { Text("Enter webpage URL...") },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        },
                        trailingIcon = {
                            if (currentUrl.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { viewModel.loadUrl(webView) }
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.loadUrl(webView) },
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

            // Embedded WebView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Media Selection Bottom Sheet
    if (showSelectionDialog) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissMediaSelection() },
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
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("Select All")
                        }
                        TextButton(onClick = { viewModel.deselectAll() }) {
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
                    items(detectedMedia) { media ->
                        val isSelected = selectedMedia.contains(media)
                        DetectedMediaItem(
                            media = media,
                            isSelected = isSelected,
                            onToggle = { viewModel.toggleMediaSelection(media) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.downloadSelected() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "VideoDownloader Light")
@Composable
private fun VideoDownloaderScreenPreviewLight() {
    com.msahil432.multitool.ui.theme.MultiToolTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Video Downloader") },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("WebView Placeholder", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "VideoDownloader Dark")
@Composable
private fun VideoDownloaderScreenPreviewDark() {
    com.msahil432.multitool.ui.theme.MultiToolTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Video Downloader") },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("WebView Placeholder", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
