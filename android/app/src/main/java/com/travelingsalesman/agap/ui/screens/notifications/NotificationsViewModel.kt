package com.travelingsalesman.agap.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.NotificationDao
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.remote.NotificationApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val apiService: NotificationApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            notificationDao.getRecentNotifications(NOTIFICATION_WORKING_SET).collect { list ->
                _uiState.update { it.copy(notifications = list) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
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
                    entities.chunked(NOTIFICATION_WRITE_CHUNK).forEach { batch ->
                        notificationDao.insertNotifications(batch)
                    }
                }
            } catch (e: Exception) {
                // Network error
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            try {
                apiService.markAsRead(id)
                notificationDao.markAsRead(id)
            } catch (_: Exception) {}
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            notificationDao.clearAll()
        }
    }

    private fun parseServerTime(time: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(time)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    companion object {
        // Keep old notifications in Room while exposing only a bounded recent
        // working set to Compose. This prevents years of history from becoming
        // one giant recomposition/list allocation.
        private const val NOTIFICATION_WORKING_SET = 250
        private const val NOTIFICATION_WRITE_CHUNK = 200
    }

}
