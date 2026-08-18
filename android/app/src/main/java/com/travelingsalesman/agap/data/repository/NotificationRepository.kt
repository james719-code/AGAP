package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.remote.dto.AdminAlertDto
import com.travelingsalesman.agap.data.remote.dto.AlertCategory
import com.travelingsalesman.agap.data.remote.dto.AlertSeverity
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getUnreadCount(): Flow<Int>
    fun getRecentNotifications(limit: Int): Flow<List<NotificationEntity>>
    suspend fun refreshNotifications()
    suspend fun markAsRead(id: Long)
    suspend fun clearAll()

    // Admin Alerts
    fun getAdminAlerts(): Flow<List<AdminAlertEntity>>
    fun getUnreadAlertCount(): Flow<Int>
    suspend fun refreshAlerts()
    suspend fun createAlert(
        title: String,
        body: String,
        category: AlertCategory,
        severity: AlertSeverity,
        isPinned: Boolean = false,
        predefinedMessageId: Long? = null
    ): com.travelingsalesman.agap.core.util.Result<AdminAlertDto>
    suspend fun markAlertAsRead(id: Long)
}
