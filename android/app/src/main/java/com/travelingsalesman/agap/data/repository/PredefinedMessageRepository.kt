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

    override suspend fun syncMessages(messages: List<PredefinedMessageDto>) {
        // The server catalog is authoritative. Replacing the local set prevents a
        // reply that was disabled/deleted by an admin from remaining selectable
        // offline forever. This method is only called after a successful server sync.
        predefinedMessageDao.replaceMessages(messages.map { it.toEntity() })
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

        // Operational reply choices are server-owned. Do not silently invent a
        // catalog after a failed first sync: the user must connect once so this
        // installation has the authoritative protocol_code -> message mapping.
        return Result.Error(errorMessage)
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
