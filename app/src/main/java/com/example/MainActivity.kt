package com.example

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
import com.example.data.SettingsRepository
import com.example.service.FileMonitorService
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(dataStore)
        val appDatabase = (application as AutoDeleteApp).database
        
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
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        settingsRepository = settingsRepository,
                        appDao = appDatabase.appDao()
                    )
                }
            }
        }
    }
}
