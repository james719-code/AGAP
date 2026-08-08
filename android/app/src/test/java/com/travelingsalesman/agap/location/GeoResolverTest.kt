package com.travelingsalesman.agap.location

import com.travelingsalesman.agap.data.local.dao.CatalogDao
import com.travelingsalesman.agap.data.local.entity.CachedAgencyServiceAreaEntity
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.data.local.entity.CachedCatalogVersionEntity
import com.travelingsalesman.agap.data.local.entity.CachedEmergencySubtypeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoResolverTest {

    @Test
    fun resolveNearestBarangay_returnsClosestBarangayWithPsgcCode() = runBlocking {
        val brgy1 = CachedBarangayEntity(
            "PH-ALB-LEG-001",
            "Barangay 1 - Em's Barrio",
            representativeLatitudeE7 = 131391000,
            representativeLongitudeE7 = 1237438000,
            boundaryGeoJson = "{\"type\":\"Polygon\",\"coordinates\":[[[123.74,13.135],[123.745,13.135],[123.745,13.14],[123.74,13.14],[123.74,13.135]]]}"
        )
        val brgy2 = CachedBarangayEntity(
            "PH-ALB-LEG-002",
            "Barangay 2 - Bitano",
            representativeLatitudeE7 = 131430000,
            representativeLongitudeE7 = 1237450000,
            boundaryGeoJson = "{\"type\":\"Polygon\",\"coordinates\":[[[123.74,13.14],[123.75,13.14],[123.75,13.147],[123.74,13.147],[123.74,13.14]]]}"
        )

        val fakeDao = object : CatalogDao {
            override suspend fun insertCatalogVersion(version: CachedCatalogVersionEntity) {}
            override suspend fun getCatalogVersion(name: String): CachedCatalogVersionEntity? = null
            override suspend fun insertBarangays(barangays: List<CachedBarangayEntity>) {}
            override suspend fun getAllBarangays(): List<CachedBarangayEntity> = listOf(brgy1, brgy2)
            override suspend fun getBarangayByCode(code: String): CachedBarangayEntity? = if (code == "PH-ALB-LEG-001") brgy1 else brgy2
            override suspend fun insertEmergencySubtypes(subtypes: List<CachedEmergencySubtypeEntity>) {}
            override suspend fun getSubtypesForType(typeCode: String): List<CachedEmergencySubtypeEntity> = emptyList()
            override suspend fun insertServiceAreas(areas: List<CachedAgencyServiceAreaEntity>) {}
            override suspend fun getPrimaryAgenciesForBarangay(brgyCode: String): List<String> = emptyList()
        }

        val resolver = GeoResolver(fakeDao)
        val nearest = resolver.resolveNearestBarangay(13.1392, 123.7439)

        assertEquals("PH-ALB-LEG-001", nearest?.barangayCode)
    }
}
