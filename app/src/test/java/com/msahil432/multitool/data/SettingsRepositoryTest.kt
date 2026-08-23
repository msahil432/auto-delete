package com.msahil432.multitool.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SettingsRepository] testing all DataStore preference getters and setters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  private lateinit var repo: SettingsRepository
  private lateinit var dataStoreFile: java.io.File

  @Before
  fun setUp() {
    dataStoreFile = tempFolder.newFile("test_settings.preferences_pb")
    val dataStore = PreferenceDataStoreFactory.create(
      scope = testScope,
      produceFile = { dataStoreFile }
    )
    repo = SettingsRepository(dataStore)
  }

  @Test
  fun `tamperAlarmEnabled defaults to false and updates correctly`() = runTest {
    assertFalse(repo.tamperAlarmEnabled.first())
    repo.setTamperAlarmEnabled(true)
    assertTrue(repo.tamperAlarmEnabled.first())
  }

  @Test
  fun `strictModeState defaults and custom state persistence works`() = runTest {
    val defaultState = repo.strictModeState.first()
    assertFalse(defaultState.isActive)
    assertEquals(0L, defaultState.startedAt)
    assertEquals(0L, defaultState.endAt)
    assertEquals(UnlockMethod.TEXT, defaultState.unlockMethod)
    assertEquals(0L, defaultState.pendingDeactivationAt)

    val newState = StrictModeState(
      isActive = true,
      startedAt = 1000L,
      endAt = 5000L,
      unlockMethod = UnlockMethod.PIN,
      pendingDeactivationAt = 6000L
    )
    repo.setStrictModeState(newState)

    val updatedState = repo.strictModeState.first()
    assertEquals(newState, updatedState)
  }

  @Test
  fun `strictModeState falls back to TEXT on unknown unlock method`() = runTest {
    val fallbackFile = tempFolder.newFile("fallback_settings.preferences_pb")
    val fallbackDataStore = PreferenceDataStoreFactory.create(
      produceFile = { fallbackFile }
    )
    fallbackDataStore.edit { preferences ->
      preferences[SettingsRepository.STRICT_UNLOCK_METHOD] = "UNKNOWN_NON_EXISTENT_METHOD"
    }

    val fallbackRepo = SettingsRepository(fallbackDataStore)
    val state = fallbackRepo.strictModeState.first()
    assertEquals(UnlockMethod.TEXT, state.unlockMethod)
  }

  @Test
  fun `strictModeActive and strictPendingDeactivationAt work correctly`() = runTest {
    assertFalse(repo.strictModeActive.first())
    repo.setStrictModeActive(true)
    assertTrue(repo.strictModeActive.first())

    repo.setStrictPendingDeactivationAt(9999L)
    val state = repo.strictModeState.first()
    assertEquals(9999L, state.pendingDeactivationAt)
  }

  @Test
  fun `masterPasswordHash and qrExpectedValue work correctly`() = runTest {
    assertNull(repo.masterPasswordHash.first())
    repo.setMasterPasswordHash("hashed_secret_123")
    assertEquals("hashed_secret_123", repo.masterPasswordHash.first())

    assertNull(repo.qrExpectedValue.first())
    repo.setQrExpectedValue("QR_TOKEN_ABC")
    assertEquals("QR_TOKEN_ABC", repo.qrExpectedValue.first())
  }

  @Test
  fun `cooldownMinutes and textChallengeLength work correctly with defaults`() = runTest {
    assertEquals(15, repo.cooldownMinutes.first())
    repo.setCooldownMinutes(30)
    assertEquals(30, repo.cooldownMinutes.first())

    assertEquals(100, repo.textChallengeLength.first())
    repo.setTextChallengeLength(200)
    assertEquals(200, repo.textChallengeLength.first())
  }

  @Test
  fun `notification preferences work correctly`() = runTest {
    assertFalse(repo.notificationVaultEnabled.first())
    repo.setNotificationVaultEnabled(true)
    assertTrue(repo.notificationVaultEnabled.first())

    assertTrue(repo.notificationBlockedPackages.first().isEmpty())
    val pkgs = setOf("com.whatsapp", "com.instagram.android")
    repo.setNotificationBlockedPackages(pkgs)
    assertEquals(pkgs, repo.notificationBlockedPackages.first())
  }

  @Test
  fun `short-form content blocking flags work correctly`() = runTest {
    assertFalse(repo.blockYtShorts.first())
    repo.setBlockYtShorts(true)
    assertTrue(repo.blockYtShorts.first())

    assertFalse(repo.blockIgReels.first())
    repo.setBlockIgReels(true)
    assertTrue(repo.blockIgReels.first())

    assertFalse(repo.blockFbReels.first())
    repo.setBlockFbReels(true)
    assertTrue(repo.blockFbReels.first())
  }

  @Test
  fun `browsing tracking and usage timestamp work correctly`() = runTest {
    assertFalse(repo.trackBrowserUrls.first())
    repo.setTrackBrowserUrls(true)
    assertTrue(repo.trackBrowserUrls.first())

    assertEquals(0L, repo.usageLastProcessedTs.first())
    repo.setUsageLastProcessedTs(123456789L)
    assertEquals(123456789L, repo.usageLastProcessedTs.first())
  }

  @Test
  fun `onboarding and global file settings work correctly with defaults`() = runTest {
    assertFalse(repo.onboardingComplete.first())
    repo.setOnboardingComplete(true)
    assertTrue(repo.onboardingComplete.first())

    assertEquals("30s,1h,1w,1mo,never", repo.globalDefaultPool.first())
    repo.setGlobalDefaultPool("1h,1d,never")
    assertEquals("1h,1d,never", repo.globalDefaultPool.first())

    assertEquals(DeletionMode.TRASH.name, repo.globalDeletionMode.first())
    repo.setGlobalDeletionMode(DeletionMode.DELETE.name)
    assertEquals(DeletionMode.DELETE.name, repo.globalDeletionMode.first())
  }

  @Test
  fun `module activation flags work correctly`() = runTest {
    assertFalse(repo.moduleFileCleanup.first())
    repo.setModuleFileCleanup(true)
    assertTrue(repo.moduleFileCleanup.first())

    assertFalse(repo.moduleUsageStats.first())
    repo.setModuleUsageStats(true)
    assertTrue(repo.moduleUsageStats.first())

    assertFalse(repo.moduleAppFocus.first())
    repo.setModuleAppFocus(true)
    assertTrue(repo.moduleAppFocus.first())
  }
}
