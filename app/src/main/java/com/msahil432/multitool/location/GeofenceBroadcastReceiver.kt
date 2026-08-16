package com.msahil432.multitool.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.data.GeofenceRepository
import com.msahil432.multitool.data.TimelineEventType
import com.msahil432.multitool.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        val app = context.applicationContext as? MultiToolApp ?: return
        val database = app.database
        val geofenceRepo = GeofenceRepository(database.geofenceDao())
        val blockingRepo = BlockingRepository(database.blockingDao())
        val usageRepo = UsageRepository(database.usageDao())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleGeofenceTransition(
                    transitionType = transitionType,
                    triggeringGeofenceIds = triggeringGeofences.map { it.requestId },
                    geofenceRepository = geofenceRepo,
                    blockingRepository = blockingRepo,
                    usageRepository = usageRepo
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        suspend fun handleGeofenceTransition(
            transitionType: Int,
            triggeringGeofenceIds: List<String>,
            geofenceRepository: GeofenceRepository,
            blockingRepository: BlockingRepository,
            usageRepository: UsageRepository
        ) {
            for (requestId in triggeringGeofenceIds) {
                val profileId = requestId.toLongOrNull() ?: continue
                val profile = geofenceRepository.getProfileById(profileId) ?: continue
                if (!profile.enabled) continue

                val enterGroupIds = GeofenceRepository.parseGroupIds(profile.onEnterGroupIds)
                val exitGroupIds = GeofenceRepository.parseGroupIds(profile.onExitGroupIds)

                when (transitionType) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        if (enterGroupIds.isNotEmpty()) {
                            blockingRepository.setGroupsEnabled(enterGroupIds, true)
                        }
                        if (exitGroupIds.isNotEmpty()) {
                            blockingRepository.setGroupsEnabled(exitGroupIds, false)
                        }
                        usageRepository.recordTimeline(
                            pkg = profile.name,
                            type = TimelineEventType.GEOFENCE_ENTER
                        )
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        if (exitGroupIds.isNotEmpty()) {
                            blockingRepository.setGroupsEnabled(exitGroupIds, true)
                        }
                        if (enterGroupIds.isNotEmpty()) {
                            blockingRepository.setGroupsEnabled(enterGroupIds, false)
                        }
                        usageRepository.recordTimeline(
                            pkg = profile.name,
                            type = TimelineEventType.GEOFENCE_EXIT
                        )
                    }
                }
            }
        }
    }
}
