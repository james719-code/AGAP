package com.travelingsalesman.agap.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.*
import com.travelingsalesman.agap.data.repository.*
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.remote.NotificationApiService
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminManagementUiState(
    val selectedTab: Int = 0,
    val currentUserId: Long? = null,
    val usersList: List<UserDto> = emptyList(),
    val agenciesList: List<AgencyDto> = emptyList(),
    val responderUnitsList: List<ResponderUnitDto> = emptyList(),
    val alertsList: List<AdminAlertDto> = emptyList(),
    val predefinedMessages: List<PredefinedMessageEntity> = emptyList(),
    val selectedUserDetails: UserDto? = null,
    val selectedAgencyDetails: AgencyDto? = null,
    val selectedResponderDetails: ResponderUnitDto? = null,
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class AdminManagementViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val agencyRepository: AgencyRepository,
    private val responderRepository: ResponderRepository,
    private val predefinedMessageRepository: PredefinedMessageRepository,
    private val notificationApiService: NotificationApiService,
    private val bleMeshManager: BleMeshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminManagementUiState())
    val uiState: StateFlow<AdminManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.userId.collect { userId ->
                _uiState.update { it.copy(currentUserId = userId) }
            }
        }
        viewModelScope.launch {
            predefinedMessageRepository.getMessagesFlow().collect { list ->
                _uiState.update { it.copy(predefinedMessages = list) }
            }
        }
        refreshAllData()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex.coerceIn(0, 3)) }
        when (tabIndex) {
            0 -> fetchUsers()
            1 -> fetchAgencies()
            2 -> fetchResponders()
            3 -> fetchAlerts()
        }
    }

    /**
     * Refreshes the management datasets in parallel.
     */
    fun refreshAllData() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, feedbackMessage = null) }

            val usersDeferred = async { authRepository.listUsers() }
            val agenciesDeferred = async { agencyRepository.getAgencies() }
            val respondersDeferred = async { responderRepository.getResponders() }
            val alertsDeferred = async { 
                runCatching { notificationApiService.listAlerts(activeOnly = false) }.getOrNull()
            }

            val usersResult = usersDeferred.await()
            val agenciesResult = agenciesDeferred.await()
            val respondersResult = respondersDeferred.await()
            val alertsResponse = alertsDeferred.await()

            predefinedMessageRepository.syncCatalog()

            _uiState.update { current ->
                current.copy(
                    usersList = usersResult.successDataOr(current.usersList),
                    agenciesList = agenciesResult.successDataOr(current.agenciesList),
                    responderUnitsList = respondersResult.successDataOr(current.responderUnitsList),
                    alertsList = if (alertsResponse?.isSuccessful == true) alertsResponse.body()!!.alerts else current.alertsList,
                    isLoading = false,
                    feedbackMessage = listOfNotNull(
                        usersResult.errorMessageOrNull(),
                        agenciesResult.errorMessageOrNull(),
                        respondersResult.errorMessageOrNull()
                    ).distinct().takeIf { it.isNotEmpty() }?.joinToString(" • ")
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // User access management
    // -------------------------------------------------------------------------

    fun fetchUsers() {
        viewModelScope.launch { reloadUsers() }
    }

    fun loadUserDetails(userId: Long) {
        viewModelScope.launch {
            when (val result = authRepository.getUserById(userId)) {
                is Result.Success -> _uiState.update { it.copy(selectedUserDetails = result.data) }
                is Result.Error -> showFeedback(result.message ?: "Unable to load account details")
                Result.Loading -> Unit
            }
        }
    }

    fun loadAgencyDetails(agencyId: Long) {
        viewModelScope.launch {
            when (val result = agencyRepository.getAgency(agencyId)) {
                is Result.Success -> _uiState.update { it.copy(selectedAgencyDetails = result.data) }
                is Result.Error -> showFeedback(result.message ?: "Unable to load agency details")
                Result.Loading -> Unit
            }
        }
    }

    fun loadResponderDetails(responderId: Long) {
        viewModelScope.launch {
            when (val result = responderRepository.getResponder(responderId)) {
                is Result.Success -> _uiState.update { it.copy(selectedResponderDetails = result.data) }
                is Result.Error -> showFeedback(result.message ?: "Unable to load responder unit details")
                Result.Loading -> Unit
            }
        }
    }

    fun verifyUserAccount(userId: Long) {
        viewModelScope.launch {
            when (
                val result = authRepository.updateUserStatus(
                    userId,
                    "APPROVED",
                    "Approved from admin mobile"
                )
            ) {
                is Result.Success -> {
                    reloadUsers()
                    refreshSelectedUserDetails(userId)
                    showFeedback("User account approved")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to approve user account")
                Result.Loading -> Unit
            }
        }
    }

    fun rejectUserAccount(userId: Long) {
        viewModelScope.launch {
            when (
                val result = authRepository.updateUserStatus(
                    userId,
                    "REJECTED",
                    "Rejected from admin mobile"
                )
            ) {
                is Result.Success -> {
                    reloadUsers()
                    refreshSelectedUserDetails(userId)
                    showFeedback("User registration rejected")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to reject user account")
                Result.Loading -> Unit
            }
        }
    }

    fun suspendUserAccount(userId: Long) {
        viewModelScope.launch {
            when (
                val result = authRepository.updateUserStatus(
                    userId,
                    "SUSPENDED",
                    "Suspended from admin mobile"
                )
            ) {
                is Result.Success -> {
                    reloadUsers()
                    refreshSelectedUserDetails(userId)
                    showFeedback("User account suspended")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to suspend user account")
                Result.Loading -> Unit
            }
        }
    }

    fun deleteUserAccount(userId: Long) {
        viewModelScope.launch {
            when (val result = authRepository.deleteUser(userId)) {
                is Result.Success -> {
                    reloadUsers()
                    showFeedback("User account removed")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to remove user account")
                Result.Loading -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Agency management
    // -------------------------------------------------------------------------

    fun fetchAgencies() {
        viewModelScope.launch { reloadAgencies() }
    }

    fun createAgency(code: String, name: String, type: String, contact: String) {
        viewModelScope.launch {
            val dto = AgencyDto(
                code = code.trim(),
                name = name.trim(),
                type = type.trim(),
                contactNumber = contact.trim()
            )

            when (val result = agencyRepository.createAgency(dto)) {
                is Result.Success -> {
                    reloadAgencies()
                    showFeedback("Agency created")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to create agency")
                Result.Loading -> Unit
            }
        }
    }

    fun updateAgency(id: Long, code: String, name: String, type: String, contact: String) {
        viewModelScope.launch {
            val dto = AgencyDto(
                id = id,
                code = code.trim(),
                name = name.trim(),
                type = type.trim(),
                contactNumber = contact.trim()
            )

            when (val result = agencyRepository.updateAgency(id, dto)) {
                is Result.Success -> {
                    reloadAgencies()
                    refreshSelectedAgencyDetails(id)
                    showFeedback("Agency updated")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to update agency")
                Result.Loading -> Unit
            }
        }
    }

    fun deleteAgency(id: Long) {
        viewModelScope.launch {
            when (val result = agencyRepository.deleteAgency(id)) {
                is Result.Success -> {
                    reloadAgencies()
                    _uiState.update { it.copy(selectedAgencyDetails = null) }
                    // Unit relationships may have changed through cascading backend rules.
                    reloadResponders()
                    showFeedback("Agency deleted")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to delete agency")
                Result.Loading -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Responder unit management
    // -------------------------------------------------------------------------

    fun fetchResponders() {
        viewModelScope.launch { reloadResponders() }
    }

    fun createResponderUnit(
        agencyId: Long,
        unitCallsign: String,
        responderName: String,
        phone: String,
        targetId: String
    ) {
        // responderName and phone are retained in the signature for compatibility with the
        // current screen/API layer. ResponderUnitDto does not expose those fields yet.
        @Suppress("UNUSED_VARIABLE")
        val compatibilityFields = responderName to phone

        viewModelScope.launch {
            val normalizedCallsign = unitCallsign.trim()
            val dto = ResponderUnitDto(
                agencyId = agencyId,
                unitCallsign = normalizedCallsign,
                targetId = targetId.trim().ifBlank { "TARGET-$normalizedCallsign" },
                status = "IDLE"
            )

            when (val result = responderRepository.createResponderUnit(dto)) {
                is Result.Success -> {
                    reloadResponders()
                    showFeedback("Responder unit created")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to create responder unit")
                Result.Loading -> Unit
            }
        }
    }

    fun updateResponderUnit(
        id: Long,
        agencyId: Long,
        unitCallsign: String,
        status: String,
        targetId: String
    ) {
        viewModelScope.launch {
            val dto = ResponderUnitDto(
                id = id,
                agencyId = agencyId,
                unitCallsign = unitCallsign.trim(),
                targetId = targetId.trim(),
                status = normalizeResponderStatusValue(status)
            )

            when (val result = responderRepository.updateResponderUnit(id, dto)) {
                is Result.Success -> {
                    reloadResponders()
                    showFeedback("Responder unit updated")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to update responder unit")
                Result.Loading -> Unit
            }
        }
    }

    fun deleteResponderUnit(id: Long) {
        viewModelScope.launch {
            when (val result = responderRepository.deleteResponderUnit(id)) {
                is Result.Success -> {
                    reloadResponders()
                    showFeedback("Responder unit deleted")
                }

                is Result.Error -> showFeedback(result.message ?: "Unable to delete responder unit")
                Result.Loading -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Alert & Announcement broadcast
    // -------------------------------------------------------------------------

    fun fetchAlerts() {
        viewModelScope.launch {
            try {
                val response = notificationApiService.listAlerts(activeOnly = false)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.update { it.copy(alertsList = response.body()!!.alerts) }
                }
            } catch (e: Exception) {
                showFeedback("Failed to load alerts")
            }
        }
    }

    fun createAlert(
        title: String,
        body: String,
        category: AlertCategory,
        severity: AlertSeverity,
        predefinedMessageId: Long? = null,
        isPinned: Boolean = false
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val request = CreateAlertRequestDto(
                    title = title,
                    body = body,
                    category = category.name,
                    severity = severity.name,
                    // Workaround: Stop sending predefinedMessageId until server-side SQL error is fixed.
                    predefinedMessageId = null,
                    isPinned = isPinned
                )
                val response = notificationApiService.createAlert(request)
                if (response.isSuccessful && response.body() != null) {
                    val created = response.body()!!
                    showFeedback("Announcement broadcasted successfully")
                    
                    // If it's a predefined message, also broadcast via BLE Mesh
                    predefinedMessageId?.let { id ->
                        val msg = _uiState.value.predefinedMessages.firstOrNull { it.id == id.toInt() }
                        if (msg != null) {
                            bleMeshManager.queuePredefinedResponse(
                                incidentIdentifier = created.id.toString(),
                                messageType = msg.messageType,
                                protocolCode = msg.protocolCode
                            )
                        }
                    }
                    
                    fetchAlerts()
                } else {
                    showFeedback("Failed to broadcast: ${response.code()}")
                }
            } catch (e: Exception) {
                showFeedback("Broadcast error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            try {
                val response = notificationApiService.deleteAlert(id)
                if (response.isSuccessful) {
                    showFeedback("Alert deleted")
                    fetchAlerts()
                }
            } catch (e: Exception) {
                showFeedback("Delete error: ${e.message}")
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private suspend fun reloadUsers() {
        when (val result = authRepository.listUsers()) {
            is Result.Success -> _uiState.update { it.copy(usersList = result.data) }
            is Result.Error -> showFeedback(result.message ?: "Unable to load users")
            Result.Loading -> Unit
        }
    }

    private suspend fun refreshSelectedUserDetails(userId: Long) {
        if (_uiState.value.selectedUserDetails?.id != userId) return
        when (val result = authRepository.getUserById(userId)) {
            is Result.Success -> _uiState.update { it.copy(selectedUserDetails = result.data) }
            is Result.Error -> Unit
            Result.Loading -> Unit
        }
    }

    private suspend fun refreshSelectedAgencyDetails(agencyId: Long) {
        if (_uiState.value.selectedAgencyDetails?.id != agencyId) return
        when (val result = agencyRepository.getAgency(agencyId)) {
            is Result.Success -> _uiState.update { it.copy(selectedAgencyDetails = result.data) }
            is Result.Error, Result.Loading -> Unit
        }
    }

    private suspend fun reloadAgencies() {
        when (val result = agencyRepository.getAgencies()) {
            is Result.Success -> _uiState.update { it.copy(agenciesList = result.data) }
            is Result.Error -> showFeedback(result.message ?: "Unable to load agencies")
            Result.Loading -> Unit
        }
    }

    private suspend fun reloadResponders() {
        when (val result = responderRepository.getResponders()) {
            is Result.Success -> _uiState.update { it.copy(responderUnitsList = result.data) }
            is Result.Error -> showFeedback(result.message ?: "Unable to load responder units")
            Result.Loading -> Unit
        }
    }

    private fun showFeedback(message: String) {
        _uiState.update { it.copy(feedbackMessage = message) }
    }
}

private fun <T> Result<T>.successDataOr(fallback: T): T = when (this) {
    is Result.Success -> data
    else -> fallback
}

private fun Result<*>.errorMessageOrNull(): String? = when (this) {
    is Result.Error -> message ?: "Unable to refresh management data"
    else -> null
}
