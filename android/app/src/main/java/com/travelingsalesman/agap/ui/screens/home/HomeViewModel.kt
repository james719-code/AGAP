package com.travelingsalesman.agap.ui.screens.home

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.bluetooth.CitizenSosTelemetryService
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.network.WifiHotspotMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.EmergencyPreferencesManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.EmergencyTypeRepository
import com.travelingsalesman.agap.data.repository.IncidentReportRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.NotificationRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isReceivingEmergency: Boolean = false,
    val isRefreshing: Boolean = false,
    val sosFeedbackMessage: String? = null,
    val username: String? = null,
    val role: String? = null,
    val userDisplayId: String? = null,
    val isWifiHotspotBroadcasting: Boolean = false,
    val vibrationAlertsEnabled: Boolean = true,
    val unreadNotificationCount: Int = 0,
    /** Non-null while the cancel window after an SOS release is running. */
    val sosCountdown: SosCountdownUiState? = null
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
    private val incidentReportRepository: IncidentReportRepository,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val bleMeshManager: BleMeshManager,
    private val relayScheduler: BleRelayScheduler,
    private val serverConfigManager: ServerConfigManager,
    private val emergencyPreferencesManager: EmergencyPreferencesManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Citizen location tracking is allowed to follow only a locally persisted
     * SOS created by this device. The general incident cache also contains
     * other citizens' emergencies, so it must never select a tracking target.
     */
    private var localQuickSosReports: List<LocalIncidentReportEntity> = emptyList()
    private var cachedIncidents: List<IncidentEntity> = emptyList()
    private var localSosHistoryLoaded = false
    private var userRoleLoaded = false
    private var isCitizenAccount = false
    private var trackedCitizenIncidentId: String? = null
    private var hasReconciledCitizenTracking = false
    private var incidentsInitialized = false
    private val knownIncidentIds = mutableSetOf<String>()
    private var receivingResetJob: Job? = null
    private val sosCountdown = SosCountdown()
    private var sosCountdownJob: Job? = null

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
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
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
                isCitizenAccount = role.equals("CITIZEN", ignoreCase = true)
                userRoleLoaded = true
                updateCitizenOwnedIncident()
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
                    val incomingIncident = incidentsInitialized && incidents.any {
                        knownIncidentIds.add(it.logicalId.ifBlank { it.incidentId }).not()
                    }
                    incidents.forEach { knownIncidentIds.add(it.logicalId.ifBlank { it.incidentId }) }
                    incidentsInitialized = true
                    if (incomingIncident) {
                        receivingResetJob?.cancel()
                        _uiState.update { it.copy(isReceivingEmergency = true) }
                        receivingResetJob = viewModelScope.launch {
                            delay(2_000L)
                            _uiState.update { it.copy(isReceivingEmergency = false) }
                        }
                    }
                    cachedIncidents = incidents
                    updateCitizenOwnedIncident()

                    _uiState.update { it.copy(incidents = incidents) }
                }
        }

        viewModelScope.launch {
            incidentReportRepository
                .getRecentLocalSosHistoryFlow(CITIZEN_SOS_HISTORY_WORKING_SET)
                .distinctUntilChanged()
                .collect { reports ->
                    localQuickSosReports = reports.filter {
                        it.submissionType.equals(QUICK_SOS_SUBMISSION_TYPE, ignoreCase = true)
                    }
                    localSosHistoryLoaded = true
                    updateCitizenOwnedIncident()
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
                                sosFeedbackMessage = UserMessageResolver.safe(
                                    result.message,
                                    "SOS could not be sent online. It remains saved and will retry automatically."
                                )
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

    /**
     * Opens the cancel window after the SOS control is released. The alert is
     * sent through [triggerSos] only when the window ends or the person taps
     * "Send now"; cancelling sends nothing.
     */
    fun startSosCountdown(emergencyTypeCode: String) {
        if (_uiState.value.isSendingSos) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!sosCountdown.start(emergencyTypeCode, now)) return

        val label = _uiState.value.emergencyTypes.firstOrNull {
            it.code.equals(emergencyTypeCode, ignoreCase = true)
        }?.displayName ?: emergencyTypeCode.lowercase().replaceFirstChar { it.titlecase() }

        publishCountdown(emergencyTypeCode, label, now)
        sosCountdownJob?.cancel()
        sosCountdownJob = viewModelScope.launch {
            while (true) {
                val tick = android.os.SystemClock.elapsedRealtime()
                val due = sosCountdown.dueCode(tick)
                if (due != null) {
                    _uiState.update { it.copy(sosCountdown = null) }
                    triggerSos(due)
                    break
                }
                if (!sosCountdown.isPending) break
                publishCountdown(emergencyTypeCode, label, tick)
                delay(100L)
            }
        }
    }

    fun cancelSosCountdown() {
        val cancelled = sosCountdown.cancel() ?: return
        sosCountdownJob?.cancel()
        sosCountdownJob = null
        BleMeshLog.i("SOS_COUNTDOWN_CANCELLED", "emergency" to cancelled)
        _uiState.update {
            it.copy(sosCountdown = null, sosFeedbackMessage = "SOS cancelled. Nothing was sent.")
        }
    }

    fun sendSosNow() {
        val code = sosCountdown.sendNow() ?: return
        sosCountdownJob?.cancel()
        sosCountdownJob = null
        _uiState.update { it.copy(sosCountdown = null) }
        triggerSos(code)
    }

    private fun publishCountdown(code: String, label: String, nowMs: Long) {
        _uiState.update {
            it.copy(
                sosCountdown = SosCountdownUiState(
                    emergencyCode = code,
                    emergencyLabel = label,
                    secondsRemaining = sosCountdown.secondsRemaining(nowMs),
                    progress = sosCountdown.progress(nowMs)
                )
            )
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(sosFeedbackMessage = null) }
    }

    /**
     * Reconciles the local SOS outbox with the shared incident cache.
     *
     * The cache is intentionally *not* a source of ownership: it can contain
     * incidents from every citizen. A tracking session is started only after a
     * persisted QUICK_SOS from this device selects the incident or its local
     * transport token. If that proof disappears or the selected incident is
     * terminal, stop the foreground tracker immediately.
     */
    private fun updateCitizenOwnedIncident() {
        val selection = if (isCitizenAccount) {
            resolveCitizenOwnedSos(
                reports = localQuickSosReports,
                incidents = cachedIncidents
            )
        } else {
            null
        }

        _uiState.update { state ->
            state.copy(activeIncident = selection?.incident)
        }

        // Do not interrupt an in-progress foreground SOS service while Room is
        // still loading the local ownership ledger after a process restart.
        if (!localSosHistoryLoaded || !userRoleLoaded) {
            return
        }

        val nextTrackingId = selection?.trackingId
        if (hasReconciledCitizenTracking && nextTrackingId == trackedCitizenIncidentId) {
            return
        }

        hasReconciledCitizenTracking = true
        trackedCitizenIncidentId = nextTrackingId
        if (nextTrackingId == null) {
            CitizenSosTelemetryService.stopTracking(context)
        } else {
            CitizenSosTelemetryService.startTracking(context, nextTrackingId)
        }
    }

    companion object {
        private const val QUICK_SOS_SUBMISSION_TYPE = "QUICK_SOS"

        /**
         * Citizen SOS needs the current/near-recent incident context, not an unbounded archive.
         */
        private const val CITIZEN_INCIDENT_WORKING_SET = 100

        /** The local SOS ledger is the ownership proof for location tracking. */
        private const val CITIZEN_SOS_HISTORY_WORKING_SET = 200
    }
}

/**
 * The one incident a citizen device may track. This is deliberately derived
 * from a locally persisted QUICK_SOS rather than the global incident list.
 */
internal data class CitizenOwnedSosSelection(
    val trackingId: String,
    val incident: IncidentEntity?
)

/**
 * Selects the most recent persisted SOS from this device.
 *
 * A matching terminal server incident stops tracking; we deliberately do not
 * fall back to an older report because that could restart a stale SOS session.
 * A not-yet-synced report can use its persisted transport token for the
 * mesh-only tracking path, but a synced report without a current incident
 * record is not assumed to still be active.
 */
internal fun resolveCitizenOwnedSos(
    reports: List<LocalIncidentReportEntity>,
    incidents: List<IncidentEntity>
): CitizenOwnedSosSelection? {
    val latestReport = reports
        .asSequence()
        .filter {
            it.submissionType.equals("QUICK_SOS", ignoreCase = true)
        }
        .maxByOrNull { it.observedTimestampMs }
        ?: return null

    val linkedIncident = incidents.firstOrNull { incident ->
        latestReport.matchesIncident(incident)
    }

    if (linkedIncident != null) {
        if (linkedIncident.status.uppercase() in CITIZEN_TERMINAL_INCIDENT_STATUSES) {
            return null
        }

        return CitizenOwnedSosSelection(
            trackingId = linkedIncident.serverIncidentId?.toString()
                ?: linkedIncident.incidentId,
            incident = linkedIncident
        )
    }

    if (latestReport.syncState.uppercase() !in CITIZEN_PENDING_REPORT_STATES) {
        return null
    }

    val persistedTrackingId = latestReport.logicalId
        .takeIf { it.isNotBlank() }
        ?: latestReport.transportToken.takeIf { it.isNotBlank() }
        ?: return null

    return CitizenOwnedSosSelection(
        trackingId = persistedTrackingId,
        incident = null
    )
}

private fun LocalIncidentReportEntity.matchesIncident(
    incident: IncidentEntity
): Boolean {
    val serverIdMatches = serverIncidentId?.let { serverId ->
        incident.serverIncidentId == serverId || incident.incidentId == serverId.toString()
    } ?: false

    return serverIdMatches ||
        transportToken.isNotBlank() && (
            incident.packetHash == transportToken ||
                incident.logicalId == transportToken
            ) ||
        logicalId.isNotBlank() && logicalId == incident.logicalId
}

private val CITIZEN_TERMINAL_INCIDENT_STATUSES =
    setOf("RESOLVED", "CANCELLED", "FALSE_ALARM")

private val CITIZEN_PENDING_REPORT_STATES =
    setOf("PENDING", "PENDING_INTERNET", "QUEUED_OFFLINE", "BLE_ONLY")
