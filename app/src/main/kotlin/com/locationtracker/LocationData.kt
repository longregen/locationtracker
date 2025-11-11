package com.locationtracker

data class LocationData(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val firstVisit: Long,
    val visitCount: Int
)
