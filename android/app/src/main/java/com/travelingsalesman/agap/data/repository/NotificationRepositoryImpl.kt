package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.dao.NotificationDao
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.remote.NotificationApiService
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
    private val apiService: NotificationApiService
) : NotificationRepository {

    override fun getUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()

    override fun getRecentNotifications(limit: Int): Flow<List<NotificationEntity>> =
        notificationDao.getRecentNotifications(limit)

    override suspend fun refreshNotifications() {
        try {
            val response = apiService.listNotifications()
            if (response.isSuccessful && response.body() != null) {
                val entities = response.body()!!.map { dto ->
                    NotificationEntity(
                        id = dto.id,
                        userId = dto.userId,
                        title = dto.title,
                        message = dto.message,
                        type = dto.type,
                        isRead = dto.isRead,
                        referenceId = dto.referenceId,
                        createdAt = parseServerTime(dto.createdAt)
                    )
                }
                notificationDao.insertNotifications(entities)
            }
        } catch (_: Exception) {
            // Network or other error
        }
    }

    override suspend fun markAsRead(id: Long) {
        try {
            apiService.markAsRead(id)
            notificationDao.markAsRead(id)
        } catch (_: Exception) {
            // Still mark as read locally even if API fails
            notificationDao.markAsRead(id)
        }
    }

    override suspend fun clearAll() {
        try {
            notificationDao.clearAll()
        } catch (_: Exception) {}
    }

    private fun parseServerTime(time: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(time)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
