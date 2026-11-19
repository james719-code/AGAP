package com.travelingsalesman.agap.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.dao.CatalogDao
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.data.repository.SubmitReportParams
import com.travelingsalesman.agap.data.repository.SubmitReportResult
import com.travelingsalesman.agap.domain.usecase.SubmitIncidentReportUseCase
import com.travelingsalesman.agap.location.LocationRepository
import com.travelingsalesman.agap.ui.components.DEFAULT_LEGAZPI_BARANGAYS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReportPhase {
    FORM,
    SUBMITTED
}

data class ReportUiState(
    val phase: ReportPhase = ReportPhase.FORM,
    val selectedType: String = "MEDICAL",
    val selectedSubtype: String = "GENERAL",
    val subjectRelation: String = "SELF",
    val immediateDanger: Boolean = false,
    val peopleAffected: Int = 1,
    // A source is unknown until GPS returns a usable fix or the citizen confirms a map pin.
    // This deliberately prevents a visual map fallback from becoming report data.
    val locationSource: LocationSource = LocationSource.UNKNOWN,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val barangayCode: String? = null,
    val isLocationManuallySelected: Boolean = false,
    val description: String = "",
    val isRefreshingLocation: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionResult: SubmitReportResult? = null,
    val submissionError: String? = null,
    /**
     * The report repository persists a local packet before its recoverable transport
     * errors. Once true, this draft must not be sent again with a new token.
     */
    val isReportSavedLocally: Boolean = false,
    val isOnline: Boolean = true,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val cachedBarangays: List<CachedBarangayEntity> = emptyList()
) {
    /** A dispatchable point must be a valid GPS fix or a confirmed manual map pin. */
    val hasConfirmedIncidentLocation: Boolean
        get() = locationSource.isActualIncidentLocationSource() &&
            hasValidIncidentCoordinates(latitude, longitude)

    val canEditDraft: Boolean
        get() = phase == ReportPhase.FORM && !isSubmitting && !isReportSavedLocally

    val canSubmit: Boolean
        get() = canEditDraft && !isRefreshingLocation && hasConfirmedIncidentLocation

    /**
     * GPS acquisition is automatic, so it does not make a report dirty on its own.
     * This only tracks values the citizen can intentionally change in the form.
     */
    val hasUnsavedChanges: Boolean
        get() = phase == ReportPhase.FORM && !isReportSavedLocally && (
            selectedType != "MEDICAL" ||
                selectedSubtype != "GENERAL" ||
                subjectRelation != "SELF" ||
                immediateDanger ||
                peopleAffected != 1 ||
                isLocationManuallySelected ||
                description.isNotBlank()
            )
}

internal fun hasValidIncidentCoordinates(latitude: Double?, longitude: Double?): Boolean {
    return latitude != null &&
        longitude != null &&
        latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
}

private fun LocationSource.isActualIncidentLocationSource(): Boolean {
    return this == LocationSource.GPS || this == LocationSource.MAP_PIN
}

private const val LOCATION_REQUIRED_MESSAGE =
    "Choose a GPS location or place a map pin before sending this report."

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val submitIncidentReportUseCase: SubmitIncidentReportUseCase,
    private val locationRepository: LocationRepository,
    private val networkMonitor: NetworkMonitor,
    private val catalogDao: CatalogDao,
    private val serverConfigManager: ServerConfigManager,
    private val bleMeshManager: BleMeshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            serverConfigManager.transmissionMode.collect { mode ->
                _uiState.update { it.copy(transmissionMode = mode) }
            }
        }
        viewModelScope.launch {
            val barangays = try {
                catalogDao.getAllBarangays().ifEmpty { DEFAULT_LEGAZPI_BARANGAYS }
            } catch (_: Exception) {
                DEFAULT_LEGAZPI_BARANGAYS
            }
            _uiState.update { it.copy(cachedBarangays = barangays) }
        }
        acquireGpsLocation()
    }

    fun setEmergencyType(type: String, subtype: String = "GENERAL") {
        updateDraft { it.copy(selectedType = type, selectedSubtype = subtype) }
    }

    fun setSubjectRelation(relation: String, danger: Boolean) {
        updateDraft { it.copy(subjectRelation = relation, immediateDanger = danger) }
    }

    fun setPeopleAffected(count: Int) {
        updateDraft { it.copy(peopleAffected = count.coerceAtLeast(1)) }
    }

    fun setLocationChoice(
        source: LocationSource,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        barangay: String? = null
    ) {
        if (source != LocationSource.MAP_PIN || !hasValidIncidentCoordinates(lat, lon)) {
            _uiState.update { state ->
                if (state.canEditDraft) state.copy(submissionError = LOCATION_REQUIRED_MESSAGE) else state
            }
            return
        }

        updateDraft {
            it.copy(
                locationSource = source,
                latitude = lat,
                longitude = lon,
                accuracyMeters = accuracy,
                barangayCode = barangay,
                isLocationManuallySelected = true
            )
        }
    }

    fun setDescription(desc: String) {
        updateDraft { it.copy(description = desc) }
    }

    fun acquireGpsLocation() {
        val current = _uiState.value
        if (!current.canEditDraft || current.isRefreshingLocation) return

        _uiState.update { it.copy(isRefreshingLocation = true, submissionError = null) }
        viewModelScope.launch {
            val fix = runCatching {
                locationRepository.getBoundedLocationFix(3000L)
            }.getOrNull()

            val usableFix = fix?.takeIf {
                it.source == LocationSource.GPS &&
                    hasValidIncidentCoordinates(it.latitude, it.longitude)
            }

            _uiState.update { state ->
                when {
                    usableFix != null -> state.copy(
                        locationSource = LocationSource.GPS,
                        latitude = usableFix.latitude,
                        longitude = usableFix.longitude,
                        accuracyMeters = usableFix.accuracyMeters,
                        isLocationManuallySelected = false,
                        isRefreshingLocation = false
                    )

                    // Do not discard an already-confirmed map pin merely because a
                    // fresh GPS request timed out or lost permission.
                    state.hasConfirmedIncidentLocation -> state.copy(
                        isRefreshingLocation = false
                    )

                    else -> state.copy(
                        locationSource = LocationSource.UNKNOWN,
                        latitude = null,
                        longitude = null,
                        accuracyMeters = null,
                        isLocationManuallySelected = false,
                        isRefreshingLocation = false
                    )
                }
            }
        }
    }

    fun submitReport() {
        val current = _uiState.value
        if (current.isSubmitting || current.isReportSavedLocally) return
        if (!current.hasConfirmedIncidentLocation) {
            _uiState.update { it.copy(submissionError = LOCATION_REQUIRED_MESSAGE) }
            return
        }

        // Set this synchronously before launching so rapid double taps cannot
        // create separate locally persisted reports with different tokens.
        _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            val state = _uiState.value

            val params = SubmitReportParams(
                emergencyType = state.selectedType,
                subtype = state.selectedSubtype,
                subjectRelation = state.subjectRelation,
                immediateDanger = state.immediateDanger,
                peopleAffected = state.peopleAffected,
                locationSource = state.locationSource,
                latitude = state.latitude,
                longitude = state.longitude,
                accuracyMeters = state.accuracyMeters,
                barangayCode = state.barangayCode,
                description = state.description,
                isOnline = state.isOnline,
                transmissionMode = state.transmissionMode
            )

            val result = runCatching {
                submitIncidentReportUseCase(params)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionError = error.message ?: "Unable to save the report. Please try again."
                    )
                }
                return@launch
            }

            when (result) {
                is Result.Success -> {
                    if (state.transmissionMode == TransmissionMode.FORCE_BLE_MESH ||
                        (state.transmissionMode == TransmissionMode.AUTO && !state.isOnline)
                    ) {
                        bleMeshManager.requestImmediateRelayPulse()
                    }
                    _uiState.update { 
                        it.copy(
                            isSubmitting = false,
                            submissionResult = result.data,
                            phase = ReportPhase.SUBMITTED
                        ) 
                    }
                }
                is Result.Error -> {
                    // IncidentReportRepository writes the local record before its
                    // recoverable Result.Error paths (for example FORCE_INTERNET
                    // while offline). The queued UplinkFlushWorker owns retries;
                    // keeping this draft terminal avoids a second emergency packet.
                    _uiState.update { 
                        it.copy(
                            isSubmitting = false,
                            submissionError = result.exception.message ?: "Delivery is pending.",
                            isReportSavedLocally = true
                        ) 
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = "Unable to confirm report delivery. Please try again."
                        )
                    }
                }
            }
        }
    }

    private fun updateDraft(transform: (ReportUiState) -> ReportUiState) {
        _uiState.update { state ->
            if (!state.canEditDraft) {
                state
            } else {
                transform(state).copy(submissionError = null)
            }
        }
    }
}
