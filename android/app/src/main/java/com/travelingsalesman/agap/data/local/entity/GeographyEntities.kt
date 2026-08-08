package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_barangays")
data class CachedBarangayEntity(
    @PrimaryKey
    val barangayCode: String,
    val barangayName: String,
    val cityMunicipality: String = "LEGAZPI",
    val province: String = "ALBAY",
    val representativeLatitudeE7: Int,
    val representativeLongitudeE7: Int,
    val boundaryGeoJson: String? = null
)
