package com.msahil432.multitool.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.msahil432.multitool.ui.screens.BlockOverlayContent
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Full-screen blocking fallback Activity when SYSTEM_ALERT_WINDOW (overlay) permission is missing.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val info = BlockOverlayManager.currentInfo ?: parseInfoFromIntent(intent)
        val onFriction = BlockOverlayManager.onFrictionCallback

        setContent {
            MultiToolTheme {
                BlockOverlayContent(
                    info = info,
                    onGoBack = {
                        BlockOverlayManager.onCloseCallback?.invoke()
                        BlockOverlayManager.navigateToHome(this)
                        finish()
                    },
                    onUnlockAnyway = if (info.allowFriction) {
                        {
                            onFriction?.invoke()
                            finish()
                        }
                    } else null
                )
            }
        }
    }

    private fun parseInfoFromIntent(intent: Intent): BlockOverlayManager.BlockInfo {
        return BlockOverlayManager.BlockInfo(
            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "",
            appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: "",
            reason = intent.getStringExtra(EXTRA_REASON) ?: "App is blocked",
            allowFriction = intent.getBooleanExtra(EXTRA_ALLOW_FRICTION, false),
            usedSeconds = if (intent.hasExtra(EXTRA_USED_SECONDS)) intent.getLongExtra(EXTRA_USED_SECONDS, 0L) else null,
            limitSeconds = if (intent.hasExtra(EXTRA_LIMIT_SECONDS)) intent.getLongExtra(EXTRA_LIMIT_SECONDS, 0L) else null,
            endsAtMillis = if (intent.hasExtra(EXTRA_ENDS_AT_MILLIS)) intent.getLongExtra(EXTRA_ENDS_AT_MILLIS, 0L) else null
        )
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_ALLOW_FRICTION = "extra_allow_friction"
        const val EXTRA_USED_SECONDS = "extra_used_seconds"
        const val EXTRA_LIMIT_SECONDS = "extra_limit_seconds"
        const val EXTRA_ENDS_AT_MILLIS = "extra_ends_at_millis"

        fun createIntent(context: Context, info: BlockOverlayManager.BlockInfo): Intent {
            return Intent(context, BlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_PACKAGE_NAME, info.packageName)
                putExtra(EXTRA_APP_LABEL, info.appLabel)
                putExtra(EXTRA_REASON, info.reason)
                putExtra(EXTRA_ALLOW_FRICTION, info.allowFriction)
                info.usedSeconds?.let { putExtra(EXTRA_USED_SECONDS, it) }
                info.limitSeconds?.let { putExtra(EXTRA_LIMIT_SECONDS, it) }
                info.endsAtMillis?.let { putExtra(EXTRA_ENDS_AT_MILLIS, it) }
            }
        }
    }
}
