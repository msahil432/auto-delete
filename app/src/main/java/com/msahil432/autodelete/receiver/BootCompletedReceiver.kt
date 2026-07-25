package com.msahil432.autodelete.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.msahil432.autodelete.monitor.FileMonitorService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, FileMonitorService::class.java)
            context.startService(serviceIntent)
        }
    }
}
