package com.msahil432.multitool.blocking

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.msahil432.multitool.ui.screens.BlockOverlayContent
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Manages full-screen blocking overlay display over restricted apps using WindowManager
 * (TYPE_APPLICATION_OVERLAY) or falls back to BlockActivity when overlay permission is missing.
 */
object BlockOverlayManager {

    data class BlockInfo(
        val packageName: String,
        val appLabel: String,
        val reason: String,
        val allowFriction: Boolean = false,
        val usedSeconds: Long? = null,
        val limitSeconds: Long? = null,
        val endsAtMillis: Long? = null
    )

    private var currentView: ComposeView? = null
    private var currentLifecycleOwner: BlockLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    internal var currentInfo: BlockInfo? = null
    internal var onCloseCallback: (() -> Unit)? = null
    internal var onFrictionCallback: (() -> Unit)? = null

    /**
     * Shows full-screen blocking overlay above the target application.
     */
    fun show(
        context: Context,
        info: BlockInfo,
        onClose: () -> Unit,
        onFriction: (() -> Unit)? = null
    ) {
        currentInfo = info
        onCloseCallback = onClose
        onFrictionCallback = onFriction

        runOnMainThread {
            if (Settings.canDrawOverlays(context)) {
                showWindowOverlay(context, info, onClose, onFriction)
            } else {
                showFallbackActivity(context, info)
            }
        }
    }

    private fun showWindowOverlay(
        context: Context,
        info: BlockInfo,
        onClose: () -> Unit,
        onFriction: (() -> Unit)?
    ) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Remove any existing overlay view first
        hideOverlayView(windowManager)

        val composeView = ComposeView(context)
        val lifecycleOwner = BlockLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        composeView.setContent {
            MultiToolTheme {
                BlockOverlayContent(
                    info = info,
                    onGoBack = {
                        onClose()
                        navigateToHome(context)
                        hide()
                    },
                    onUnlockAnyway = if (info.allowFriction) {
                        {
                            onFriction?.invoke()
                            hide()
                        }
                    } else null
                )
            }
        }

        // Intercept back key to navigate home & dismiss
        composeView.isFocusable = true
        composeView.isFocusableInTouchMode = true
        composeView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onClose()
                navigateToHome(context)
                hide()
                true
            } else {
                false
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager.addView(composeView, layoutParams)
        currentView = composeView
        currentLifecycleOwner = lifecycleOwner
    }

    private fun showFallbackActivity(context: Context, info: BlockInfo) {
        val intent = BlockActivity.createIntent(context, info)
        context.startActivity(intent)
    }

    /**
     * Hides and removes the overlay view if showing.
     */
    fun hide() {
        runOnMainThread {
            currentView?.let { view ->
                val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (windowManager != null) {
                    hideOverlayView(windowManager)
                }
            }
            currentInfo = null
            onCloseCallback = null
            onFrictionCallback = null
        }
    }

    private fun hideOverlayView(windowManager: WindowManager) {
        currentLifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        currentLifecycleOwner = null

        currentView?.let { view ->
            if (view.parent != null) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Exception) {}
            }
        }
        currentView = null
    }

    /**
     * Returns true if overlay view is currently attached to WindowManager.
     */
    fun isShowing(): Boolean {
        return currentView != null && currentView?.parent != null
    }

    internal fun navigateToHome(context: Context) {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        } catch (_: Exception) {}
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private class BlockLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: android.os.Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}
