package com.msahil432.multitool.location

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.Geofence
import com.msahil432.multitool.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeofenceBroadcastReceiverTest {

    private lateinit var db: AppDatabase
    private lateinit var geofenceRepo: GeofenceRepository
    private lateinit var blockingRepo: BlockingRepository
    private lateinit var usageRepo: UsageRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        geofenceRepo = GeofenceRepository(db.geofenceDao())
        blockingRepo = BlockingRepository(db.blockingDao())
        usageRepo = UsageRepository(db.usageDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testHandleGeofenceTransitionEnter() = runBlocking {
        val group1Id = blockingRepo.upsertGroup(
            BlockGroup(name = "Social", packageNames = "com.ig", enabled = false, createdAt = System.currentTimeMillis())
        )
        val group2Id = blockingRepo.upsertGroup(
            BlockGroup(name = "Gaming", packageNames = "com.game", enabled = false, createdAt = System.currentTimeMillis())
        )
        val group3Id = blockingRepo.upsertGroup(
            BlockGroup(name = "Leisure", packageNames = "com.video", enabled = true, createdAt = System.currentTimeMillis())
        )

        val profileId = geofenceRepo.upsertProfile(
            GeofenceProfile(
                name = "Office",
                latitude = 37.7749,
                longitude = -122.4194,
                radiusMeters = 150f,
                onEnterGroupIds = "$group1Id;$group2Id",
                onExitGroupIds = "$group3Id",
                enabled = true
            )
        )

        GeofenceBroadcastReceiver.handleGeofenceTransition(
            transitionType = Geofence.GEOFENCE_TRANSITION_ENTER,
            triggeringGeofenceIds = listOf(profileId.toString()),
            geofenceRepository = geofenceRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo
        )

        val g1 = blockingRepo.getGroupById(group1Id)
        val g2 = blockingRepo.getGroupById(group2Id)
        val g3 = blockingRepo.getGroupById(group3Id)

        assertTrue(g1?.enabled == true)
        assertTrue(g2?.enabled == true)
        assertFalse(g3?.enabled == true)

        val timeline = usageRepo.timelineToday().first()
        assertEquals(1, timeline.size)
        assertEquals(TimelineEventType.GEOFENCE_ENTER, timeline[0].eventType)
        assertEquals("Office", timeline[0].packageName)
    }

    @Test
    fun testHandleGeofenceTransitionExit() = runBlocking {
        val group1Id = blockingRepo.upsertGroup(
            BlockGroup(name = "Social", packageNames = "com.ig", enabled = true, createdAt = System.currentTimeMillis())
        )
        val group2Id = blockingRepo.upsertGroup(
            BlockGroup(name = "Home Free", packageNames = "com.relax", enabled = false, createdAt = System.currentTimeMillis())
        )

        val profileId = geofenceRepo.upsertProfile(
            GeofenceProfile(
                name = "Office",
                latitude = 37.7749,
                longitude = -122.4194,
                radiusMeters = 150f,
                onEnterGroupIds = "$group1Id",
                onExitGroupIds = "$group2Id",
                enabled = true
            )
        )

        GeofenceBroadcastReceiver.handleGeofenceTransition(
            transitionType = Geofence.GEOFENCE_TRANSITION_EXIT,
            triggeringGeofenceIds = listOf(profileId.toString()),
            geofenceRepository = geofenceRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo
        )

        val g1 = blockingRepo.getGroupById(group1Id)
        val g2 = blockingRepo.getGroupById(group2Id)

        assertFalse(g1?.enabled == true)
        assertTrue(g2?.enabled == true)

        val timeline = usageRepo.timelineToday().first()
        assertEquals(1, timeline.size)
        assertEquals(TimelineEventType.GEOFENCE_EXIT, timeline[0].eventType)
        assertEquals("Office", timeline[0].packageName)
    }

    @Test
    fun testDisabledProfileIgnoresTransition() = runBlocking {
        val groupId = blockingRepo.upsertGroup(
            BlockGroup(name = "Social", packageNames = "com.ig", enabled = false, createdAt = System.currentTimeMillis())
        )

        val profileId = geofenceRepo.upsertProfile(
            GeofenceProfile(
                name = "Office",
                latitude = 37.7749,
                longitude = -122.4194,
                radiusMeters = 150f,
                onEnterGroupIds = "$groupId",
                onExitGroupIds = "",
                enabled = false
            )
        )

        GeofenceBroadcastReceiver.handleGeofenceTransition(
            transitionType = Geofence.GEOFENCE_TRANSITION_ENTER,
            triggeringGeofenceIds = listOf(profileId.toString()),
            geofenceRepository = geofenceRepo,
            blockingRepository = blockingRepo,
            usageRepository = usageRepo
        )

        val g = blockingRepo.getGroupById(groupId)
        assertFalse(g?.enabled == true)
    }
}
