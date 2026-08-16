package com.msahil432.multitool.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDevicePolicyManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceAdminHelperTest {

    private lateinit var context: Context
    private lateinit var dpm: DevicePolicyManager
    private lateinit var shadowDpm: ShadowDevicePolicyManager
    private lateinit var adminComponent: ComponentName

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        shadowDpm = shadowOf(dpm)
        adminComponent = DeviceAdminHelper.component(context)
    }

    @Test
    fun `component points to MultiToolDeviceAdminReceiver`() {
        assertEquals(
            MultiToolDeviceAdminReceiver::class.java.name,
            adminComponent.className
        )
        assertEquals(context.packageName, adminComponent.packageName)
    }

    @Test
    fun `isActive returns false when admin is not active`() {
        assertFalse(DeviceAdminHelper.isActive(context))
    }

    @Test
    fun `isActive returns true when admin is active`() {
        shadowDpm.setActiveAdmin(adminComponent)
        assertTrue(DeviceAdminHelper.isActive(context))
    }

    @Test
    fun `deactivate removes active admin`() {
        shadowDpm.setActiveAdmin(adminComponent)
        assertTrue(DeviceAdminHelper.isActive(context))

        DeviceAdminHelper.deactivate(context)
        assertFalse(DeviceAdminHelper.isActive(context))
    }

    @Test
    fun `requestActivation fires ACTION_ADD_DEVICE_ADMIN intent with proper extras`() {
        val shadowApp = shadowOf(context as android.app.Application)
        DeviceAdminHelper.requestActivation(context)

        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, startedIntent.action)
        assertEquals(adminComponent, IntentCompat.getParcelableExtra(startedIntent, DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName::class.java))
        assertNotNull(startedIntent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION))
        assertTrue((startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `receiver onDisableRequested returns warning message`() {
        val receiver = MultiToolDeviceAdminReceiver()
        val warning = receiver.onDisableRequested(context, Intent())
        assertNotNull(warning)
        assertTrue(warning.toString().contains("anti-uninstall", ignoreCase = true) || warning.toString().contains("focus", ignoreCase = true))
    }
}
