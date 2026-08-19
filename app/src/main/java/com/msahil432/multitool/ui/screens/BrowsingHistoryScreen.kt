package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.accessibility.BrowserSignatures
import com.msahil432.multitool.data.BrowsingEvent
import com.msahil432.multitool.data.BrowsingKind
import com.msahil432.multitool.data.BrowsingRepository
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.theme.MultiToolTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowsingHistoryScreen(
    browsingRepository: BrowsingRepository,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit
) {
    val events by browsingRepository.allRecent().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browsing Activity", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val bottomNavPadding = maxOf(paddingValues.calculateBottomPadding(), innerPadding.calculateBottomPadding())
        BrowsingHistoryContent(
            events = events,
            innerPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = bottomNavPadding
            ),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BrowsingHistoryContent(
    events: List<BrowsingEvent>,
    innerPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "No browsing activity",
            message = "Visited domains and search queries will appear here when browser tracking is enabled.",
            modifier = modifier.padding(innerPadding)
        )
    } else {
        val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            )
        ) {
            items(events, key = { it.id }) { event ->
                val formattedTime = timeFormat.format(Date(event.timestamp))
                val browserLabel = getBrowserDisplayName(event.packageName)

                BrowsingEventRow(
                    event = event,
                    time = formattedTime,
                    browserLabel = browserLabel
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
            }
        }
    }
}

@Composable
private fun BrowsingEventRow(
    event: BrowsingEvent,
    time: String,
    browserLabel: String,
    modifier: Modifier = Modifier
) {
    val isUrl = event.kind == BrowsingKind.URL
    val icon = if (isUrl) Icons.Default.Language else Icons.Default.Search
    val tint = if (isUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val kindLabel = if (isUrl) "URL" else "Search"

    val semanticsDesc = buildString {
        append(kindLabel)
        append(": ")
        append(event.value)
        append(", ")
        append(browserLabel)
        append(", ")
        append(time)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = kindLabel,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = browserLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Kind badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isUrl) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isUrl) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private fun getBrowserDisplayName(packageName: String): String {
    return when (packageName) {
        BrowserSignatures.PKG_CHROME -> "Chrome"
        BrowserSignatures.PKG_FIREFOX -> "Firefox"
        BrowserSignatures.PKG_BRAVE -> "Brave"
        BrowserSignatures.PKG_EDGE -> "Edge"
        BrowserSignatures.PKG_DUCKDUCKGO -> "DuckDuckGo"
        BrowserSignatures.PKG_OPERA -> "Opera"
        BrowserSignatures.PKG_SAMSUNG -> "Samsung Internet"
        else -> packageName.substringAfterLast('.')
    }
}

@Preview(showBackground = true, name = "BrowsingHistoryScreen Preview")
@Composable
private fun BrowsingHistoryPreview() {
    MultiToolTheme {
        BrowsingHistoryContent(
            events = listOf(
                BrowsingEvent(
                    id = 1,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                    packageName = "com.android.chrome",
                    kind = BrowsingKind.URL,
                    value = "github.com"
                ),
                BrowsingEvent(
                    id = 2,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    packageName = "org.mozilla.firefox",
                    kind = BrowsingKind.SEARCH_QUERY,
                    value = "kotlin coroutines best practices"
                )
            )
        )
    }
}
