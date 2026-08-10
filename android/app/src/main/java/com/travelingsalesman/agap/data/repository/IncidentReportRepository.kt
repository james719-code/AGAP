package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketCodec
import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketType
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.remote.IncidentReportApiService
import com.travelingsalesman.agap.data.remote.dto.CreateReportRequestDto
import com.travelingsalesman.agap.domain.model.LocationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
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
    val submissionType: String = "DETAILED",
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO
)

data class SubmitReportResult(
    val reportUuid: String,
    val transportTokenHex: String,
    val message: String,
    val serverIncidentId: Long? = null,
    val logicalId: String = transportTokenHex
)

@Singleton
class IncidentReportRepository @Inject constructor(
    private val reportDao: ReportDao,
    private val apiService: IncidentReportApiService,
    private val relayScheduler: BleRelayScheduler,
    private val authTokenManager: AuthTokenManager? = null
) {
    fun getAllReportsFlow(): Flow<List<LocalIncidentReportEntity>> = reportDao.getAllReportsFlow()

    fun getRecentLocalSosHistoryFlow(limit: Int = 200): Flow<List<LocalIncidentReportEntity>> =
        reportDao.getRecentLocalSosHistoryFlow(limit)

    suspend fun submitReport(params: SubmitReportParams): Result<SubmitReportResult> {
        val reportUuid = UUID.randomUUID().toString()
        val tokenBytes = BleMeshPacketCodec.generateTransportToken()
        val transportTokenHex = tokenBytes.toHex()

        val initialSyncState = when (params.transmissionMode) {
            TransmissionMode.FORCE_BLE_MESH -> "BLE_ONLY"
            TransmissionMode.FORCE_INTERNET -> "PENDING_INTERNET"
            TransmissionMode.AUTO -> if (params.isOnline) "PENDING" else "QUEUED_OFFLINE"
        }

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
            syncState = initialSyncState
        )

        reportDao.insertReport(localReport)
        reportDao.deleteOldestReportsExceedingLimit(200)

        BleMeshLog.i(
            "REPORT_SUBMIT_POLICY",
            "mode" to params.transmissionMode.name,
            "onlineObserved" to params.isOnline,
            "type" to params.submissionType,
            "emergency" to params.emergencyType,
            "token" to BleMeshLog.token(transportTokenHex)
        )

        when (params.transmissionMode) {
            TransmissionMode.FORCE_BLE_MESH -> {
                BleMeshLog.i(
                    "REPORT_FORCE_BLE",
                    "token" to BleMeshLog.token(transportTokenHex),
                    "internetSkipped" to true
                )
                enqueueBlePacket(params, tokenBytes)
                return Result.Success(
                    SubmitReportResult(
                        reportUuid = reportUuid,
                        transportTokenHex = transportTokenHex,
                        message = "No internet. Your SOS is saved and AGAP is automatically searching for a nearby relay."
                    )
                )
            }

            TransmissionMode.FORCE_INTERNET -> {
                BleMeshLog.i(
                    "REPORT_FORCE_INTERNET",
                    "token" to BleMeshLog.token(transportTokenHex),
                    "onlineObserved" to params.isOnline
                )
                if (!params.isOnline) {
                    return Result.Error(IOException("Internet-only mode selected, but no internet connection is available."))
                }

                return tryInternetSubmission(localReport, params, reportUuid, transportTokenHex)
                    ?: Result.Error(IOException("Internet-only submission failed. BLE fallback is disabled in Internet-only mode."))
            }

            TransmissionMode.AUTO -> {
                BleMeshLog.i(
                    "REPORT_AUTO_ROUTE",
                    "token" to BleMeshLog.token(transportTokenHex),
                    "onlineObserved" to params.isOnline
                )
                if (params.isOnline) {
                    val onlineResult = tryInternetSubmission(localReport, params, reportUuid, transportTokenHex)
                    if (onlineResult != null) return onlineResult
                }

                enqueueBlePacket(params, tokenBytes)
                reportDao.insertReport(localReport.copy(syncState = "QUEUED_OFFLINE"))
                return Result.Success(
                    SubmitReportResult(
                        reportUuid = reportUuid,
                        transportTokenHex = transportTokenHex,
                        message = "No internet. Your report is saved and AGAP is automatically searching for a nearby relay."
                    )
                )
            }
        }
    }

    private suspend fun tryInternetSubmission(
        localReport: LocalIncidentReportEntity,
        params: SubmitReportParams,
        reportUuid: String,
        transportTokenHex: String
    ): Result<SubmitReportResult>? {
        return try {
            BleMeshLog.d(
                "REPORT_HTTP_ATTEMPT",
                "token" to BleMeshLog.token(transportTokenHex),
                "mode" to params.transmissionMode.name
            )
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
                BleMeshLog.i(
                    "REPORT_HTTP_SUCCESS",
                    "token" to BleMeshLog.token(transportTokenHex),
                    "code" to response.code()
                )
                reportDao.insertReport(
                    localReport.copy(
                        syncState = "SYNCED",
                        serverIncidentId = response.body()?.report?.incidentId,
                        logicalId = transportTokenHex
                    )
                )
            Result.Success(
                SubmitReportResult(
                    reportUuid = reportUuid,
                    transportTokenHex = transportTokenHex,
                    message = "Emergency command received your report.",
                    serverIncidentId = response.body()?.report?.incidentId
                )
                )
            } else if (response.code() == 409) {
                // The transport token/report UUID was already accepted by the
                // backend. Treat this as terminal success so AUTO mode does not
                // enqueue a second mesh delivery for the same emergency.
                reportDao.insertReport(
                    localReport.copy(
                        syncState = "SYNCED",
                        serverIncidentId = response.body()?.report?.incidentId,
                        logicalId = transportTokenHex
                    )
                )
                Result.Success(
                    SubmitReportResult(
                        reportUuid = reportUuid,
                        transportTokenHex = transportTokenHex,
                        message = "This SOS was already received online; no duplicate was created.",
                        serverIncidentId = response.body()?.report?.incidentId
                    )
                )
            } else {
                BleMeshLog.w(
                    "REPORT_HTTP_REJECTED",
                    "token" to BleMeshLog.token(transportTokenHex),
                    "code" to response.code()
                )
                null
            }
        } catch (e: Exception) {
            BleMeshLog.w(
                "REPORT_HTTP_FAILED",
                "token" to BleMeshLog.token(transportTokenHex),
                "errorType" to e::class.java.simpleName,
                error = e
            )
            null
        }
    }

    private suspend fun enqueueBlePacket(params: SubmitReportParams, tokenBytes: ByteArray) {
        val packetType = if (params.submissionType == "QUICK_SOS") {
            BleMeshPacketType.SOS
        } else {
            BleMeshPacketType.INCIDENT_REPORT
        }

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
            horizontalAccuracyMeters = (params.accuracyMeters ?: 255f).toInt(),
            originDeviceId = authTokenManager?.deviceId?.firstOrNull()
        )

        val packet = BleMeshPacketCodec.decode(emergencyBytes) ?: run {
            BleMeshLog.e("REPORT_BLE_ENCODE_DECODE_FAILED", "bytes" to emergencyBytes.size)
            return
        }
        val queueId = relayScheduler.enqueueForRelay(packet, emergencyBytes, isOriginLocal = true)
        BleMeshLog.i(
            "REPORT_BLE_QUEUED",
            "queueId" to queueId,
            "token" to BleMeshLog.token(packet.transportTokenHex),
            "type" to packet.packetType.name,
            "bytes" to emergencyBytes.size
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
