package com.msahil432.multitool.blocking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockOverlayTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testBlockInfoDataClass() {
        val info = BlockOverlayManager.BlockInfo(
            packageName = "com.example.app",
            appLabel = "Example App",
            reason = "Daily limit reached",
            allowFriction = true,
            usedSeconds = 3600L,
            limitSeconds = 3600L,
            endsAtMillis = 1700000000000L
        )

        assertEquals("com.example.app", info.packageName)
        assertEquals("Example App", info.appLabel)
        assertEquals("Daily limit reached", info.reason)
        assertTrue(info.allowFriction)
        assertEquals(3600L, info.usedSeconds)
        assertEquals(3600L, info.limitSeconds)
        assertEquals(1700000000000L, info.endsAtMillis)
    }

    @Test
    fun testBlockActivityIntentCreation() {
        val info = BlockOverlayManager.BlockInfo(
            packageName = "com.example.app",
            appLabel = "Example App",
            reason = "Work schedule active",
            allowFriction = false,
            usedSeconds = 1200L,
            limitSeconds = 3600L,
            endsAtMillis = 1800000000000L
        )

        val intent = BlockActivity.createIntent(context, info)
        assertNotNull(intent)
        assertEquals("com.example.app", intent.getStringExtra(BlockActivity.EXTRA_PACKAGE_NAME))
        assertEquals("Example App", intent.getStringExtra(BlockActivity.EXTRA_APP_LABEL))
        assertEquals("Work schedule active", intent.getStringExtra(BlockActivity.EXTRA_REASON))
        assertFalse(intent.getBooleanExtra(BlockActivity.EXTRA_ALLOW_FRICTION, true))
        assertEquals(1200L, intent.getLongExtra(BlockActivity.EXTRA_USED_SECONDS, 0L))
        assertEquals(3600L, intent.getLongExtra(BlockActivity.EXTRA_LIMIT_SECONDS, 0L))
        assertEquals(1800000000000L, intent.getLongExtra(BlockActivity.EXTRA_ENDS_AT_MILLIS, 0L))
    }

    @Test
    fun testBlockOverlayManagerShowAndHide() {
        var closed = false
        var frictionTriggered = false

        val info = BlockOverlayManager.BlockInfo(
            packageName = "com.example.app",
            appLabel = "Example App",
            reason = "Daily limit reached",
            allowFriction = true
        )

        BlockOverlayManager.show(
            context = context,
            info = info,
            onClose = { closed = true },
            onFriction = { frictionTriggered = true }
        )

        assertEquals(info, BlockOverlayManager.currentInfo)
        assertNotNull(BlockOverlayManager.onCloseCallback)
        assertNotNull(BlockOverlayManager.onFrictionCallback)

        BlockOverlayManager.onCloseCallback?.invoke()
        assertTrue(closed)

        BlockOverlayManager.onFrictionCallback?.invoke()
        assertTrue(frictionTriggered)

        BlockOverlayManager.hide()
        // verify after hide
        assertFalse(BlockOverlayManager.isShowing())
    }
}
