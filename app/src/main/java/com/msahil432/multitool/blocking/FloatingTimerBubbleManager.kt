package com.msahil432.multitool.blocking

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.msahil432.multitool.ui.screens.FloatingTimerBubbleContent
import com.msahil432.multitool.ui.theme.MultiToolTheme
import io.sentry.Sentry
import kotlin.math.hypot

/**
 * Manages the floating timer bubble overlay window above restricted foreground apps using [WindowManager].
 *
 * Handles dragging gestures, magnetic snapping to screen edges, smooth tap expansion,
 * and dropping onto a bottom dismiss target.
 */
object FloatingTimerBubbleManager {

    private const val TAG = "FloatingBubbleManager"
    private const val EDGE_MARGIN_DP = 12

    private var currentBubbleView: ComposeView? = null
    private var currentDismissView: ComposeView? = null
    private var bubbleLifecycleOwner: BubbleLifecycleOwner? = null
    private var dismissLifecycleOwner: BubbleLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    internal var currentInfo: AppTimerInfo? = null
    private var isExpandedState by mutableStateOf(false)
    private var isDismissHoveredState by mutableStateOf(false)
    private var onDismissCallback: (() -> Unit)? = null

    // WindowManager LayoutParams
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var dismissLayoutParams: WindowManager.LayoutParams? = null

    /**
     * Shows or updates the floating timer bubble on screen for the specified [info].
     *
     * @param context Application or service context.
     * @param info Metadata containing current remaining time and app details.
     * @param onDismiss Invoked when the user drags the bubble into the dismiss target.
     */
    fun showOrUpdate(
        context: Context,
        info: AppTimerInfo,
        onDismiss: () -> Unit
    ) {
        currentInfo = info
        onDismissCallback = onDismiss

        runOnMainThread {
            if (!Settings.canDrawOverlays(context)) {
                Log.d(TAG, "Cannot draw overlays: missing SYSTEM_ALERT_WINDOW permission")
                return@runOnMainThread
            }

            if (currentBubbleView != null && currentBubbleView?.parent != null) {
                // Already attached: Compose reactivity automatically updates UI via currentInfo
                return@runOnMainThread
            }

            createAndShowBubble(context.applicationContext, info)
        }
    }

    /**
     * Hides and tears down the floating timer bubble and dismiss targets.
     */
    fun hide() {
        runOnMainThread {
            currentBubbleView?.let { view ->
                val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (wm != null) {
                    removeBubbleView(wm)
                    removeDismissView(wm)
                }
            }
            currentInfo = null
            onDismissCallback = null
            isExpandedState = false
            isDismissHoveredState = false
        }
    }

    /** Returns true if the floating bubble is currently visible on screen. */
    fun isShowing(): Boolean = currentBubbleView != null && currentBubbleView?.parent != null

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndShowBubble(context: Context, initialInfo: AppTimerInfo) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Tear down any leftover views
        removeBubbleView(windowManager)
        removeDismissView(windowManager)

        // 1. Initialize Dismiss Target View
        val dismissView = ComposeView(context)
        val dismissLife = BubbleLifecycleOwner()
        dismissLife.performRestore(null)
        dismissLife.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        dismissView.setViewTreeLifecycleOwner(dismissLife)
        dismissView.setViewTreeSavedStateRegistryOwner(dismissLife)
        dismissView.setViewTreeOnBackPressedDispatcherOwner(dismissLife)

        dismissView.setContent {
            MultiToolTheme {
                DismissTargetContent(isHovered = isDismissHoveredState)
            }
        }

        val dismissParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (60 * density).toInt()
        }

        dismissLife.handleLifecycleEvent(Lifecycle.Event.ON_START)
        dismissLife.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dismissView.visibility = View.GONE
        try {
            windowManager.addView(dismissView, dismissParams)
            currentDismissView = dismissView
            dismissLayoutParams = dismissParams
            dismissLifecycleOwner = dismissLife
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add dismiss target view", e)
            Sentry.captureException(e)
        }

        // 2. Initialize Floating Bubble View
        val bubbleView = ComposeView(context)
        val bubbleLife = BubbleLifecycleOwner()
        bubbleLife.performRestore(null)
        bubbleLife.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        bubbleView.setViewTreeLifecycleOwner(bubbleLife)
        bubbleView.setViewTreeSavedStateRegistryOwner(bubbleLife)
        bubbleView.setViewTreeOnBackPressedDispatcherOwner(bubbleLife)

        bubbleView.setContent {
            MultiToolTheme {
                val info = currentInfo ?: initialInfo
                FloatingTimerBubbleContent(
                    info = info,
                    isExpanded = isExpandedState,
                    onToggleExpand = {
                        isExpandedState = !isExpandedState
                    }
                )
            }
        }

        val initialY = (screenHeight * 0.25f).toInt()
        val initialX = screenWidth - (80 * density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        // 3. Attach Drag, Snap, and Dismiss Touch Listener
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialWinX = 0
        var initialWinY = 0
        var isDragging = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialWinX = layoutParams.x
                    initialWinY = layoutParams.y
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        currentDismissView?.visibility = View.VISIBLE
                    }

                    if (isDragging) {
                        layoutParams.x = (initialWinX + dx).toInt()
                        layoutParams.y = (initialWinY + dy).toInt()
                        try {
                            windowManager.updateViewLayout(bubbleView, layoutParams)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update bubble layout", e)
                        }

                        // Check hover collision with dismiss zone
                        isDismissHoveredState = isOverDismissZone(
                            bubbleX = layoutParams.x,
                            bubbleY = layoutParams.y,
                            bubbleWidth = bubbleView.width,
                            bubbleHeight = bubbleView.height,
                            screenWidth = displayMetrics.widthPixels,
                            screenHeight = displayMetrics.heightPixels,
                            density = density
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentDismissView?.visibility = View.GONE

                    if (isDragging) {
                        if (isDismissHoveredState) {
                            // Dropped on dismiss target
                            onDismissCallback?.invoke()
                            hide()
                        } else {
                            // Snap to nearest screen edge
                            snapToEdge(
                                windowManager = windowManager,
                                view = bubbleView,
                                params = layoutParams,
                                screenWidth = displayMetrics.widthPixels,
                                density = density
                            )
                        }
                    } else {
                        // Click / Tap detected
                        isExpandedState = !isExpandedState
                    }
                    isDragging = false
                    isDismissHoveredState = false
                    true
                }

                else -> false
            }
        }

        bubbleLife.handleLifecycleEvent(Lifecycle.Event.ON_START)
        bubbleLife.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            windowManager.addView(bubbleView, layoutParams)
            currentBubbleView = bubbleView
            bubbleLayoutParams = layoutParams
            bubbleLifecycleOwner = bubbleLife
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble view", e)
            Sentry.captureException(e)
        }
    }

    private fun isOverDismissZone(
        bubbleX: Int,
        bubbleY: Int,
        bubbleWidth: Int,
        bubbleHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        density: Float
    ): Boolean {
        val bubbleCenterX = bubbleX + bubbleWidth / 2
        val bubbleCenterY = bubbleY + bubbleHeight / 2

        val dismissCenterX = screenWidth / 2
        val dismissCenterY = screenHeight - (60 * density).toInt()
        val dismissRadius = (60 * density).toInt()

        val distance = hypot(
            (bubbleCenterX - dismissCenterX).toDouble(),
            (bubbleCenterY - dismissCenterY).toDouble()
        )
        return distance <= dismissRadius
    }

    private fun snapToEdge(
        windowManager: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        screenWidth: Int,
        density: Float
    ) {
        val marginPx = (EDGE_MARGIN_DP * density).toInt()
        val bubbleWidth = view.width.coerceAtLeast(1)
        val targetX = if (params.x + bubbleWidth / 2 < screenWidth / 2) {
            marginPx
        } else {
            screenWidth - bubbleWidth - marginPx
        }

        val startX = params.x
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                if (view.parent != null) {
                    params.x = animation.animatedValue as Int
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update layout during snap", e)
                    }
                }
            }
        }
        animator.start()
    }

    private fun removeBubbleView(windowManager: WindowManager) {
        bubbleLifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        bubbleLifecycleOwner = null

        currentBubbleView?.let { view ->
            if (view.parent != null) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove bubble view", e)
                    Sentry.captureException(e)
                }
            }
        }
        currentBubbleView = null
        bubbleLayoutParams = null
    }

    private fun removeDismissView(windowManager: WindowManager) {
        dismissLifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        dismissLifecycleOwner = null

        currentDismissView?.let { view ->
            if (view.parent != null) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove dismiss view", e)
                    Sentry.captureException(e)
                }
            }
        }
        currentDismissView = null
        dismissLayoutParams = null
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Composable content rendering the bottom dismiss drop target.
     */
    @Composable
    private fun DismissTargetContent(isHovered: Boolean) {
        val scale by animateFloatAsState(
            targetValue = if (isHovered) 1.25f else 1.0f,
            label = "dismissScale"
        )
        val targetColor = if (isHovered) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        }
        val targetContentColor = if (isHovered) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        val containerColor by animateColorAsState(targetColor, label = "dismissContainerColor")
        val contentColor by animateColorAsState(targetContentColor, label = "dismissContentColor")

        Surface(
            modifier = Modifier
                .size(64.dp)
                .scale(scale),
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Drop to dismiss bubble",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }

    /**
     * Custom [LifecycleOwner], [SavedStateRegistryOwner], and [OnBackPressedDispatcherOwner]
     * supporting Jetpack Compose in floating system window overlays.
     */
    internal class BubbleLifecycleOwner(
        override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()
    ) : LifecycleOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
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
