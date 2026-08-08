package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.repository.IncidentReportRepository
import com.travelingsalesman.agap.data.repository.SubmitReportParams
import com.travelingsalesman.agap.data.repository.SubmitReportResult
import javax.inject.Inject

class SubmitIncidentReportUseCase @Inject constructor(
    private val repository: IncidentReportRepository
) {
    suspend operator fun invoke(params: SubmitReportParams): Result<SubmitReportResult> {
        return repository.submitReport(params)
    }
}
