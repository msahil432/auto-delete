package com.msahil432.multitool.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeofenceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GeofenceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GeofenceRepository(db.geofenceDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testParseAndFormatGroupIds() {
        val ids = listOf(1L, 2L, 5L)
        val formatted = GeofenceRepository.formatGroupIds(ids)
        assertEquals("1;2;5", formatted)

        val parsed = GeofenceRepository.parseGroupIds("1;2;5")
        assertEquals(listOf(1L, 2L, 5L), parsed)

        val parsedComma = GeofenceRepository.parseGroupIds(" 1 , 2 , 3 ")
        assertEquals(listOf(1L, 2L, 3L), parsedComma)

        val parsedEmpty = GeofenceRepository.parseGroupIds("")
        assertTrue(parsedEmpty.isEmpty())
    }

    @Test
    fun testInsertAndGetProfile() = runBlocking {
        val profile = GeofenceProfile(
            name = "Workplace",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 150f,
            onEnterGroupIds = "1;2",
            onExitGroupIds = "3",
            enabled = true
        )

        val id = repository.upsertProfile(profile)
        assertTrue(id > 0)

        val fetched = repository.getProfileById(id)
        assertNotNull(fetched)
        assertEquals("Workplace", fetched?.name)
        assertEquals(37.7749, fetched?.latitude ?: 0.0, 0.0001)
        assertEquals(-122.4194, fetched?.longitude ?: 0.0, 0.0001)
        assertEquals(150f, fetched?.radiusMeters)
        assertEquals("1;2", fetched?.onEnterGroupIds)
        assertEquals("3", fetched?.onExitGroupIds)
        assertTrue(fetched?.enabled == true)
    }

    @Test
    fun testGetEnabledProfiles() = runBlocking {
        repository.upsertProfile(
            GeofenceProfile(
                name = "Active Zone",
                latitude = 10.0,
                longitude = 20.0,
                radiusMeters = 100f,
                onEnterGroupIds = "1",
                onExitGroupIds = "",
                enabled = true
            )
        )
        repository.upsertProfile(
            GeofenceProfile(
                name = "Disabled Zone",
                latitude = 30.0,
                longitude = 40.0,
                radiusMeters = 200f,
                onEnterGroupIds = "2",
                onExitGroupIds = "",
                enabled = false
            )
        )

        val enabled = repository.getEnabledProfilesSync()
        assertEquals(1, enabled.size)
        assertEquals("Active Zone", enabled[0].name)
    }

    @Test
    fun testToggleEnabledAndDelete() = runBlocking {
        val id = repository.upsertProfile(
            GeofenceProfile(
                name = "Toggle Zone",
                latitude = 12.0,
                longitude = 34.0,
                radiusMeters = 100f,
                onEnterGroupIds = "1",
                onExitGroupIds = "",
                enabled = true
            )
        )

        repository.setProfileEnabled(id, false)
        val disabled = repository.getProfileById(id)
        assertFalse(disabled?.enabled ?: true)

        repository.deleteProfileById(id)
        val deleted = repository.getProfileById(id)
        assertNull(deleted)
    }
}
