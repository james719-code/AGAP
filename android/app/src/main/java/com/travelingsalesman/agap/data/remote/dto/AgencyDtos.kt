package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgencyDto(
    @SerialName("id") val id: Long,
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String,
    @SerialName("region_code") val regionCode: String = "",
    @SerialName("province_code") val provinceCode: String = "",
    @SerialName("muni_code") val muniCode: String = "",
    @SerialName("barangay_code") val barangayCode: String = "",
    @SerialName("contact_number") val contactNumber: String = "",
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0
)
