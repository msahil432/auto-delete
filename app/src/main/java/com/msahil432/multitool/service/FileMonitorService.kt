package com.msahil432.multitool.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.FolderConfig
import com.msahil432.multitool.data.FilterRule
import com.msahil432.multitool.data.decodeFilterRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class FileMonitorService : Service() {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observers = mutableListOf<RecursiveFileObserver>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        coroutineScope.launch {
            val db = (application as MultiToolApp).database
            db.appDao().getEnabledFolderConfigs().collectLatest { configs ->
                Log.d("FileMonitorService", "Configs updated, restarting observers")
                restartObservers(configs)
            }
        }
    }

    private fun restartObservers(configs: List<FolderConfig>) {
        observers.forEach { it.stopWatching() }
        observers.clear()

        configs.forEach { config ->
            val observer = RecursiveFileObserver(config.path, config) { fileEvent ->
                handleNewFile(config, fileEvent)
            }
            observer.startWatching()
            observers.add(observer)
        }
    }

    private fun handleNewFile(config: FolderConfig, filePath: String) {
        Log.d("FileMonitorService", "New file detected: $filePath in ${config.path}")
        coroutineScope.launch(Dispatchers.Main) {
            PromptHelper.showPrompt(this@FileMonitorService, config, filePath)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        coroutineScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "file_monitor_channel",
            "File Monitor Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors folders for new files"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "file_monitor_channel")
            .setContentTitle("Multi Tool")
            .setContentText("Monitoring folders for new files")
            .setSmallIcon(android.R.drawable.ic_menu_delete) // Update to real icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

class RecursiveFileObserver(
    private val rootPath: String,
    private val config: FolderConfig,
    private val onFileCreated: (String) -> Unit
) : FileObserver(rootPath, CREATE or MOVED_TO) {

    // Decode filter rules once at construction time for efficiency
    private val excludeRules: List<FilterRule> = decodeFilterRules(config.fileTypeExcludeList)
    private val includeRules: List<FilterRule> = decodeFilterRules(config.fileTypeIncludeList)

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        val fullPath = "$rootPath/$path"

        if (event and CREATE != 0 || event and MOVED_TO != 0) {
            val fileName = path.substringAfterLast('/')

            // 1. Inclusion list check — if non-empty, file MUST match at least one rule
            if (includeRules.isNotEmpty() && includeRules.none { it.matches(fileName) }) {
                Log.d("FileMonitorService", "Skipping (not in include list): $fileName")
                return
            }

            // 2. Exclusion list check — if file matches any exclusion rule, skip it
            if (excludeRules.any { it.matches(fileName) }) {
                Log.d("FileMonitorService", "Skipping (excluded): $fileName")
                return
            }

            onFileCreated(fullPath)
        }
    }
}
