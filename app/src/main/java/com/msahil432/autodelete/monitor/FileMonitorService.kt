package com.msahil432.autodelete.monitor

import android.app.Service
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import com.msahil432.autodelete.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class FileMonitorService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val observers = mutableListOf<FileObserver>()
    private var isMonitoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            isMonitoring = true
            serviceScope.launch {
                val dao = AppDatabase.getDatabase(applicationContext).appDao()
                dao.getAllFolders().collect { folders ->
                    observers.forEach { it.stopWatching() }
                    observers.clear()
                    
                    folders.forEach { folder ->
                        val path = folder.path
                        if (File(path).exists()) {
                            @Suppress("DEPRECATION")
                            val observer = object : FileObserver(path, CREATE) {
                                override fun onEvent(event: Int, file: String?) {
                                    if (file != null) {
                                        val fullPath = "$path/$file"
                                        Log.d("FileMonitorService", "New file detected: $fullPath")
                                        triggerOverlayForFile(fullPath, folder.id)
                                    }
                                }
                            }
                            observer.startWatching()
                            observers.add(observer)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }
    
    private fun triggerOverlayForFile(filePath: String, folderId: Long) {
        val intent = Intent(applicationContext, Class.forName("com.msahil432.autodelete.overlay.OverlayService")).apply {
            putExtra("FILE_PATH", filePath)
            putExtra("FOLDER_ID", folderId)
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        serviceJob.cancel()
    }
}
