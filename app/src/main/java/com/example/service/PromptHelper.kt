package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.FolderConfig
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.AutoDeleteApp
import com.example.data.PendingAction
import com.example.data.ActionStatus
import com.example.data.LogAction
import com.example.data.ActivityLogEntry
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object PromptHelper {
    fun showPrompt(context: Context, config: FolderConfig, filePath: String) {
        if (Settings.canDrawOverlays(context)) {
            showOverlayPrompt(context, config, filePath)
        } else {
            showNotificationPrompt(context, config, filePath)
        }
    }

    private fun showOverlayPrompt(context: Context, config: FolderConfig, filePath: String) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val composeView = ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.padding(16.dp).widthIn(max = 300.dp),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        PromptContent(
                            config = config,
                            filePath = filePath,
                            onAction = { timePeriod ->
                                handleAction(context, config, filePath, timePeriod)
                                windowManager.removeViewImmediate(this@apply)
                            },
                            onKeep = {
                                handleKeep(context, config, filePath)
                                windowManager.removeViewImmediate(this@apply)
                            }
                        )
                    }
                }
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        val lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager.addView(composeView, layoutParams)
        
        // Auto dismiss after some time (e.g., 10 seconds)
        CoroutineScope(Dispatchers.Main).launch {
            delay(10000)
            if (composeView.parent != null) {
                windowManager.removeViewImmediate(composeView)
                // Handle default action on ignore
                handleIgnore(context, config, filePath)
            }
        }
    }

    private fun showNotificationPrompt(context: Context, config: FolderConfig, filePath: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "prompts", "Prompts", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val periods = config.recentlyUsedPeriods.split(",").filter { it.isNotBlank() }
        val candidatePeriods = if (periods.isNotEmpty()) periods else config.candidateTimePeriods.split(",").filter { it.isNotBlank() }
        
        val actions = candidatePeriods.take(3).map { period ->
            val actionIntent = Intent(context, ActionReceiver::class.java).apply {
                action = "com.example.ACTION_SCHEDULE"
                putExtra("folderId", config.id)
                putExtra("filePath", filePath)
                putExtra("timePeriod", period)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                filePath.hashCode() + period.hashCode(), 
                actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(0, "In $period", pendingIntent)
        }

        val builder = NotificationCompat.Builder(context, "prompts")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("New File Detected")
            .setContentText("Schedule auto-delete for ${filePath.substringAfterLast("/")}?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        actions.forEach { builder.addAction(it) }

        val keepIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "com.example.ACTION_KEEP"
            putExtra("folderId", config.id)
            putExtra("filePath", filePath)
        }
        val keepPendingIntent = PendingIntent.getBroadcast(
            context,
            filePath.hashCode(),
            keepIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Keep", keepPendingIntent)

        notificationManager.notify(filePath.hashCode(), builder.build())
    }

    private fun handleIgnore(context: Context, config: FolderConfig, filePath: String) {
        if (config.defaultActionOnIgnore == "KEEP") {
            handleKeep(context, config, filePath)
        } else {
            handleAction(context, config, filePath, config.defaultActionOnIgnore)
        }
    }

    private fun handleAction(context: Context, config: FolderConfig, filePath: String, timePeriod: String) {
        if (timePeriod.equals("never", ignoreCase = true)) {
            handleKeep(context, config, filePath)
            return
        }
        
        val durationMillis = parseTimePeriod(timePeriod)
        
        val db = (context.applicationContext as AutoDeleteApp).database
        CoroutineScope(Dispatchers.IO).launch {
            db.appDao().insertPendingAction(
                PendingAction(
                    folderId = config.id,
                    fileUri = filePath,
                    scheduledAt = System.currentTimeMillis() + durationMillis,
                    status = ActionStatus.PENDING
                )
            )
            // Schedule worker here
            FileActionWorker.schedule(context, config.id, filePath, durationMillis)
            
            // Update recently used periods
            updateRecentlyUsed(db, config, timePeriod)
        }
    }

    private fun handleKeep(context: Context, config: FolderConfig, filePath: String) {
        val db = (context.applicationContext as AutoDeleteApp).database
        CoroutineScope(Dispatchers.IO).launch {
            db.appDao().insertActivityLog(
                ActivityLogEntry(
                    folderId = config.id,
                    fileName = filePath.substringAfterLast("/"),
                    fileUri = filePath,
                    action = LogAction.KEPT,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
    
    private suspend fun updateRecentlyUsed(db: com.example.data.AppDatabase, config: FolderConfig, timePeriod: String) {
        val currentList = config.recentlyUsedPeriods.split(",").filter { it.isNotBlank() }.toMutableList()
        currentList.remove(timePeriod)
        currentList.add(0, timePeriod)
        val newList = currentList.take(4).joinToString(",")
        db.appDao().updateFolderConfig(config.copy(recentlyUsedPeriods = newList))
    }

    private fun parseTimePeriod(period: String): Long {
        // Handle "30 sec", "1 hour", "1 week", "1 month"
        val lower = period.lowercase().trim()
        val num = lower.filter { it.isDigit() }.toLongOrNull() ?: 1L
        return when {
            lower.contains("sec") -> num * 1000L
            lower.contains("min") -> num * 60_000L
            lower.contains("h") -> num * 3600_000L
            lower.contains("d") -> num * 86_400_000L
            lower.contains("w") -> num * 604_800_000L
            lower.contains("mo") -> num * 2_592_000_000L
            else -> 60_000L
        }
    }
}

@Composable
fun PromptContent(
    config: FolderConfig,
    filePath: String,
    onAction: (String) -> Unit,
    onKeep: () -> Unit
) {
    val periods = config.recentlyUsedPeriods.split(",").filter { it.isNotBlank() }
    val candidatePeriods = if (periods.isNotEmpty()) periods else config.candidateTimePeriods.split(",").filter { it.isNotBlank() }
    val topPeriods = candidatePeriods.take(4)

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "New File Detected", style = MaterialTheme.typography.titleMedium)
        Text(text = filePath.substringAfterLast("/"), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            topPeriods.take(2).forEach { period ->
                Button(onClick = { onAction(period) }) { Text(period) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            topPeriods.drop(2).take(2).forEach { period ->
                Button(onClick = { onAction(period) }) { Text(period) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) {
            Text("Keep")
        }
    }
}

class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: android.os.Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}
