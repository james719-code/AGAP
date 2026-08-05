package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.repository.ResponderRepository
import javax.inject.Inject

class GetRespondersUseCase @Inject constructor(
    private val responderRepository: ResponderRepository
) {
    suspend operator fun invoke(agencyId: Long? = null): Result<List<ResponderUnitDto>> {
        return responderRepository.getResponders(agencyId)
    }
}
