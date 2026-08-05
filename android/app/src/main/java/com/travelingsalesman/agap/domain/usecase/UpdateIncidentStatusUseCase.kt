package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.repository.IncidentRepository
import javax.inject.Inject

class UpdateIncidentStatusUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository
) {
    suspend operator fun invoke(incidentId: Long, newStatus: String): Result<Boolean> {
        return incidentRepository.updateStatus(incidentId, newStatus)
    }
}
