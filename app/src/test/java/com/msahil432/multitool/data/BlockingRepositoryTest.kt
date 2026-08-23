package com.msahil432.multitool.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.time.LocalDate

/**
 * Unit tests for [BlockingRepository] covering block groups, rules, counters, and interception logs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockingRepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var repo: BlockingRepository
  private var fakeTime = 1_000_000L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = BlockingRepository(db.blockingDao(), clock = { fakeTime })
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `upsertGroup, groupById and groups flow work correctly`() = runTest {
    val group = BlockGroup(
      name = "Social",
      packageNames = "com.instagram.android;com.facebook.katana",
      enabled = true,
      createdAt = 1000L
    )
    val id = repo.upsertGroup(group)
    assertTrue(id > 0)

    val groups = repo.groups().first()
    assertEquals(1, groups.size)
    assertEquals("Social", groups[0].name)
    assertEquals(id, groups[0].id)

    val fetchedFlow = repo.groupById(id).first()
    assertNotNull(fetchedFlow)
    assertEquals("Social", fetchedFlow?.name)

    val fetchedSync = repo.getGroupById(id)
    assertNotNull(fetchedSync)
    assertEquals("Social", fetchedSync?.name)
  }

  @Test
  fun `setGroupsEnabled updates group enabled state`() = runTest {
    val g1 = BlockGroup(name = "Group 1", packageNames = "com.app.one", enabled = true, createdAt = 1000L)
    val g2 = BlockGroup(name = "Group 2", packageNames = "com.app.two", enabled = true, createdAt = 1000L)
    val id1 = repo.upsertGroup(g1)
    val id2 = repo.upsertGroup(g2)

    repo.setGroupsEnabled(listOf(id1, id2), false)

    val updated1 = repo.getGroupById(id1)
    val updated2 = repo.getGroupById(id2)
    assertFalse(updated1?.enabled ?: true)
    assertFalse(updated2?.enabled ?: true)
  }

  @Test
  fun `deleteGroup removes group and its associated rules and counters`() = runTest {
    val group = BlockGroup(name = "Distractions", packageNames = "com.games.fun", enabled = true, createdAt = 1000L)
    val groupId = repo.upsertGroup(group)

    val rule = BlockRule(groupId = groupId, type = BlockRuleType.DAILY_QUOTA, dailyQuotaMinutes = 30)
    repo.upsertRule(rule)

    val counter = BlockCounter(dateEpochDay = 100L, groupId = groupId, usedForegroundMillis = 5000L)
    repo.upsertCounter(counter)

    val persistedGroup = repo.getGroupById(groupId)!!
    repo.deleteGroup(persistedGroup)

    assertNull(repo.getGroupById(groupId))
    assertTrue(repo.getRulesForGroupSync(groupId).isEmpty())
    assertNull(repo.getCounterSync(100L, groupId))
  }

  @Test
  fun `rule CRUD operations work as expected`() = runTest {
    val group = BlockGroup(name = "Test", packageNames = "com.test", enabled = true, createdAt = 1000L)
    val groupId = repo.upsertGroup(group)

    val rule1 = BlockRule(groupId = groupId, type = BlockRuleType.LAUNCH_LIMIT, maxLaunchesPerDay = 5)
    val rule2 = BlockRule(groupId = groupId, type = BlockRuleType.SCHEDULE, startMinuteOfDay = 540, endMinuteOfDay = 1020)
    val r1Id = repo.upsertRule(rule1)
    val r2Id = repo.upsertRule(rule2)

    val rules = repo.rulesFor(groupId).first()
    assertEquals(2, rules.size)

    val allRules = repo.allRules().first()
    assertEquals(2, allRules.size)

    val syncRules = repo.getRulesForGroupSync(groupId)
    assertEquals(2, syncRules.size)

    // Delete single rule
    repo.deleteRule(syncRules.first { it.id == r1Id })
    val remaining = repo.getRulesForGroupSync(groupId)
    assertEquals(1, remaining.size)
    assertEquals(r2Id, remaining[0].id)

    // Delete remaining rules for group
    repo.deleteRulesForGroup(groupId)
    assertTrue(repo.getRulesForGroupSync(groupId).isEmpty())
  }

  @Test
  fun `counter and counterForToday create or fetch record correctly`() = runTest {
    val group = BlockGroup(name = "Focus", packageNames = "com.focus", enabled = true, createdAt = 1000L)
    val groupId = repo.upsertGroup(group)

    val todayEpoch = LocalDate.now().toEpochDay()
    val initialCounter = repo.counterForToday(groupId, todayEpoch)
    assertEquals(todayEpoch, initialCounter.dateEpochDay)
    assertEquals(groupId, initialCounter.groupId)
    assertEquals(0L, initialCounter.usedForegroundMillis)

    // Update counter
    repo.upsertCounter(initialCounter.copy(usedForegroundMillis = 15000L, launchesUsed = 3))

    val fetchedFlow = repo.counter(todayEpoch, groupId).first()
    assertNotNull(fetchedFlow)
    assertEquals(15000L, fetchedFlow?.usedForegroundMillis)
    assertEquals(3, fetchedFlow?.launchesUsed)

    val fetchedToday = repo.counterForToday(groupId, todayEpoch)
    assertEquals(15000L, fetchedToday.usedForegroundMillis)
    assertEquals(3, fetchedToday.launchesUsed)
  }

  @Test
  fun `enabledGroupsContaining matches package in semicolon delimited list`() = runTest {
    val g1 = BlockGroup(name = "G1", packageNames = "com.pkg.a; com.pkg.b", enabled = true, createdAt = 1000L)
    val g2 = BlockGroup(name = "G2", packageNames = "com.pkg.b; com.pkg.c", enabled = false, createdAt = 1000L)
    val g3 = BlockGroup(name = "G3", packageNames = "com.pkg.c; com.pkg.d", enabled = true, createdAt = 1000L)
    repo.upsertGroup(g1)
    repo.upsertGroup(g2)
    repo.upsertGroup(g3)

    val matchesA = repo.enabledGroupsContaining("com.pkg.a")
    assertEquals(1, matchesA.size)
    assertEquals("G1", matchesA[0].name)

    val matchesB = repo.enabledGroupsContaining("com.pkg.b")
    assertEquals(1, matchesB.size) // g2 is disabled so only g1 is returned
    assertEquals("G1", matchesB[0].name)

    val matchesC = repo.enabledGroupsContaining("com.pkg.c")
    assertEquals(1, matchesC.size) // g2 is disabled, g3 enabled
    assertEquals("G3", matchesC[0].name)
  }

  @Test
  fun `enabledRules returns only active rules for the group`() = runTest {
    val group = BlockGroup(name = "Rules", packageNames = "com.rules", enabled = true, createdAt = 1000L)
    val groupId = repo.upsertGroup(group)

    repo.upsertRule(BlockRule(groupId = groupId, type = BlockRuleType.SCHEDULE, enabled = true))
    repo.upsertRule(BlockRule(groupId = groupId, type = BlockRuleType.DAILY_QUOTA, enabled = false))

    val enabled = repo.enabledRules(groupId)
    assertEquals(1, enabled.size)
    assertEquals(BlockRuleType.SCHEDULE, enabled[0].type)
  }

  @Test
  fun `logInterception and recordInterception store interception with clock`() = runTest {
    fakeTime = 5_555_000L
    val id1 = repo.logInterception("com.bad.app", 42L, BlockRuleType.DAILY_QUOTA)
    assertTrue(id1 > 0)

    val interception2 = BlockInterception(
      timestamp = 6_000_000L,
      packageName = "com.bad.app2",
      ruleId = 43L,
      ruleType = BlockRuleType.SCHEDULE
    )
    val id2 = repo.recordInterception(interception2)
    assertTrue(id2 > 0)

    val events = repo.interceptionsSince(5_000_000L).first()
    assertEquals(2, events.size)

    val recentOnly = repo.interceptionsSince(5_900_000L).first()
    assertEquals(1, recentOnly.size)
    assertEquals("com.bad.app2", recentOnly[0].packageName)
  }
}
