package com.msahil432.autodelete.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.msahil432.autodelete.data.AppDatabase
import com.msahil432.autodelete.data.ScheduledDeletion
import com.msahil432.autodelete.theme.AutoDeleteTheme
import com.msahil432.autodelete.worker.DeletionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(Dispatchers.IO)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("FILE_PATH") ?: return START_NOT_STICKY
        val folderId = intent.getLongExtra("FOLDER_ID", -1L)
        
        showOverlay(filePath, folderId)
        return START_NOT_STICKY
    }

    private fun showOverlay(filePath: String, folderId: Long) {
        if (composeView != null) return

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                AutoDeleteTheme {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("New file detected:")
                                Text(File(filePath).name)
                                
                                Button(onClick = { schedule(filePath, folderId, 3600000L) }) {
                                    Text("1 hour")
                                }
                                Button(onClick = { schedule(filePath, folderId, 86400000L) }) {
                                    Text("1 day")
                                }
                                Button(onClick = { closeOverlay() }) {
                                    Text("Never delete")
                                }
                            }
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        windowManager.addView(composeView, params)
    }

    private fun schedule(filePath: String, folderId: Long, durationMs: Long) {
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val deletion = ScheduledDeletion(
                filePath = filePath,
                folderId = folderId,
                scheduledTime = System.currentTimeMillis() + durationMs,
                deletionMode = "Permanent"
            )
            val id = db.appDao().insertScheduledDeletion(deletion)

            val workRequest = OneTimeWorkRequestBuilder<DeletionWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(DeletionWorker.KEY_FILE_PATH, filePath)
                        .putLong(DeletionWorker.KEY_DELETION_ID, id)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(applicationContext).enqueue(workRequest)
            
            launch(Dispatchers.Main) {
                closeOverlay()
            }
        }
    }

    private fun closeOverlay() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        composeView?.let {
            windowManager.removeView(it)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
