package com.msahil432.multitool

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.service.FileMonitorService
import com.msahil432.multitool.ui.theme.MultiToolTheme
import com.msahil432.multitool.ui.navigation.AppNavigation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import io.sentry.Sentry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // waiting for view to draw to better represent a captured error with a screenshot
        findViewById<android.view.View>(android.R.id.content).viewTreeObserver.addOnGlobalLayoutListener {
            try {
                throw Exception("This app uses Sentry! :)")
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(dataStore)
        val appDatabase = (application as MultiToolApp).database
        val usageRepository = com.msahil432.multitool.data.UsageRepository(appDatabase.usageDao())
        val blockingRepository = com.msahil432.multitool.data.BlockingRepository(appDatabase.blockingDao())
        val browsingRepository = com.msahil432.multitool.data.BrowsingRepository(appDatabase.browsingDao())
        val notificationRepository = com.msahil432.multitool.data.NotificationRepository(appDatabase.notificationDao())
        val geofenceRepository = com.msahil432.multitool.data.GeofenceRepository(appDatabase.geofenceDao())
        
        // Start foreground service only if the File Cleanup module is active
        val isFileCleanupActive = runBlocking {
            settingsRepository.moduleFileCleanup.first()
        }
        if (isFileCleanupActive) {
            val serviceIntent = Intent(this, FileMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    // Ignore if not allowed in background
                }
            } else {
                startService(serviceIntent)
            }
        }

        setContent {
            MultiToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        settingsRepository = settingsRepository,
                        appDao = appDatabase.appDao(),
                        usageRepository = usageRepository,
                        blockingRepository = blockingRepository,
                        browsingRepository = browsingRepository,
                        notificationRepository = notificationRepository,
                        geofenceRepository = geofenceRepository
                    )
                }
            }
        }
    }
}

