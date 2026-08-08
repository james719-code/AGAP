package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import com.travelingsalesman.agap.data.repository.SubmitReportParams
import com.travelingsalesman.agap.domain.model.LocationSource
import javax.inject.Inject

class SendSosUseCase @Inject constructor(
    private val submitIncidentReportUseCase: SubmitIncidentReportUseCase
) {
    suspend operator fun invoke(
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean,
        forcedMode: TransmissionMode? = null
    ): Result<TelemetryUplinkResponse> {
        val params = SubmitReportParams(
            emergencyType = emergencyType,
            submissionType = "QUICK_SOS",
            subjectRelation = "SELF",
            immediateDanger = true,
            locationSource = LocationSource.GPS,
            latitude = latitude,
            longitude = longitude,
            isOnline = isOnline
        )

        return when (val res = submitIncidentReportUseCase(params)) {
            is Result.Success -> Result.Success(
                TelemetryUplinkResponse(
                    success = true,
                    message = res.data.message,
                    incidentId = res.data.reportUuid
                )
            )
            is Result.Error -> Result.Error(res.exception)
            is Result.Loading -> Result.Loading
        }
    }
}
