package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getUnreadCount(): Flow<Int>
    fun getRecentNotifications(limit: Int): Flow<List<NotificationEntity>>
    suspend fun refreshNotifications()
    suspend fun markAsRead(id: Long)
    suspend fun clearAll()
}
