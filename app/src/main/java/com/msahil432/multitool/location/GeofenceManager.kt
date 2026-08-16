package com.msahil432.multitool.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.msahil432.multitool.data.GeofenceProfile
import com.msahil432.multitool.data.GeofenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeofenceManager(
    private val context: Context,
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
) {

    fun hasForegroundLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasForegroundLocationPermission()
        }
    }

    fun hasAllLocationPermissions(): Boolean {
        return hasForegroundLocationPermission() && hasBackgroundLocationPermission()
    }

    fun buildGeofence(profile: GeofenceProfile): Geofence {
        return Geofence.Builder()
            .setRequestId(profile.id.toString())
            .setCircularRegion(profile.latitude, profile.longitude, profile.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
    }

    private fun buildGeofencingRequest(geofences: List<Geofence>): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofences(geofences)
            .build()
    }

    fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun registerGeofence(profile: GeofenceProfile, onComplete: ((Boolean) -> Unit)? = null) {
        if (!profile.enabled) {
            unregisterGeofence(profile.id, onComplete)
            return
        }
        if (!hasAllLocationPermissions()) {
            onComplete?.invoke(false)
            return
        }

        try {
            val geofence = buildGeofence(profile)
            val request = buildGeofencingRequest(listOf(geofence))
            geofencingClient.addGeofences(request, getGeofencePendingIntent())
                .addOnSuccessListener { onComplete?.invoke(true) }
                .addOnFailureListener { onComplete?.invoke(false) }
        } catch (e: SecurityException) {
            onComplete?.invoke(false)
        }
    }

    fun unregisterGeofence(profileId: Long, onComplete: ((Boolean) -> Unit)? = null) {
        geofencingClient.removeGeofences(listOf(profileId.toString()))
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    @SuppressLint("MissingPermission")
    suspend fun reRegisterAll(repository: GeofenceRepository) = withContext(Dispatchers.IO) {
        if (!hasAllLocationPermissions()) return@withContext

        try {
            val enabledProfiles = repository.getEnabledProfilesSync()
            if (enabledProfiles.isEmpty()) {
                geofencingClient.removeGeofences(getGeofencePendingIntent())
                return@withContext
            }

            val geofences = enabledProfiles.map { buildGeofence(it) }
            val request = buildGeofencingRequest(geofences)
            geofencingClient.addGeofences(request, getGeofencePendingIntent())
        } catch (e: SecurityException) {
            // Permission revoked concurrently
        } catch (e: Exception) {
            // Ignore failure on best effort
        }
    }

    companion object {
        fun isLocationEnabled(context: Context): Boolean {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            }
        }
    }
}
