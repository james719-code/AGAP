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
            PredefinedMessageEntity(id = 1, code = "SOS_RECEIVED", protocolCode = 1, messageText = "Emergency distress received by command", messageType = "STATUS"),
            PredefinedMessageEntity(id = 2, code = "HELP_EN_ROUTE", protocolCode = 2, messageText = "Help is en route to your location", messageType = "STATUS"),
            PredefinedMessageEntity(id = 3, code = "RESCUE_DISPATCHED", protocolCode = 3, messageText = "Rescue units dispatched", messageType = "STATUS"),
            PredefinedMessageEntity(id = 4, code = "UNIT_EN_ROUTE", protocolCode = 4, messageText = "Unit en route", messageType = "STATUS"),
            PredefinedMessageEntity(id = 5, code = "UNIT_ARRIVING", protocolCode = 5, messageText = "Unit arriving on scene", messageType = "STATUS"),
            PredefinedMessageEntity(id = 6, code = "INCIDENT_RESOLVED", protocolCode = 6, messageText = "Incident resolved by response team", messageType = "STATUS"),
            PredefinedMessageEntity(id = 7, code = "INCIDENT_CANCELLED", protocolCode = 7, messageText = "Emergency report cancelled", messageType = "STATUS")
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
