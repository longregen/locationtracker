package com.locationtracker

data class LocationData(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val firstVisit: Long,
    val visitCount: Int,
    val accuracy: Float? = null,
    val name: String? = null
)

data class RawLocationData(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float? = null
)
