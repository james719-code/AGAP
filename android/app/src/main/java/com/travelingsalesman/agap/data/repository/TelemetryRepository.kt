package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.AgapApiService
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface TelemetryRepository {
    suspend fun sendUplink(request: TelemetryUplinkRequest): Result<TelemetryUplinkResponse>
    suspend fun savePendingPacket(packet: PendingPacketEntity)
    suspend fun getPendingPackets(): List<PendingPacketEntity>
    fun getPendingPacketsFlow(): Flow<List<PendingPacketEntity>>
    suspend fun deletePendingPacket(hash: String)
    fun getPendingCount(): Flow<Int>
}

@Singleton
class TelemetryRepositoryImpl @Inject constructor(
    private val apiService: AgapApiService,
    private val pendingPacketDao: PendingPacketDao
) : TelemetryRepository {

    override suspend fun sendUplink(request: TelemetryUplinkRequest): Result<TelemetryUplinkResponse> {
        return try {
            val response = apiService.sendTelemetryUplink(request)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(Exception("HTTP Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun savePendingPacket(packet: PendingPacketEntity) {
        pendingPacketDao.insertPacket(packet)
    }

    override suspend fun getPendingPackets(): List<PendingPacketEntity> {
        return pendingPacketDao.getAllPendingPackets()
    }

    override fun getPendingPacketsFlow(): Flow<List<PendingPacketEntity>> {
        return pendingPacketDao.getAllPendingPacketsFlow()
    }

    override suspend fun deletePendingPacket(hash: String) {
        pendingPacketDao.deleteByHash(hash)
    }

    override fun getPendingCount(): Flow<Int> {
        return pendingPacketDao.getPendingCountFlow()
    }
}
