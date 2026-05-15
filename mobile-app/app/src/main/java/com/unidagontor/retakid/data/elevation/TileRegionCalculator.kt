package com.unidagontor.retakid.data.elevation

import kotlin.math.floor

object TileRegionCalculator {

    fun tilesForLocation(latitude: Double, longitude: Double): List<String> {
        val latSouth = floor(latitude).toInt()
        val lonWest = floor(longitude).toInt()
        val tiles = mutableListOf<String>()
        for (i in 0..1) {
            val tileLat = latSouth + i
            for (j in 0..1) {
                val tileLon = lonWest + j
                tiles.add(HgtElevationSource.formatTileName(tileLat, tileLon))
            }
        }
        return tiles.toList()
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String {
        val latDir = if (latitude < 0) "S" else "N"
        val lonDir = if (longitude < 0) "B" else "T"
        val latStr = "${absToDegMin(latitude)}${latDir}"
        val lonStr = "${absToDegMin(longitude)}${lonDir}"
        return "$latStr, $lonStr"
    }

    fun estimateKabupaten(latitude: Double, longitude: Double): String {
        return when {
            latitude in -8.5..-7.5 && longitude in 111.0..112.0 -> "Ponorogo, Jawa Timur"
            latitude in -7.5..-6.5 && longitude in 110.5..111.5 -> "Rembang, Jawa Tengah"
            latitude in -8.0..-7.0 && longitude in 110.0..111.0 -> "Wonogiri, Jawa Tengah"
            latitude in -7.0..-6.0 && longitude in 106.0..107.0 -> "Bogor, Jawa Barat"
            latitude in -8.5..-7.5 && longitude in 112.0..113.0 -> "Malang, Jawa Timur"
            else -> "Indonesia"
        }
    }

    private fun absToDegMin(value: Double): String {
        val abs = kotlin.math.abs(value)
        val deg = abs.toInt()
        val min = ((abs - deg) * 60).toInt()
        return "${deg}°${min}'"
    }
}
