package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing vaulted and silenced notifications.
 */
class NotificationRepository(private val notificationDao: NotificationDao) {

    /** Returns a [Flow] of all vaulted notifications ordered newest first. */
    val allVaulted: Flow<List<VaultedNotification>> = notificationDao.getAllVaulted()

    /** Returns a [Flow] of undelivered vaulted notifications ordered newest first. */
    val undelivered: Flow<List<VaultedNotification>> = notificationDao.getUndelivered()

    /** Returns a [Flow] of the total count of undelivered notifications. */
    val undeliveredCount: Flow<Int> = notificationDao.getUndeliveredCount()

    /**
     * Inserts a new notification into the vault.
     *
     * @param notification The vaulted notification record to persist.
     * @return The auto-generated row ID.
     */
    suspend fun vault(notification: VaultedNotification): Long {
        return notificationDao.insert(notification)
    }

    /** Returns all undelivered notifications synchronously. */
    suspend fun getUndeliveredSync(): List<VaultedNotification> {
        return notificationDao.getUndeliveredSync()
    }

    /**
     * Marks all undelivered notifications as delivered.
     *
     * @return Number of rows updated.
     */
    suspend fun markAllDelivered(): Int {
        return notificationDao.markAllDelivered()
    }

    /**
     * Marks specific notifications as delivered by their IDs.
     *
     * @param ids List of notification IDs to mark as delivered.
     * @return Number of rows updated.
     */
    suspend fun markDelivered(ids: List<Long>): Int {
        return notificationDao.markDelivered(ids)
    }

    /**
     * Clears all vaulted notification records from the database.
     *
     * @return Number of rows deleted.
     */
    suspend fun clearAll(): Int {
        return notificationDao.clearAll()
    }

    /**
     * Deletes a single vaulted notification record by its [id].
     *
     * @param id The row ID of the notification.
     * @return Number of rows deleted.
     */
    suspend fun deleteById(id: Long): Int {
        return notificationDao.deleteById(id)
    }
}

