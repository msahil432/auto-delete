package com.msahil432.multitool.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.GeofenceRepository
import com.msahil432.multitool.location.GeofenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, FileMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            val app = context.applicationContext as? MultiToolApp
            if (app != null) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val geofenceRepo = GeofenceRepository(app.database.geofenceDao())
                        val geofenceManager = GeofenceManager(context)
                        geofenceManager.reRegisterAll(geofenceRepo)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

