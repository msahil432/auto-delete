package com.msahil432.multitool.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.msahil432.multitool.data.UnlockType
import com.msahil432.multitool.util.Breadcrumbs

/**
 * [BroadcastReceiver] listening for [Intent.ACTION_SCREEN_ON] and [Intent.ACTION_USER_PRESENT] system broadcasts.
 */
class ScreenUnlockReceiver(private val onEvent: (UnlockType) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Breadcrumbs.record(
            category = Breadcrumbs.CAT_USAGE,
            message = "Screen state changed",
            data = mapOf("action" to action)
        )
        when (action) {
            Intent.ACTION_SCREEN_ON -> onEvent(UnlockType.SCREEN_ON)
            Intent.ACTION_USER_PRESENT -> onEvent(UnlockType.USER_PRESENT)
        }
    }
}
