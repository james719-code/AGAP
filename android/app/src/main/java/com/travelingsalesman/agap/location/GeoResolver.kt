package com.travelingsalesman.agap.location

import com.travelingsalesman.agap.data.local.dao.CatalogDao
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

@Singleton
class GeoResolver @Inject constructor(
    private val catalogDao: CatalogDao
) {
    suspend fun resolveNearestBarangay(latitude: Double, longitude: Double): CachedBarangayEntity? {
        val barangays = catalogDao.getAllBarangays()
        if (barangays.isEmpty()) return null

        val latE7 = (latitude * 1e7).toInt()
        val lngE7 = (longitude * 1e7).toInt()

        return barangays.minByOrNull { brgy ->
            val dLat = (brgy.representativeLatitudeE7 - latE7).toDouble()
            val dLng = (brgy.representativeLongitudeE7 - lngE7).toDouble()
            hypot(dLat, dLng)
        }
    }
}
