package com.msahil432.autodelete.data

import kotlinx.coroutines.flow.Flow

interface DataRepository {
    fun getAllFolders(): Flow<List<Folder>>
    suspend fun insertFolder(folder: Folder): Long
    suspend fun deleteFolder(folder: Folder)
    suspend fun getTopTimeUsages(folderId: Long): List<TimeUsage>
    suspend fun getTimeUsage(folderId: Long, duration: Long): TimeUsage?
    suspend fun insertTimeUsage(timeUsage: TimeUsage)
    suspend fun updateTimeUsage(timeUsage: TimeUsage)
    suspend fun insertScheduledDeletion(scheduledDeletion: ScheduledDeletion): Long
    suspend fun getScheduledDeletion(id: Long): ScheduledDeletion?
    suspend fun removeScheduledDeletion(id: Long)
    suspend fun getAllScheduledDeletions(): List<ScheduledDeletion>
    suspend fun insertHistory(history: History): Long
    suspend fun updateHistory(history: History)
    fun getAllHistory(): Flow<List<History>>
}

class DefaultDataRepository(private val appDao: AppDao) : DataRepository {
    override fun getAllFolders(): Flow<List<Folder>> = appDao.getAllFolders()
    override suspend fun insertFolder(folder: Folder): Long = appDao.insertFolder(folder)
    override suspend fun deleteFolder(folder: Folder) = appDao.deleteFolder(folder)
    override suspend fun getTopTimeUsages(folderId: Long): List<TimeUsage> = appDao.getTopTimeUsages(folderId)
    override suspend fun getTimeUsage(folderId: Long, duration: Long): TimeUsage? = appDao.getTimeUsage(folderId, duration)
    override suspend fun insertTimeUsage(timeUsage: TimeUsage) = appDao.insertTimeUsage(timeUsage)
    override suspend fun updateTimeUsage(timeUsage: TimeUsage) = appDao.updateTimeUsage(timeUsage)
    override suspend fun insertScheduledDeletion(scheduledDeletion: ScheduledDeletion): Long = appDao.insertScheduledDeletion(scheduledDeletion)
    override suspend fun getScheduledDeletion(id: Long): ScheduledDeletion? = appDao.getScheduledDeletion(id)
    override suspend fun removeScheduledDeletion(id: Long) = appDao.removeScheduledDeletion(id)
    override suspend fun getAllScheduledDeletions(): List<ScheduledDeletion> = appDao.getAllScheduledDeletions()
    override suspend fun insertHistory(history: History): Long = appDao.insertHistory(history)
    override suspend fun updateHistory(history: History) = appDao.updateHistory(history)
    override fun getAllHistory(): Flow<List<History>> = appDao.getAllHistory()
}

