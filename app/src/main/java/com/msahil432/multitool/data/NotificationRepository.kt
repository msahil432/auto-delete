package com.msahil432.multitool.data

import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val notificationDao: NotificationDao) {

    val allVaulted: Flow<List<VaultedNotification>> = notificationDao.getAllVaulted()

    val undelivered: Flow<List<VaultedNotification>> = notificationDao.getUndelivered()

    val undeliveredCount: Flow<Int> = notificationDao.getUndeliveredCount()

    suspend fun vault(notification: VaultedNotification): Long {
        return notificationDao.insert(notification)
    }

    suspend fun getUndeliveredSync(): List<VaultedNotification> {
        return notificationDao.getUndeliveredSync()
    }

    suspend fun markAllDelivered(): Int {
        return notificationDao.markAllDelivered()
    }

    suspend fun markDelivered(ids: List<Long>): Int {
        return notificationDao.markDelivered(ids)
    }

    suspend fun clearAll(): Int {
        return notificationDao.clearAll()
    }

    suspend fun deleteById(id: Long): Int {
        return notificationDao.deleteById(id)
    }
}
