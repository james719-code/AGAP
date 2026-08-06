package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.dao.PredefinedMessageDao
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface PredefinedMessageRepository {
    fun getMessagesFlow(): Flow<List<PredefinedMessageEntity>>
    suspend fun getMessageByCode(code: String): PredefinedMessageEntity?
    suspend fun seedDefaultMessages()
}

@Singleton
class PredefinedMessageRepositoryImpl @Inject constructor(
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
            PredefinedMessageEntity(1, "MEDICAL_EMERGENCY", "Immediate medical assistance required"),
            PredefinedMessageEntity(2, "FIRE_DISASTER", "Fire emergency reported in area"),
            PredefinedMessageEntity(3, "TYPHOON_EVACUATE", "Typhoon alert: Evacuate immediately"),
            PredefinedMessageEntity(4, "VOLCANO_ASHFALL", "Mayon volcano ashfall / eruption alert"),
            PredefinedMessageEntity(5, "FLOOD_RESCUE", "Severe flooding: Water rescue needed"),
            PredefinedMessageEntity(6, "TRAFFIC_ACCIDENT", "Vehicular accident requiring response"),
            PredefinedMessageEntity(7, "STATUS_SAFE", "I am safe and accounted for")
        )
        predefinedMessageDao.insertMessages(defaults)
    }
}
