package com.travelingsalesman.agap.ui.screens.home

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.network.WifiHotspotMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.EmergencyPreferencesManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.EmergencyTypeRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.domain.usecase.SendSosUseCase
import com.travelingsalesman.agap.location.LocationRepository
import com.travelingsalesman.agap.ui.components.EmergencyTypeOption
import com.travelingsalesman.agap.ui.components.defaultEmergencyTypeOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isOnline: Boolean = true,
    val selectedEmergencyTypeCode: String = "MEDICAL",
    val emergencyTypes: List<EmergencyTypeOption> = defaultEmergencyTypeOptions(),
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val pendingPacketCount: Int = 0,
    val userLocation: Location? = null,
    val incidents: List<IncidentEntity> = emptyList(),
    val responders: List<ResponderUnitEntity> = emptyList(),
    val activeIncident: IncidentEntity? = null,
    val bleMeshState: BleMeshState = BleMeshState(),
    val isSendingSos: Boolean = false,
    val isRefreshing: Boolean = false,
    val sosFeedbackMessage: String? = null,
    val username: String? = null,
    val role: String? = null,
    val userDisplayId: String? = null,
    val isWifiHotspotBroadcasting: Boolean = false,
    val vibrationAlertsEnabled: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sendSosUseCase: SendSosUseCase,
    private val networkMonitor: NetworkMonitor,
    private val wifiHotspotMonitor: WifiHotspotMonitor,
    private val telemetryRepository: TelemetryRepository,
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val emergencyTypeRepository: EmergencyTypeRepository,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val bleMeshManager: BleMeshManager,
    private val relayScheduler: BleRelayScheduler,
    private val serverConfigManager: ServerConfigManager,
    private val emergencyPreferencesManager: EmergencyPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentActiveIncidentId: String? = null

    init {
        // BleMeshService owns radio/transport lifecycle. ViewModels only observe
        // state and request relay pulses, avoiding duplicate scanner/advertiser
        // start work whenever the Home screen is recreated.

        viewModelScope.launch {
            try {
                responderRepository.getResponders()
            } catch (_: Exception) {
                // Room continues to provide the latest cached responder list.
            }
        }

        viewModelScope.launch {
            responderRepository.getRespondersFlow().collect { responders ->
                _uiState.update { it.copy(responders = responders) }
            }
        }

        viewModelScope.launch {
            serverConfigManager.transmissionMode.collect { mode ->
                _uiState.update { it.copy(transmissionMode = mode) }
            }
        }

        viewModelScope.launch {
            emergencyPreferencesManager.preferences.collect { preferences ->
                _uiState.update {
                    it.copy(vibrationAlertsEnabled = preferences.vibrationAlert)
                }
            }
        }

        viewModelScope.launch {
            bleMeshManager.meshState.collect { meshState ->
                _uiState.update { it.copy(bleMeshState = meshState) }
            }
        }

        viewModelScope.launch {
            wifiHotspotMonitor.isHotspotActive.collect { active ->
                _uiState.update { it.copy(isWifiHotspotBroadcasting = active) }
            }
        }

        viewModelScope.launch {
            combine(
                authRepository.username,
                authRepository.userRole,
                authRepository.userDisplayId
            ) { name, role, id ->
                Triple(name, role, id)
            }.collect { (name, role, id) ->
                _uiState.update {
                    it.copy(
                        username = name,
                        role = role,
                        userDisplayId = id
                    )
                }
            }
        }

        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }

        viewModelScope.launch {
            relayScheduler
                .observeUndeliveredMessageCount()
                .distinctUntilChanged()
                .collect { pending ->
                    BleMeshLog.d("PENDING_DELIVERY_COUNT", "pending" to pending)
                    _uiState.update { it.copy(pendingPacketCount = pending) }
                }
        }

        viewModelScope.launch {
            try {
                locationRepository.getLocationUpdates().collect { location ->
                    _uiState.update { it.copy(userLocation = location) }
                }
            } catch (_: Exception) {
                // Location can be temporarily unavailable before permission is granted.
            }
        }

        viewModelScope.launch {
            incidentRepository
                .getRecentIncidents(
                    CITIZEN_INCIDENT_WORKING_SET
                )
                .distinctUntilChanged()
                .collect { incidents ->
                    val active = if (currentActiveIncidentId != null) {
                        incidents.find { it.incidentId == currentActiveIncidentId }
                    } else {
                        incidents.firstOrNull {
                            it.status.uppercase() !in TERMINAL_INCIDENT_STATUSES
                        }
                    }

                    _uiState.update {
                        it.copy(
                            incidents = incidents,
                            activeIncident = active
                        )
                    }
                }
        }

        viewModelScope.launch {
            emergencyTypeRepository.getActiveEmergencyTypesFlow().collect { types ->
                val options = types
                    .filter { it.isActive }
                    .map {
                        EmergencyTypeOption(
                            code = it.code,
                            displayName = it.name,
                            targetAgencyType = it.targetAgencyType
                        )
                    }
                    .ifEmpty { defaultEmergencyTypeOptions() }

                val currentCode = _uiState.value.selectedEmergencyTypeCode
                val selectedCode = if (
                    options.any { it.code.equals(currentCode, ignoreCase = true) }
                ) {
                    currentCode
                } else {
                    options.first().code
                }

                _uiState.update {
                    it.copy(
                        emergencyTypes = options,
                        selectedEmergencyTypeCode = selectedCode
                    )
                }
            }
        }

        viewModelScope.launch {
            emergencyTypeRepository.refreshEmergencyTypes()
        }
    }

    fun selectEmergencyType(code: String) {
        if (_uiState.value.isSendingSos || _uiState.value.isRefreshing) return

        _uiState.update {
            it.copy(selectedEmergencyTypeCode = code)
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            try {
                emergencyTypeRepository.refreshEmergencyTypes()
                try { responderRepository.getResponders() } catch (_: Exception) {}

                if (!ServerConfigManager.isBleOnlyActive && _uiState.value.isOnline) {
                    try { incidentRepository.fetchRemoteIncidents() } catch (_: Exception) {}
                }

                // The foreground service owns discovery/advertising. A refresh
                // only needs to nudge the shared relay scheduler.
                bleMeshManager.requestImmediateRelayPulse()
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun selectTransmissionMode(mode: TransmissionMode) {
        // Update Compose immediately so the selected policy, route summary, and status
        // chip react on the same tap. DataStore persistence follows in the coroutine.
        _uiState.update { it.copy(transmissionMode = mode) }

        viewModelScope.launch {
            BleMeshLog.i("UI_TRANSPORT_MODE_SELECTED", "mode" to mode.name)
            serverConfigManager.setTransmissionMode(mode)

            when (mode) {
                TransmissionMode.AUTO, TransmissionMode.FORCE_BLE_MESH -> {
                    // The foreground service remains the single transport owner;
                    // changing policy only needs an immediate scheduler pulse.
                    bleMeshManager.requestImmediateRelayPulse()
                }

                TransmissionMode.FORCE_INTERNET -> {
                    // Keep the relay listener alive for emergency-network continuity, but new
                    // citizen-originated reports are routed directly by the repository policy.
                }
            }
        }
    }

    /**
     * Sends an SOS for the exact emergency code supplied by the joystick.
     *
     * Passing the code directly is important. We do not rely on a Compose selection update
     * completing before dispatch, so the emergency released by the user is always the type
     * sent to SendSosUseCase.
     */
    fun triggerSos(emergencyTypeCode: String) {
        if (_uiState.value.isSendingSos) return

        val normalizedCode = emergencyTypeCode.trim().uppercase()
        val knownType = _uiState.value.emergencyTypes.firstOrNull {
            it.code.equals(normalizedCode, ignoreCase = true)
        }

        val codeToSend = knownType?.code ?: normalizedCode

        _uiState.update {
            it.copy(
                selectedEmergencyTypeCode = codeToSend,
                isSendingSos = true,
                sosFeedbackMessage = null
            )
        }

        viewModelScope.launch {
            val stateAtDispatch = _uiState.value
            BleMeshLog.i(
                "SOS_DISPATCH_REQUEST",
                "mode" to stateAtDispatch.transmissionMode.name,
                "onlineObserved" to stateAtDispatch.isOnline,
                "emergency" to codeToSend
            )

            // The SOS screen already requires location permission + GPS before the
            // joystick can be armed. This is a second defensive check at the data layer:
            // never turn a missing location into the fake coordinate 0.0, 0.0.
            val fix = locationRepository.getBoundedLocationFix(5000L)

            val latitude =
                fix.latitude
                    ?: stateAtDispatch.userLocation?.latitude

            val longitude =
                fix.longitude
                    ?: stateAtDispatch.userLocation?.longitude

            if (latitude == null || longitude == null) {
                _uiState.update {
                    it.copy(
                        isSendingSos = false,
                        sosFeedbackMessage =
                            "GPS is enabled, but AGAP could not obtain a location fix yet. Move to an open area and try the SOS drag again."
                    )
                }
                return@launch
            }

            when (
                val result = sendSosUseCase(
                    emergencyType = codeToSend,
                    latitude = latitude,
                    longitude = longitude,
                    isOnline = stateAtDispatch.isOnline,
                    forcedMode = stateAtDispatch.transmissionMode
                )
            ) {
                is Result.Success -> {
                    BleMeshLog.i(
                        "SOS_DISPATCH_SUCCESS",
                        "mode" to stateAtDispatch.transmissionMode.name,
                        "incidentPresent" to !result.data.incidentId.isNullOrEmpty()
                    )
                    if (stateAtDispatch.transmissionMode == TransmissionMode.FORCE_BLE_MESH ||
                        (stateAtDispatch.transmissionMode == TransmissionMode.AUTO && !stateAtDispatch.isOnline)
                    ) {
                        bleMeshManager.requestImmediateRelayPulse()
                    }

                    if (!result.data.incidentId.isNullOrEmpty()) {
                        currentActiveIncidentId = result.data.incidentId
                    }

                    _uiState.update {
                        it.copy(
                            isSendingSos = false,
                            sosFeedbackMessage = result.data.message
                                ?: "${knownType?.displayName ?: codeToSend} SOS sent to emergency command."
                        )
                    }
                }

                is Result.Error -> {
                    BleMeshLog.e(
                        "SOS_DISPATCH_FAILED",
                        "mode" to stateAtDispatch.transmissionMode.name,
                        "errorType" to result.exception::class.java.simpleName,
                        error = result.exception
                    )
                    _uiState.update {
                        it.copy(
                            isSendingSos = false,
                            sosFeedbackMessage =
                                "SOS queued for emergency relay. ${result.message.orEmpty()}".trim()
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    /**
     * Compatibility overload for any older call sites that still invoke triggerSos()
     * without a type. New SOS UI calls triggerSos(typeCode) directly.
     */
    fun triggerSos() {
        triggerSos(_uiState.value.selectedEmergencyTypeCode)
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(sosFeedbackMessage = null) }
    }

    companion object {
        private val TERMINAL_INCIDENT_STATUSES =
            setOf("RESOLVED", "CANCELLED", "FALSE_ALARM")

        /**
         * Citizen SOS needs the current/near-recent incident context, not an unbounded archive.
         */
        private const val CITIZEN_INCIDENT_WORKING_SET = 100
    }
}
