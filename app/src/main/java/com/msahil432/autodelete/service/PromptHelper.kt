package com.msahil432.autodelete.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.msahil432.autodelete.MainActivity
import com.msahil432.autodelete.data.FolderConfig
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
import com.msahil432.autodelete.AutoDeleteApp
import com.msahil432.autodelete.data.PendingAction
import com.msahil432.autodelete.data.ActionStatus
import com.msahil432.autodelete.data.LogAction
import com.msahil432.autodelete.data.ActivityLogEntry
import com.msahil432.autodelete.data.TimePeriodPreset
import com.msahil432.autodelete.data.decodeTimePeriodPresets
import com.msahil432.autodelete.data.encodeTimePeriodPresets
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
                action = "com.msahil432.autodelete.ACTION_MOVE"
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
                    action = "com.msahil432.autodelete.ACTION_SCHEDULE"
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
                    action = "com.msahil432.autodelete.ACTION_SCHEDULE"
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
                action = "com.msahil432.autodelete.ACTION_KEEP"
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
        val db = (context.applicationContext as AutoDeleteApp).database
        CoroutineScope(Dispatchers.IO).launch {
            val destUriString = config.moveDestinationPath
            val sourceFile = File(filePath)

            if (destUriString.isNullOrBlank() || !sourceFile.exists()) {
                // Destination not configured or source gone — keep and notify
                handleKeep(context, config, filePath)
                fireErrorNotification(
                    context,
                    filePath,
                    if (destUriString.isNullOrBlank()) "Move destination not configured."
                    else "Source file not found: ${sourceFile.name}"
                )
                return@launch
            }

            try {
                // Resolve destination directory via SAF Uri or plain path
                val destDir: File = if (destUriString.startsWith("/")) {
                    File(destUriString)
                } else {
                    // SAF tree URI — convert to a real path heuristically (same approach as FolderPathSection)
                    val uriPath = Uri.parse(destUriString).path ?: destUriString
                    val friendlyPath = uriPath
                        .removePrefix("/tree/primary:")
                        .removePrefix("/tree/")
                        .let { if (!it.startsWith("/")) "/storage/emulated/0/$it" else it }
                    File(friendlyPath)
                }

                if (!destDir.exists()) destDir.mkdirs()

                // Build a unique destination file name (handle conflicts)
                val baseName = sourceFile.nameWithoutExtension
                val ext = sourceFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
                var destFile = File(destDir, sourceFile.name)
                var counter = 1
                while (destFile.exists()) {
                    destFile = File(destDir, "${baseName}_$counter$ext")
                    counter++
                }

                // Copy then delete source
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                sourceFile.delete()

                // Log success
                db.appDao().insertActivityLog(
                    ActivityLogEntry(
                        folderId = config.id,
                        fileName = sourceFile.name,
                        fileUri = filePath,
                        action = LogAction.MOVED,
                        timestamp = System.currentTimeMillis(),
                        destinationPath = destFile.absolutePath
                    )
                )
            } catch (e: Exception) {
                Log.e("PromptHelper", "Move failed for $filePath", e)
                handleKeep(context, config, filePath)
                fireErrorNotification(context, filePath, "Move failed: ${e.localizedMessage}")
            }
        }
    }

    private fun fireErrorNotification(context: Context, filePath: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = android.app.NotificationChannel(
                "move_errors", "Move Errors", NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "move_errors")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Auto Delete — Move Failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "File: ${filePath.substringAfterLast("/")}\n$message\nThe file was kept in place."
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(filePath.hashCode() xor 0x7E770001, notification)
    }

    private fun handleAction(context: Context, config: FolderConfig, filePath: String, preset: TimePeriodPreset) {
        val db = (context.applicationContext as AutoDeleteApp).database
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

    private suspend fun updateRecentlyUsed(
        db: com.msahil432.autodelete.data.AppDatabase,
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
