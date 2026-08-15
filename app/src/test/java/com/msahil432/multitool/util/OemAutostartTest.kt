package com.msahil432.multitool.util

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OemAutostartTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDetectOemReturnsValidBrand() {
        val brand = OemAutostart.detectOem()
        assertNotNull(brand)
        assertNotNull(brand.displayName)
    }

    @Test
    fun testGetInstructionsForBrands() {
        for (brand in OemAutostart.OemBrand.entries) {
            val instructions = OemAutostart.getInstructions(brand)
            assertNotNull(instructions)
            assertTrue(instructions.isNotEmpty())
        }
    }

    @Test
    fun testOpenFallsBackToAppDetailsWhenOemIntentsCannotResolve() {
        OemAutostart.open(context)

        val shadowApp = shadowOf(context as android.app.Application)
        val nextIntent = shadowApp.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, nextIntent.action)
        assertEquals("package:${context.packageName}", nextIntent.dataString)
    }

    @Test
    fun testOpenAppDetails() {
        OemAutostart.openAppDetails(context)

        val shadowApp = shadowOf(context as android.app.Application)
        val nextIntent = shadowApp.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, nextIntent.action)
        assertEquals("package:${context.packageName}", nextIntent.dataString)
    }
}
