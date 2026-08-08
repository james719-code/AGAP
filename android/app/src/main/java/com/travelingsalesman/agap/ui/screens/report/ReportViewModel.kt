package com.travelingsalesman.agap.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.domain.usecase.SendSosUseCase
import com.travelingsalesman.agap.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val currentStep: Int = 1,
    val selectedType: String = "MEDICAL",
    val selectedSubtype: String = "GENERAL",
    val subjectRelation: String = "SELF",
    val immediateDanger: Boolean = false,
    val peopleAffected: Int = 1,
    val locationSource: LocationSource = LocationSource.GPS,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val barangayCode: String? = null,
    val description: String = "",
    val isSubmitting: Boolean = false,
    val submissionResult: String? = null,
    val submissionError: String? = null,
    val isOnline: Boolean = true
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val sendSosUseCase: SendSosUseCase,
    private val locationRepository: LocationRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        acquireGpsLocation()
    }

    fun setStep(step: Int) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun nextStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(5)) }
    }

    fun prevStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) }
    }

    fun setEmergencyType(type: String, subtype: String = "GENERAL") {
        _uiState.update { it.copy(selectedType = type, selectedSubtype = subtype) }
    }

    fun setSubjectRelation(relation: String, danger: Boolean) {
        _uiState.update { it.copy(subjectRelation = relation, immediateDanger = danger) }
    }

    fun setPeopleAffected(count: Int) {
        _uiState.update { it.copy(peopleAffected = count.coerceAtLeast(1)) }
    }

    fun setLocationChoice(source: LocationSource, lat: Double?, lon: Double?, accuracy: Float?, barangay: String? = null) {
        _uiState.update { 
            it.copy(
                locationSource = source,
                latitude = lat,
                longitude = lon,
                accuracyMeters = accuracy,
                barangayCode = barangay
            ) 
        }
    }

    fun setDescription(desc: String) {
        _uiState.update { it.copy(description = desc) }
    }

    fun acquireGpsLocation() {
        viewModelScope.launch {
            val loc = locationRepository.getLastKnownLocation()
            if (loc != null) {
                _uiState.update { 
                    it.copy(
                        locationSource = LocationSource.GPS,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy
                    )
                }
            }
        }
    }

    fun submitReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
            val state = _uiState.value
            val lat = state.latitude ?: 0.0
            val lon = state.longitude ?: 0.0

            val result = sendSosUseCase(
                emergencyType = state.selectedType,
                latitude = lat,
                longitude = lon,
                isOnline = state.isOnline
            )

            when (result) {
                is Result.Success -> {
                    _uiState.update { 
                        it.copy(
                            isSubmitting = false,
                            submissionResult = result.data.message ?: "Report submitted successfully!",
                            currentStep = 6
                        ) 
                    }
                }
                is Result.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSubmitting = false,
                            submissionError = result.exception.message ?: "Failed to submit report"
                        ) 
                    }
                }
                else -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }
}
