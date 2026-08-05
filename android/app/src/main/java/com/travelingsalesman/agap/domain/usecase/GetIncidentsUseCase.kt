package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.repository.IncidentRepository
import javax.inject.Inject

class GetIncidentsUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository
) {
    suspend operator fun invoke(
        type: String? = null,
        status: String? = null
    ): Result<List<IncidentEntity>> {
        return incidentRepository.fetchRemoteIncidents(type = type, status = status)
    }
}
