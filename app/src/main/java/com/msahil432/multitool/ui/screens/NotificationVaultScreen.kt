package com.msahil432.multitool.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.NotificationRepository
import com.msahil432.multitool.data.VaultedNotification
import com.msahil432.multitool.ui.components.ConfirmDialog
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationVaultScreen(
    notificationRepository: NotificationRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notifications by notificationRepository.allVaulted.collectAsState(initial = null)
    var showClearConfirm by remember { mutableStateOf(false) }

    // Map of package -> (Label, Icon)
    var appInfoMap by remember { mutableStateOf<Map<String, Pair<String, Bitmap?>>>(emptyMap()) }

    LaunchedEffect(notifications) {
        val currentList = notifications ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val newMap = currentList.map { it.packageName }.distinct().associateWith { pkg ->
                val label = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                val iconBitmap = try {
                    val drawable = pm.getApplicationIcon(pkg)
                    drawableToBitmap(drawable)
                } catch (_: Exception) {
                    null
                }
                label to iconBitmap
            }
            appInfoMap = newMap
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notification Vault", style = MaterialTheme.typography.titleLarge)
                        val count = notifications?.size ?: 0
                        Text(
                            text = if (count == 1) "1 item" else "$count items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!notifications.isNullOrEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.semantics { contentDescription = "Clear all notifications" }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                notifications == null -> {
                    // Todo: add message: "Loading vaulted notifications..."
                    LoadingState()
                }
                notifications!!.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.NotificationsOff,
                        title = "No held notifications",
                        message = "Notifications silenced during active focus schedules will appear here in your vault."
                    )
                }
                else -> {
                    val list = notifications!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list, key = { it.id }) { item ->
                            val (appLabel, appIcon) = appInfoMap[item.packageName]
                                ?: (item.packageName to null)

                            VaultedNotificationCard(
                                item = item,
                                appLabel = appLabel,
                                appIcon = appIcon,
                                onDelete = {
                                    coroutineScope.launch {
                                        notificationRepository.deleteById(item.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear Notification Vault?",
            text = "All vaulted notifications will be permanently removed.",
            confirmLabel = "Clear All",
            onConfirm = {
                showClearConfirm = false
                coroutineScope.launch {
                    notificationRepository.clearAll()
                }
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
fun VaultedNotificationCard(
    item: VaultedNotification,
    appLabel: String,
    appIcon: Bitmap?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Notification from $appLabel: ${item.title ?: "Untitled"}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: App icon + App name + Delivery status + Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatNotificationTime(item.postedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delivery badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.delivered) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = if (item.delivered) "Delivered" else "Held",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (item.delivered) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Delete notification" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Body: Title & Content text
            if (!item.title.isNullOrBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!item.text.isNullOrBlank()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatNotificationTime(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val now = LocalDateTime.now()

    return if (ldt.toLocalDate() == now.toLocalDate()) {
        "Today " + ldt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } else if (ldt.toLocalDate() == now.toLocalDate().minusDays(1)) {
        "Yesterday " + ldt.format(DateTimeFormatter.ofPattern("h:mm a"))
    } else {
        ldt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Preview(name = "VaultedNotificationCard Light", showBackground = true)
@Composable
private fun VaultedNotificationCardPreviewLight() {
    MultiToolTheme {
        VaultedNotificationCard(
            item = VaultedNotification(
                id = 1,
                packageName = "com.instagram.android",
                title = "alex_smith sent you a message",
                text = "Hey, are you free for a call tonight?",
                postedAt = System.currentTimeMillis() - 15 * 60_000,
                delivered = false
            ),
            appLabel = "Instagram",
            appIcon = null,
            onDelete = {}
        )
    }
}

@Preview(name = "VaultedNotificationCard Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VaultedNotificationCardPreviewDark() {
    MultiToolTheme {
        VaultedNotificationCard(
            item = VaultedNotification(
                id = 2,
                packageName = "com.whatsapp",
                title = "Project Team (3 messages)",
                text = "Sarah: The release is scheduled for tomorrow at 10 AM.",
                postedAt = System.currentTimeMillis() - 3600_000,
                delivered = true
            ),
            appLabel = "WhatsApp",
            appIcon = null,
            onDelete = {}
        )
    }
}
