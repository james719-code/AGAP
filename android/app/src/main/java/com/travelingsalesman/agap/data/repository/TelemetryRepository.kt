package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.AgapApiService
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import com.travelingsalesman.agap.data.remote.dto.MeshDeliveryEventUploadDto
import com.travelingsalesman.agap.data.remote.dto.MeshDeliveryEventsBatchRequestDto
import com.travelingsalesman.agap.data.remote.dto.SyncBootstrapDto
import com.travelingsalesman.agap.data.remote.dto.SyncChangesDto
import com.travelingsalesman.agap.data.remote.dto.SyncPushRequestDto
import com.travelingsalesman.agap.data.remote.dto.SyncPushResultDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface TelemetryRepository {
    suspend fun sendUplink(request: TelemetryUplinkRequest): Result<TelemetryUplinkResponse>
    suspend fun savePendingPacket(packet: PendingPacketEntity)
    suspend fun getPendingPackets(): List<PendingPacketEntity>
    suspend fun getNextPendingPacket(): PendingPacketEntity?
    suspend fun hasPendingPackets(): Boolean
    fun getPendingPacketsFlow(): Flow<List<PendingPacketEntity>>
    fun getRecentPendingPacketsFlow(
        limit: Int
    ): Flow<List<PendingPacketEntity>>
    suspend fun deletePendingPacket(hash: String)
    suspend fun recordMeshDeliveryEvent(event: MeshDeliveryEventEntity)
    fun getMeshDeliveryEventsFlow(): Flow<List<MeshDeliveryEventEntity>>
    fun getRecentSuccessfullyRelayedPacketHashesFlow(limit: Int): Flow<List<String>>
    suspend fun markMeshEventsSynced(ids: List<Long>)
    suspend fun flushUnsyncedMeshDeliveryEvents(): Result<Int>
    suspend fun getSyncBootstrap(): Result<SyncBootstrapDto>
    suspend fun pushSync(request: SyncPushRequestDto): Result<SyncPushResultDto>
    suspend fun getSyncChanges(since: String): Result<SyncChangesDto>
    fun getPendingCount(): Flow<Int>
}

@Singleton
class TelemetryRepositoryImpl @Inject constructor(
    private val apiService: AgapApiService,
    private val pendingPacketDao: PendingPacketDao,
    private val meshDeliveryEventDao: MeshDeliveryEventDao
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
        meshDeliveryEventDao.insertEvent(
            MeshDeliveryEventEntity(
                packetHash = packet.packetHash,
                packetType = "SOS",
                eventType = "CREATED",
                latitude = packet.latitude,
                longitude = packet.longitude,
                hopCount = packet.hopCount
            )
        )
    }

    override suspend fun getPendingPackets(): List<PendingPacketEntity> {
        return pendingPacketDao.getAllPendingPackets()
    }

    override suspend fun getNextPendingPacket(): PendingPacketEntity? {
        return pendingPacketDao.getNextPendingPacket()
    }

    override suspend fun hasPendingPackets(): Boolean {
        return pendingPacketDao.hasPendingPackets()
    }

    override fun getPendingPacketsFlow(): Flow<List<PendingPacketEntity>> {
        return pendingPacketDao.getAllPendingPacketsFlow()
    }

    override fun getRecentPendingPacketsFlow(
        limit: Int
    ): Flow<List<PendingPacketEntity>> {
        return pendingPacketDao
            .getRecentPendingPacketsFlow(
                limit.coerceIn(1, 2_000)
            )
    }

    override suspend fun deletePendingPacket(hash: String) {
        pendingPacketDao.deleteByHash(hash)
    }

    override suspend fun recordMeshDeliveryEvent(event: MeshDeliveryEventEntity) {
        meshDeliveryEventDao.insertEvent(event)
    }

    override fun getMeshDeliveryEventsFlow(): Flow<List<MeshDeliveryEventEntity>> {
        return meshDeliveryEventDao.getAllEventsFlow()
    }

    override fun getRecentSuccessfullyRelayedPacketHashesFlow(limit: Int): Flow<List<String>> {
        return meshDeliveryEventDao.observeRecentSuccessfullyRelayedPacketHashes(
            limit.coerceIn(1, 2_000)
        )
    }

    override suspend fun markMeshEventsSynced(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            meshDeliveryEventDao.markSynced(ids)
        }
    }

    override suspend fun flushUnsyncedMeshDeliveryEvents(): Result<Int> {
        return try {
            val events = meshDeliveryEventDao.getUnsyncedEvents(limit = 200)
            if (events.isEmpty()) {
                return Result.Success(0)
            }

            val response = apiService.sendMeshDeliveryEvents(
                MeshDeliveryEventsBatchRequestDto(events.map { it.toUploadDto() })
            )
            if (response.isSuccessful) {
                markMeshEventsSynced(events.map { it.id })
                Result.Success(response.body()?.recorded ?: events.size)
            } else {
                Result.Error("Failed to upload mesh delivery events: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getSyncBootstrap(): Result<SyncBootstrapDto> {
        return try {
            val response = apiService.getSyncBootstrap()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to fetch sync bootstrap: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun pushSync(request: SyncPushRequestDto): Result<SyncPushResultDto> {
        return try {
            val response = apiService.pushSync(request)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to push sync payload: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getSyncChanges(since: String): Result<SyncChangesDto> {
        return try {
            val response = apiService.getSyncChanges(since)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to fetch sync changes: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun getPendingCount(): Flow<Int> {
        return pendingPacketDao.getPendingCountFlow()
    }

    private fun MeshDeliveryEventEntity.toUploadDto(): MeshDeliveryEventUploadDto {
        return MeshDeliveryEventUploadDto(
            packetHash = packetHash,
            packetType = packetType,
            eventType = eventType,
            deviceIdentifier = deviceIdentifier,
            latitude = latitude,
            longitude = longitude,
            hopCount = hopCount,
            recordedAt = Instant.ofEpochMilli(recordedAt).toString()
        )
    }
}