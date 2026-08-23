package com.msahil432.multitool.location

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.msahil432.multitool.data.GeofenceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [GeofenceManager] covering geofence creation, pending intent, and permission queries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeofenceManagerTest {

  private lateinit var context: Context
  private lateinit var geofenceManager: GeofenceManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    geofenceManager = GeofenceManager(context)
  }

  @Test
  fun `buildGeofence constructs valid Geofence instance`() {
    val profile = GeofenceProfile(
      id = 42L,
      name = "Home",
      latitude = 37.422,
      longitude = -122.084,
      radiusMeters = 100f,
      onEnterGroupIds = "1",
      onExitGroupIds = "2",
      enabled = true
    )

    val geofence = geofenceManager.buildGeofence(profile)
    assertNotNull(geofence)
    assertEquals("42", geofence.requestId)
  }

  @Test
  fun `getGeofencePendingIntent returns non-null broadcast pending intent`() {
    val pendingIntent = geofenceManager.getGeofencePendingIntent()
    assertNotNull(pendingIntent)
  }

  @Test
  fun `isLocationEnabled executes without error`() {
    // Verifies helper function executes safely
    val enabled = GeofenceManager.isLocationEnabled(context)
    assertNotNull(enabled)
  }
}
