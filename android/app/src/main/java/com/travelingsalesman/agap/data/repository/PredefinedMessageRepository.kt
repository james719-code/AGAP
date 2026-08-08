package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.PredefinedMessageDao
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.remote.MessageApiService
import com.travelingsalesman.agap.data.remote.dto.PredefinedMessageDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface PredefinedMessageRepository {
    fun getMessagesFlow(): Flow<List<PredefinedMessageEntity>>
    suspend fun getMessageByCode(code: String): PredefinedMessageEntity?
    suspend fun seedDefaultMessages()
    suspend fun syncMessages(messages: List<PredefinedMessageDto>)
    suspend fun syncCatalog(): Result<List<PredefinedMessageDto>>
}

@Singleton
class PredefinedMessageRepositoryImpl @Inject constructor(
    private val messageApiService: MessageApiService,
    private val predefinedMessageDao: PredefinedMessageDao
) : PredefinedMessageRepository {

    override fun getMessagesFlow(): Flow<List<PredefinedMessageEntity>> {
        return predefinedMessageDao.getAllMessagesFlow()
    }

    override suspend fun getMessageByCode(code: String): PredefinedMessageEntity? {
        return predefinedMessageDao.getMessageByCode(code)
    }

    override suspend fun seedDefaultMessages() {
        val defaults = listOf(
            PredefinedMessageEntity(id = 1, code = "MEDICAL_EMERGENCY", protocolCode = 1, messageText = "Immediate medical assistance required", messageType = "SOS", emergencyTypeId = 1),
            PredefinedMessageEntity(id = 2, code = "FIRE_DISASTER", protocolCode = 2, messageText = "Fire emergency reported in area", messageType = "SOS", emergencyTypeId = 3),
            PredefinedMessageEntity(id = 3, code = "TYPHOON_EVACUATE", protocolCode = 3, messageText = "Typhoon alert: Evacuate immediately", messageType = "SOS", emergencyTypeId = 4),
            PredefinedMessageEntity(id = 4, code = "VOLCANO_ASHFALL", protocolCode = 4, messageText = "Mayon volcano ashfall / eruption alert", messageType = "SOS", emergencyTypeId = 4),
            PredefinedMessageEntity(id = 5, code = "FLOOD_RESCUE", protocolCode = 5, messageText = "Severe flooding: Water rescue needed", messageType = "SOS", emergencyTypeId = 4),
            PredefinedMessageEntity(id = 6, code = "TRAFFIC_ACCIDENT", protocolCode = 6, messageText = "Vehicular accident requiring response", messageType = "SOS", emergencyTypeId = 2),
            PredefinedMessageEntity(id = 7, code = "STATUS_SAFE", protocolCode = 7, messageText = "I am safe and accounted for", messageType = "STATUS")
        )
        predefinedMessageDao.insertMessages(defaults)
    }

    override suspend fun syncMessages(messages: List<PredefinedMessageDto>) {
        if (messages.isNotEmpty()) {
            predefinedMessageDao.insertMessages(messages.map { it.toEntity() })
        }
    }

    override suspend fun syncCatalog(): Result<List<PredefinedMessageDto>> {
        return try {
            val response = messageApiService.getCatalog()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                syncMessages(dtos)
                Result.Success(dtos)
            } else {
                cachedCatalogFallback("Failed to fetch message catalog: ${response.code()}")
            }
        } catch (e: Exception) {
            cachedCatalogFallback("Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun cachedCatalogFallback(errorMessage: String): Result<List<PredefinedMessageDto>> {
        val cached = predefinedMessageDao.getAllMessages()
        if (cached.isNotEmpty()) {
            return Result.Success(cached.map { it.toDto() })
        }

        seedDefaultMessages()
        val seeded = predefinedMessageDao.getAllMessages()
        return if (seeded.isNotEmpty()) {
            Result.Success(seeded.map { it.toDto() })
        } else {
            Result.Error(errorMessage)
        }
    }

    private fun PredefinedMessageDto.toEntity(): PredefinedMessageEntity {
        return PredefinedMessageEntity(
            id = id,
            code = code,
            protocolCode = protocolCode,
            messageText = messageEn,
            messageFil = messageFil,
            messageBikol = messageBikol,
            messageType = messageType,
            emergencyTypeId = emergencyTypeId,
            isActive = isActive,
            serverUpdatedAt = updatedAt
        )
    }

    private fun PredefinedMessageEntity.toDto(): PredefinedMessageDto {
        return PredefinedMessageDto(
            id = id,
            code = code,
            protocolCode = protocolCode,
            messageEn = messageText,
            messageFil = messageFil,
            messageBikol = messageBikol,
            messageType = messageType,
            emergencyTypeId = emergencyTypeId,
            isActive = isActive,
            updatedAt = serverUpdatedAt
        )
    }
}
