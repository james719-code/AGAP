package com.travelingsalesman.agap.location

import com.travelingsalesman.agap.domain.model.LocationSource

data class EmergencyLocationFix(
    val source: LocationSource,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val timestampMillis: Long?,
    val barangayCode: String? = null
)
