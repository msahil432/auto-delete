package com.msahil432.multitool.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Finds the nearest [Activity] from the given [Context].
 */
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * Composable effect that adds [WindowManager.LayoutParams.FLAG_SECURE] to the current window
 * while the composable is active in the composition, and removes it on disposal.
 * This blocks screenshots, screen recording, and system recents previews for sensitive screens.
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
