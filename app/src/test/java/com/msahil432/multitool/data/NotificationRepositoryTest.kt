package com.msahil432.multitool.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [NotificationRepository] covering vaulting, undelivered queries, delivery status updates, and deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationRepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var repo: NotificationRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = NotificationRepository(db.notificationDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `vault inserts notification and undelivered flow reacts`() = runTest {
    val notification = VaultedNotification(
      packageName = "com.whatsapp",
      title = "Alice",
      text = "Hey there!",
      postedAt = 1000L,
      delivered = false
    )
    val id = repo.vault(notification)
    assertTrue(id > 0)

    val count = repo.undeliveredCount.first()
    assertEquals(1, count)

    val undelivered = repo.undelivered.first()
    assertEquals(1, undelivered.size)
    assertEquals("Alice", undelivered[0].title)

    val syncList = repo.getUndeliveredSync()
    assertEquals(1, syncList.size)
    assertEquals("Alice", syncList[0].title)
  }

  @Test
  fun `allVaulted returns all notifications newest first`() = runTest {
    repo.vault(VaultedNotification(packageName = "com.app.a", title = "A", text = "1", postedAt = 1000L))
    repo.vault(VaultedNotification(packageName = "com.app.b", title = "B", text = "2", postedAt = 2000L))

    val all = repo.allVaulted.first()
    assertEquals(2, all.size)
    assertEquals("B", all[0].title)
    assertEquals("A", all[1].title)
  }

  @Test
  fun `markAllDelivered marks all undelivered items as delivered`() = runTest {
    repo.vault(VaultedNotification(packageName = "com.app.a", title = "A", text = "1", postedAt = 1000L, delivered = false))
    repo.vault(VaultedNotification(packageName = "com.app.b", title = "B", text = "2", postedAt = 2000L, delivered = false))

    val updatedCount = repo.markAllDelivered()
    assertEquals(2, updatedCount)

    val undeliveredCount = repo.undeliveredCount.first()
    assertEquals(0, undeliveredCount)
  }

  @Test
  fun `markDelivered marks specific notification IDs as delivered`() = runTest {
    val id1 = repo.vault(VaultedNotification(packageName = "com.app.a", title = "A", text = "1", postedAt = 1000L, delivered = false))
    val id2 = repo.vault(VaultedNotification(packageName = "com.app.b", title = "B", text = "2", postedAt = 2000L, delivered = false))

    val updatedCount = repo.markDelivered(listOf(id1))
    assertEquals(1, updatedCount)

    val undelivered = repo.undelivered.first()
    assertEquals(1, undelivered.size)
    assertEquals(id2, undelivered[0].id)
  }

  @Test
  fun `deleteById and clearAll remove notifications`() = runTest {
    val id1 = repo.vault(VaultedNotification(packageName = "com.app.a", title = "A", text = "1", postedAt = 1000L))
    val id2 = repo.vault(VaultedNotification(packageName = "com.app.b", title = "B", text = "2", postedAt = 2000L))

    val deleted = repo.deleteById(id1)
    assertEquals(1, deleted)
    assertEquals(1, repo.allVaulted.first().size)

    val cleared = repo.clearAll()
    assertEquals(1, cleared)
    assertTrue(repo.allVaulted.first().isEmpty())
  }
}
