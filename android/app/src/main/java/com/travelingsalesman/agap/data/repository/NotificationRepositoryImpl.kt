package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.notification.AdminAlertNotificationCoordinator
import com.travelingsalesman.agap.data.local.dao.AdminAlertDao
import com.travelingsalesman.agap.data.local.dao.NotificationDao
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.remote.NotificationApiService
import com.travelingsalesman.agap.data.remote.dto.AdminAlertDto
import com.travelingsalesman.agap.data.remote.dto.AlertCategory
import com.travelingsalesman.agap.data.remote.dto.AlertSeverity
import com.travelingsalesman.agap.data.remote.dto.CreateAlertRequestDto
import com.travelingsalesman.agap.data.remote.dto.NotificationDto
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

import timber.log.Timber

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
    private val adminAlertDao: AdminAlertDao,
    private val apiService: NotificationApiService,
    private val adminAlertNotifications: AdminAlertNotificationCoordinator
) : NotificationRepository {

    override fun getUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()

    override fun getRecentNotifications(limit: Int): Flow<List<NotificationEntity>> =
        notificationDao.getRecentNotifications(limit)

    override suspend fun refreshNotifications() {
        try {
            val response = apiService.listNotifications()
            if (response.isSuccessful && response.body() != null) {
                val entities = response.body()!!.map { dto ->
                    dto.toNotificationEntity(::parseServerTime)
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
            adminAlertDao.clearAll()
        } catch (_: Exception) {}
    }

    override fun getAdminAlerts(): Flow<List<AdminAlertEntity>> = adminAlertDao.getActiveAlerts()

    override fun getUnreadAlertCount(): Flow<Int> = adminAlertDao.getUnreadCount()

    override suspend fun refreshAlerts() {
        try {
            val response = apiService.listAlerts(activeOnly = true)
            if (response.isSuccessful && response.body() != null) {
                val alerts = response.body()!!.alerts
                Timber.d("Refreshing alerts: found ${alerts.size} alerts")
                val entities = alerts.map { it.toEntity(::parseServerTime) }
                
                // Find new alerts to notify
                entities.forEach { entity ->
                    if (adminAlertDao.getAlertById(entity.id) == null) {
                        Timber.i("New alert detected: ${entity.title}")
                        adminAlertNotifications.showAlert(entity)
                    }
                }
                
                adminAlertDao.insertAlerts(entities)
            } else {
                Timber.w("Failed to refresh alerts: ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing alerts")
        }
    }

    override suspend fun createAlert(
        title: String,
        body: String,
        category: AlertCategory,
        severity: AlertSeverity,
        isPinned: Boolean,
        predefinedMessageId: Long?
    ): com.travelingsalesman.agap.core.util.Result<AdminAlertDto> {
        Timber.d("Creating alert: $title, category: $category, severity: $severity, pmId: $predefinedMessageId")
        return try {
            val request = CreateAlertRequestDto(
                title = title,
                body = body,
                category = category.name,
                severity = severity.name,
                isPinned = isPinned,
                // Workaround: Stop sending predefinedMessageId until server-side SQL error is fixed.
                // The server crashes looking up the predefined_messages_legacy table.
                predefinedMessageId = null 
            )
            val response = apiService.createAlert(request)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                Timber.i("Alert created on server: ${dto.uuid} (ID: ${dto.id})")
                val entity = dto.toEntity(::parseServerTime)
                adminAlertDao.insertAlerts(listOf(entity))
                Timber.d("Alert saved to local database: ${entity.uuid}")
                com.travelingsalesman.agap.core.util.Result.Success(dto)
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.w("Failed to create alert on server: ${response.code()}, $errorBody")
                com.travelingsalesman.agap.core.util.Result.Error("Failed to create alert: ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception creating alert")
            com.travelingsalesman.agap.core.util.Result.Error(e, "Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun markAlertAsRead(id: Long) {
        try {
            // Backend might not have a dedicated markRead for admin_alerts yet if it's mirrored from notification, 
            // but the requirement says "match in our database (admin_alerts, admin_alert_read)".
            // For now we mark it locally.
            adminAlertDao.markAsRead(id)
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

internal fun NotificationDto.toNotificationEntity(
    parseServerTime: (String) -> Long
): NotificationEntity = NotificationEntity(
    id = id,
    userId = userId,
    title = title,
    message = message,
    type = type,
    isRead = isRead,
    referenceId = referenceId,
    createdAt = parseServerTime(createdAt)
)

internal fun AdminAlertDto.toEntity(
    parseServerTime: (String) -> Long
): AdminAlertEntity = AdminAlertEntity(
    id = id,
    uuid = uuid,
    authorId = authorId,
    authorName = authorName,
    title = title,
    body = body,
    category = category.name,
    severity = severity.name,
    isPinned = isPinned,
    isActive = isActive,
    publishedAt = parseServerTime(publishedAt),
    expiresAt = expiresAt?.let { parseServerTime(it) },
    createdAt = parseServerTime(createdAt),
    isRead = isRead
)
