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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(dataStore)
        val appDatabase = (application as MultiToolApp).database
        val usageRepository = com.msahil432.multitool.data.UsageRepository(appDatabase.usageDao())
        
        // Start foreground service if possible
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

        setContent {
            MultiToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        settingsRepository = settingsRepository,
                        appDao = appDatabase.appDao(),
                        usageRepository = usageRepository
                    )
                }
            }
        }
    }
}
