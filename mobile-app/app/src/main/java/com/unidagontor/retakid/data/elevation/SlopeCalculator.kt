package com.unidagontor.retakid.data.elevation

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.toDegrees

enum class SlopeCategory(val label: String, val minDeg: Double, val maxDeg: Double) {
    DATAR("Datar", 0.0, 8.0),
    LANDAI("Landai", 8.0, 15.0),
    CURAM("Curam", 15.0, 25.0),
    SANGAT_CURAM("Sangat Curam", 25.0, 90.0);

    companion object {
        fun fromDegrees(degrees: Double): SlopeCategory = entries.first { degrees >= it.minDeg && degrees < it.maxDeg }
    }
}

data class SlopeData(
    val degrees: Double,
    val category: SlopeCategory
) {
    val riskScore: Double get() = when (category) {
        SlopeCategory.DATAR -> 0.1
        SlopeCategory.LANDAI -> 0.4
        SlopeCategory.CURAM -> 0.7
        SlopeCategory.SANGAT_CURAM -> 1.0
    }
}

object SlopeCalculator {

    private const val OFFSET_DEG = 0.0009

    private data class OffsetPoint(val label: String, val latitude: Double, val longitude: Double)

    suspend fun calculateSlope(
        latitude: Double,
        longitude: Double,
        elevationService: suspend (Double, Double) -> ElevationData? = ElevationService::getElevation
    ): SlopeData? {
        val centerElevation = elevationService(latitude, longitude) ?: return null

        val points = listOf(
            OffsetPoint("N", latitude + OFFSET_DEG, longitude),
            OffsetPoint("S", latitude - OFFSET_DEG, longitude),
            OffsetPoint("E", latitude, longitude + OFFSET_DEG),
            OffsetPoint("W", latitude, longitude - OFFSET_DEG)
        )

        val maxGradient = points.mapNotNull { point ->
            val pointElevation = elevationService(point.latitude, point.longitude) ?: return@mapNotNull null
            val elevDiff = abs(pointElevation.elevationMeters - centerElevation.elevationMeters)
            val distanceMeters = OFFSET_DEG * 111_320.0
            val slopeRadians = atan(elevDiff / distanceMeters)
            slopeRadians.toDegrees()
        }.maxOrNull() ?: return null

        val degrees = (maxGradient * 10.0).roundToInt() / 10.0
        val category = SlopeCategory.fromDegrees(degrees)

        return SlopeData(degrees = degrees, category = category)
    }
}
