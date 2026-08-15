package com.msahil432.multitool.service

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
import com.msahil432.multitool.MainActivity
import com.msahil432.multitool.data.FolderConfig
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
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.PendingAction
import com.msahil432.multitool.data.ActionStatus
import com.msahil432.multitool.data.LogAction
import com.msahil432.multitool.data.ActivityLogEntry
import com.msahil432.multitool.data.TimePeriodPreset
import com.msahil432.multitool.data.decodeTimePeriodPresets
import com.msahil432.multitool.data.encodeTimePeriodPresets
import kotlinx.coroutines.*

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
                            onAction = { preset ->
                                handleAction(context, config, filePath, preset)
                                windowManager.removeViewImmediate(this@apply)
                            },
                            onKeep = {
                                handleKeep(context, config, filePath)
                                windowManager.removeViewImmediate(this@apply)
                            },
                            onMove = if (config.moveRuleEnabled) {
                                {
                                    handleMove(context, config, filePath)
                                    windowManager.removeViewImmediate(this@apply)
                                }
                            } else null
                        )
                    }
                }
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

        // Decode presets from JSON — fall back to candidateTimePeriods if recentlyUsed is empty
        val recentlyUsed = decodeTimePeriodPresets(config.recentlyUsedPeriods)
        val candidates   = decodeTimePeriodPresets(config.candidateTimePeriods)
        val displayPresets = if (recentlyUsed.isNotEmpty()) recentlyUsed else candidates

        val builder = NotificationCompat.Builder(context, "prompts")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("New File Detected")
            .setContentText("Schedule auto-delete for ${filePath.substringAfterLast("/")}?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (config.moveRuleEnabled) {
            // Move Rule active: show [Move Now] [1st time-delay] [2nd time-delay]
            val moveIntent = Intent(context, ActionReceiver::class.java).apply {
                action = "com.msahil432.multitool.ACTION_MOVE"
                putExtra("folderId", config.id)
                putExtra("filePath", filePath)
            }
            val movePendingIntent = PendingIntent.getBroadcast(
                context,
                filePath.hashCode() xor 0xABC123,
                moveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "➡️ Move Now", movePendingIntent)

            // Add up to 2 time-delay actions
            displayPresets.take(2).forEach { preset ->
                val actionIntent = Intent(context, ActionReceiver::class.java).apply {
                    action = "com.msahil432.multitool.ACTION_SCHEDULE"
                    putExtra("folderId", config.id)
                    putExtra("filePath", filePath)
                    putExtra("timePeriodMillis", preset.millis)
                    putExtra("timePeriodLabel", preset.label)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    filePath.hashCode() + preset.millis.hashCode(),
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "In ${preset.label}", pendingIntent)
            }
        } else {
            // No Move Rule: show up to 3 time-delay actions + Keep
            val actions = displayPresets.take(3).map { preset ->
                val actionIntent = Intent(context, ActionReceiver::class.java).apply {
                    action = "com.msahil432.multitool.ACTION_SCHEDULE"
                    putExtra("folderId", config.id)
                    putExtra("filePath", filePath)
                    putExtra("timePeriodMillis", preset.millis)
                    putExtra("timePeriodLabel", preset.label)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    filePath.hashCode() + preset.millis.hashCode(),
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                NotificationCompat.Action(0, "In ${preset.label}", pendingIntent)
            }
            actions.forEach { builder.addAction(it) }

            val keepIntent = Intent(context, ActionReceiver::class.java).apply {
                action = "com.msahil432.multitool.ACTION_KEEP"
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
        }

        notificationManager.notify(filePath.hashCode(), builder.build())
    }

    private fun handleIgnore(context: Context, config: FolderConfig, filePath: String) {
        if (config.defaultActionOnIgnore == "KEEP") {
            handleKeep(context, config, filePath)
        } else {
            // defaultActionOnIgnore stores a label — find the matching preset by label
            val candidates = decodeTimePeriodPresets(config.candidateTimePeriods)
            val preset = candidates.firstOrNull { it.label.equals(config.defaultActionOnIgnore, ignoreCase = true) }
            if (preset != null) {
                handleAction(context, config, filePath, preset)
            } else {
                handleKeep(context, config, filePath)
            }
        }
    }

    private fun handleMove(context: Context, config: FolderConfig, filePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            MoveHelper.performMove(context, config, filePath)
        }
    }

    private fun handleAction(context: Context, config: FolderConfig, filePath: String, preset: TimePeriodPreset) {
        val db = (context.applicationContext as MultiToolApp).database
        CoroutineScope(Dispatchers.IO).launch {
            db.appDao().insertPendingAction(
                PendingAction(
                    folderId = config.id,
                    fileUri = filePath,
                    scheduledAt = System.currentTimeMillis() + preset.millis,
                    status = ActionStatus.PENDING
                )
            )
            FileActionWorker.schedule(context, config.id, filePath, preset.millis)
            updateRecentlyUsed(db, config, preset)
        }
    }

    private fun handleKeep(context: Context, config: FolderConfig, filePath: String) {
        val db = (context.applicationContext as MultiToolApp).database
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

    private suspend fun updateRecentlyUsed(
        db: com.msahil432.multitool.data.AppDatabase,
        config: FolderConfig,
        used: TimePeriodPreset
    ) {
        // Decode existing recently-used list, move `used` to the front, keep at most 4
        val current = decodeTimePeriodPresets(config.recentlyUsedPeriods).toMutableList()
        current.removeAll { it.millis == used.millis }
        current.add(0, used)
        val trimmed = current.take(4)
        db.appDao().updateFolderConfig(config.copy(recentlyUsedPeriods = encodeTimePeriodPresets(trimmed)))
    }
}

@Composable
fun PromptContent(
    config: FolderConfig,
    filePath: String,
    onAction: (TimePeriodPreset) -> Unit,
    onKeep: () -> Unit,
    onMove: (() -> Unit)? = null
) {
    // Decode from JSON — recentlyUsed takes priority over candidatePeriods
    val recentlyUsed = decodeTimePeriodPresets(config.recentlyUsedPeriods)
    val candidates   = decodeTimePeriodPresets(config.candidateTimePeriods)
    val topPresets   = (if (recentlyUsed.isNotEmpty()) recentlyUsed else candidates).take(4)

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "New File Detected", style = MaterialTheme.typography.titleMedium)
        Text(text = filePath.substringAfterLast("/"), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            topPresets.take(2).forEach { preset ->
                Button(onClick = { onAction(preset) }) { Text("In ${preset.label}") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            topPresets.drop(2).take(2).forEach { preset ->
                Button(onClick = { onAction(preset) }) { Text("In ${preset.label}") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Move Now button — visible when Move Rule is enabled
        if (onMove != null) {
            Button(
                onClick = onMove,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("➡️ Move Now")
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        // Keep button — hidden by default when Move Rule is on; visible if moveShowKeep is true
        val showKeep = onMove == null || config.moveShowKeep
        if (showKeep) {
            OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) {
                Text("Keep")
            }
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
