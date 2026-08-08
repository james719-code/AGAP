package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketCodec
import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketType
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.remote.IncidentReportApiService
import com.travelingsalesman.agap.data.remote.dto.CreateReportRequestDto
import com.travelingsalesman.agap.domain.model.LocationSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SubmitReportParams(
    val emergencyType: String,
    val subtype: String = "GENERAL",
    val subjectRelation: String = "SELF",
    val immediateDanger: Boolean = false,
    val peopleAffected: Int = 1,
    val locationSource: LocationSource = LocationSource.GPS,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val barangayCode: String? = null,
    val description: String = "",
    val isOnline: Boolean = true,
    val submissionType: String = "DETAILED"
)

data class SubmitReportResult(
    val reportUuid: String,
    val transportTokenHex: String,
    val message: String
)

@Singleton
class IncidentReportRepository @Inject constructor(
    private val reportDao: ReportDao,
    private val apiService: IncidentReportApiService,
    private val relayScheduler: BleRelayScheduler
) {
    fun getAllReportsFlow(): Flow<List<LocalIncidentReportEntity>> = reportDao.getAllReportsFlow()

    suspend fun submitReport(params: SubmitReportParams): Result<SubmitReportResult> {
        val reportUuid = UUID.randomUUID().toString()
        val tokenBytes = BleMeshPacketCodec.generateTransportToken()
        val transportTokenHex = tokenBytes.joinToString("") { "%02x".format(it) }

        val localReport = LocalIncidentReportEntity(
            reportUuid = reportUuid,
            transportToken = transportTokenHex,
            incidentUuid = null,
            submissionType = params.submissionType,
            subjectRelation = params.subjectRelation,
            emergencyType = params.emergencyType,
            subtype = params.subtype,
            description = params.description,
            peopleAffected = params.peopleAffected,
            immediateDanger = params.immediateDanger,
            locationSource = params.locationSource.name,
            latitude = params.latitude,
            longitude = params.longitude,
            gpsAccuracyMeters = params.accuracyMeters,
            selectedBarangayCode = params.barangayCode,
            observedTimestampMs = System.currentTimeMillis(),
            syncState = if (params.isOnline) "PENDING" else "QUEUED_OFFLINE"
        )

        reportDao.insertReport(localReport)

        if (params.isOnline) {
            try {
                val dto = CreateReportRequestDto(
                    reportUuid = reportUuid,
                    transportToken = transportTokenHex,
                    submissionType = params.submissionType,
                    subjectRelation = params.subjectRelation,
                    emergencyType = params.emergencyType,
                    subtype = params.subtype,
                    description = params.description,
                    peopleAffected = params.peopleAffected,
                    immediateDanger = params.immediateDanger,
                    locationSource = params.locationSource.name,
                    selectedBarangayCode = params.barangayCode,
                    latitude = params.latitude,
                    longitude = params.longitude,
                    gpsAccuracyMeters = params.accuracyMeters,
                    originChannel = "CELLULAR"
                )

                val response = apiService.createReport(dto)
                if (response.isSuccessful && response.body()?.success == true) {
                    reportDao.insertReport(localReport.copy(syncState = "SYNCED"))
                    return Result.Success(
                        SubmitReportResult(
                            reportUuid = reportUuid,
                            transportTokenHex = transportTokenHex,
                            message = response.body()?.message ?: "Incident Report submitted successfully!"
                        )
                    )
                }
            } catch (e: Exception) {
                // Network error fallback to offline relay
            }
        }

        // Offline BLE Relay Transmission
        val packetType = if (params.submissionType == "QUICK_SOS") BleMeshPacketType.SOS else BleMeshPacketType.INCIDENT_REPORT
        val emergencyBytes = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = packetType,
            emergencyType = params.emergencyType,
            immediateDanger = params.immediateDanger,
            subjectRelation = params.subjectRelation,
            hopCount = 0,
            locationSource = params.locationSource,
            transportToken = tokenBytes,
            latitude = params.latitude ?: 0.0,
            longitude = params.longitude ?: 0.0,
            horizontalAccuracyMeters = (params.accuracyMeters ?: 255f).toInt()
        )

        val packet = BleMeshPacketCodec.decode(emergencyBytes)
        if (packet != null) {
            relayScheduler.enqueueForRelay(packet, emergencyBytes, isOriginLocal = true)
        }

        return Result.Success(
            SubmitReportResult(
                reportUuid = reportUuid,
                transportTokenHex = transportTokenHex,
                message = "Report stored locally and enqueued for BLE relay broadcast."
            )
        )
    }
}
