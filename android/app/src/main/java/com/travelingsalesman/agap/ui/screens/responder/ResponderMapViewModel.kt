package com.travelingsalesman.agap.ui.screens.responder

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.location.LocationRepository
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResponderMapUiState(
    val incidents: List<IncidentEntity> = emptyList(),
    val assignedDispatches: List<DispatchDto> = emptyList(),
    val responders: List<ResponderUnitDto> = emptyList(),
    val myUnit: ResponderUnitDto? = null,
    val userLocation: Location? = null,
    val responderStatus: String = "IDLE", // IDLE, DISPATCHED, OFFLINE
    val agencyId: Long? = null,
    val userId: Long? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingDispatches: Boolean = false,
    val feedbackMessage: String? = null,
    val bleMeshState: BleMeshState = BleMeshState()
)

@HiltViewModel
class ResponderMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val incidentRepository: IncidentRepository,
    private val telemetryRepository: TelemetryRepository,
    private val responderRepository: ResponderRepository,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val bleMeshManager: BleMeshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResponderMapUiState())
    val uiState: StateFlow<ResponderMapUiState> = _uiState.asStateFlow()

    private var previousIncidentIds = emptySet<String>()
    private var incidentObservationInitialized = false

    init {
        viewModelScope.launch {
            bleMeshManager.meshState.collect { bState ->
                _uiState.update { it.copy(bleMeshState = bState) }
            }
        }
        // Fetch current user's responder unit details from API
        fetchMyResponderUnit()
        fetchRemoteIncidents()

        // Observe auth agencyId and userId
        viewModelScope.launch {
            authRepository.agencyId.collect { aid ->
                _uiState.update { it.copy(agencyId = aid) }
                fetchResponders(aid)
            }
        }
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                _uiState.update { it.copy(userId = uid) }
            }
        }

        // Observe live incidents from Room / WebSocket combined with local QUEUED pending packets
        viewModelScope.launch {
            combine(
                incidentRepository
                    .getRecentActiveIncidents(
                        RESPONDER_INCIDENT_WORKING_SET
                    )
                    .distinctUntilChanged(),

                telemetryRepository
                    .getRecentPendingPacketsFlow(
                        RESPONDER_PENDING_WORKING_SET
                    )
                    .distinctUntilChanged()
            ) { dbIncidents, pendingPackets ->
                val queuedEntities = pendingPackets.map { pkt ->
                    IncidentEntity(
                        incidentId = pkt.incidentUuid,
                        targetId = pkt.targetId,
                        emergencyType = pkt.emergencyType,
                        latitude = pkt.latitude,
                        longitude = pkt.longitude,
                        timestamp = pkt.timestamp,
                        status = "QUEUED",
                        packetHash = pkt.packetHash,
                        channel = "BLE_MESH",
                        deliveryPath = "BLE_CLOUD_SHORTCUT",
                        lastSyncedAt = pkt.createdAt
                    )
                }
                val existingKeys = dbIncidents.map { it.incidentId }.toSet()
                val newQueued = queuedEntities.filter { it.incidentId !in existingKeys }
                newQueued + dbIncidents
            }.collect { mergedIncidents ->
                val currentIds = mergedIncidents.map { it.incidentId }.toSet()

                if (incidentObservationInitialized) {
                    mergedIncidents.asSequence()
                        .filter { incident ->
                            incident.incidentId !in previousIncidentIds
                        }
                        .forEach(::triggerResponderSosNotification)
                }

                previousIncidentIds = currentIds
                incidentObservationInitialized = true

                _uiState.update { it.copy(incidents = mergedIncidents) }
            }
        }

        // Observe responder location
        viewModelScope.launch {
            try {
                locationRepository.getLocationUpdates().collect { loc ->
                    _uiState.update { it.copy(userLocation = loc) }
                }
            } catch (e: Exception) {
                // Location permissions
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        
        viewModelScope.launch {
            try {
                fetchMyResponderUnit()
                fetchRemoteIncidents()
                _uiState.value.agencyId?.let { fetchResponders(it) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun fetchMyResponderUnit() {
        viewModelScope.launch {
            when (val res = responderRepository.getMyResponderUnit()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            myUnit = res.data,
                            responderStatus = res.data.status
                        )
                    }
                    fetchMyDispatches()
                }
                else -> {}
            }
        }
    }

    private fun fetchRemoteIncidents() {
        viewModelScope.launch {
            incidentRepository.fetchRemoteIncidents()
        }
    }

    fun fetchMyDispatches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDispatches = true) }
            when (val result = responderRepository.getMyDispatches()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            assignedDispatches = result.data
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            feedbackMessage = "Assignments unavailable: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoadingDispatches = false) }
                }
            }
        }
    }

    fun fetchResponders(agencyId: Long? = null) {
        viewModelScope.launch {
            val result = responderRepository.getResponders(agencyId)
            if (result is Result.Success) {
                _uiState.update { it.copy(responders = result.data) }
            }
        }
    }

    fun updateMyStatus(newStatus: String) {
        val unitId = _uiState.value.myUnit?.id ?: _uiState.value.userId ?: return
        val loc = _uiState.value.userLocation
        val lat = loc?.latitude ?: 13.1391
        val lon = loc?.longitude ?: 123.7438

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val result = responderRepository.updateLocation(unitId, lat, lon, newStatus)
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            responderStatus = newStatus,
                            feedbackMessage = "Responder status set to $newStatus"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun updateDispatchStatus(dispatch: DispatchDto, newStatus: String) {
        _uiState.update { it.copy(isLoadingDispatches = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.updateDispatchStatus(dispatch.incidentId, dispatch.id, newStatus)) {
                is Result.Success -> {
                    val unitStatus = when (newStatus) {
                        "EN_ROUTE", "ON_SCENE" -> newStatus
                        "COMPLETED" -> "IDLE"
                        "CANCELLED" -> "IDLE"
                        else -> _uiState.value.responderStatus
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            responderStatus = unitStatus,
                            assignedDispatches = it.assignedDispatches.map { item ->
                                if (item.id == dispatch.id) item.copy(status = newStatus) else item
                            },
                            feedbackMessage = "Dispatch updated to $newStatus"
                        )
                    }
                    fetchRemoteIncidents()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun sendBleAcknowledgment(dispatch: DispatchDto, incident: IncidentEntity?) {
        val incidentIdentifier = incident?.packetHash?.takeIf { it.isNotBlank() }
            ?: incident?.incidentId
            ?: dispatch.incidentId.toString()
        val emergencyType = incident?.emergencyType ?: "MEDICAL"

        bleMeshManager.startAdvertisingAcknowledgment(
            incidentIdentifier = incidentIdentifier,
            emergencyType = emergencyType,
            messageCode = 1
        )
        _uiState.update {
            it.copy(feedbackMessage = "BLE acknowledgment queued for GATT relay for Incident #${dispatch.incidentId}")
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    companion object {
        private const val RESPONDER_INCIDENT_WORKING_SET = 250
        private const val RESPONDER_PENDING_WORKING_SET = 100
    }

    private fun triggerResponderSosNotification(incident: IncidentEntity) {
        try {
            val isMesh = incident.channel.contains("BLE", ignoreCase = true) ||
                    incident.deliveryPath.contains("MESH", ignoreCase = true)
            val channelTag = if (isMesh) "[BLE MESH]" else "[ONLINE]"

            val notification = NotificationCompat.Builder(
                context,
                "agap_sos_channel"
            )
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("RESPONDER EMERGENCY ALERT $channelTag")
                .setContentText(
                    "Emergency #${incident.incidentId} (${incident.emergencyType}): " +
                            if (isMesh) "Received via BLE Mesh relay!" else "Distress call received!"
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.notify(
                incident.incidentId.hashCode(),
                notification
            )
        } catch (_: Exception) {
            // Notification permission/build errors must never break responder map flow.
        }
    }
}