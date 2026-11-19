package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.remote.IncidentReportApiService
import com.travelingsalesman.agap.data.remote.dto.CreateReportRequestDto
import com.travelingsalesman.agap.data.remote.dto.CreateReportResponseDto
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.core.bluetooth.FakeRelayDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FakeReportDao : ReportDao {
    val reports = mutableListOf<LocalIncidentReportEntity>()

    override suspend fun insertReport(report: LocalIncidentReportEntity) {
        reports.removeAll { it.reportUuid == report.reportUuid }
        reports.add(report)
    }

    override suspend fun getReportByUuid(uuid: String): LocalIncidentReportEntity? =
        reports.find { it.reportUuid == uuid }

    override suspend fun getReportByToken(token: String): LocalIncidentReportEntity? =
        reports.find { it.transportToken == token }

    override suspend fun getReportByLogicalId(logicalId: String): LocalIncidentReportEntity? =
        reports.find { it.logicalId == logicalId }

    override suspend fun getPendingReports(): List<LocalIncidentReportEntity> =
        reports.filter { it.syncState != "SYNCED" }

    override suspend fun getNextPendingReport(): LocalIncidentReportEntity? =
        reports.firstOrNull { it.syncState != "SYNCED" }

    override suspend fun hasPendingReports(): Boolean =
        reports.any { it.syncState != "SYNCED" }

    override fun getAllReportsFlow(): Flow<List<LocalIncidentReportEntity>> =
        flowOf(reports)

    override fun observePendingReportCount(): Flow<Int> =
        flowOf(reports.count { it.syncState != "SYNCED" })

    override fun getRecentLocalSosHistoryFlow(limit: Int): Flow<List<LocalIncidentReportEntity>> =
        flowOf(reports.sortedByDescending { it.observedTimestampMs }.take(limit))

    override suspend fun deleteOldestReportsExceedingLimit(limit: Int): Int {
        if (reports.size <= limit) return 0
        val sorted = reports.sortedByDescending { it.observedTimestampMs }
        val toKeep = sorted.take(limit).map { it.reportUuid }.toSet()
        val initialSize = reports.size
        reports.retainAll { it.reportUuid in toKeep }
        return initialSize - reports.size
    }
}

class FakeIncidentReportApiService : IncidentReportApiService {
    var lastSubmittedDto: CreateReportRequestDto? = null
    var errorCode: Int? = null

    override suspend fun createReport(dto: CreateReportRequestDto): Response<CreateReportResponseDto> {
        lastSubmittedDto = dto
        errorCode?.let { return Response.error(it, "Report was not accepted".toResponseBody()) }
        return Response.success(
            CreateReportResponseDto(
                success = true,
                message = "Report received"
            )
        )
    }
}

class IncidentReportRepositoryTest {

    @Test
    fun submitReport_cloudErrors_keepTheReportPending() = runBlocking {
        for (status in listOf(409, 429, 503)) {
            val reportDao = FakeReportDao()
            val api = FakeIncidentReportApiService().apply { errorCode = status }
            val repository = IncidentReportRepository(reportDao, api, BleRelayScheduler(FakeRelayDao()))

            val result = repository.submitReport(SubmitReportParams(
                emergencyType = "MEDICAL",
                transmissionMode = TransmissionMode.FORCE_INTERNET
            ))

            assertTrue(result is Result.Error)
            assertEquals("PENDING_INTERNET", reportDao.reports.single().syncState)
        }
    }

    @Test
    fun submitReport_online_createsAndSyncsReport() = runBlocking {
        val fakeDao = FakeReportDao()
        val fakeApi = FakeIncidentReportApiService()
        val repository = IncidentReportRepository(fakeDao, fakeApi, BleRelayScheduler(FakeRelayDao()))

        val params = SubmitReportParams(
            emergencyType = "MEDICAL",
            description = "Patient experiencing chest pain",
            locationSource = LocationSource.GPS,
            latitude = 13.1391,
            longitude = 123.7438,
            isOnline = true
        )

        val result = repository.submitReport(params)

        assertTrue(result is Result.Success)
        val successResult = result as Result.Success
        assertEquals(1, fakeDao.reports.size)
        assertEquals("SYNCED", fakeDao.reports.first().syncState)
        assertEquals("MEDICAL", fakeApi.lastSubmittedDto?.emergencyType)
        assertEquals(successResult.data.reportUuid, fakeApi.lastSubmittedDto?.reportUuid)
    }
}
