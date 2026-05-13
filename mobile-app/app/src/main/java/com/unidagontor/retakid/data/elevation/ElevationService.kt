package com.unidagontor.retakid.data.elevation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class ElevationData(val elevationMeters: Double)

object ElevationService {

    private val cache = mutableMapOf<String, ElevationData>()

    private const val TIMEOUT_MS = 3000

    suspend fun getElevation(latitude: Double, longitude: Double): ElevationData? = withContext(Dispatchers.IO) {
        val cacheKey = "${latitude.toPrecision(4)},${longitude.toPrecision(4)}"

        cache[cacheKey]?.let { return@withContext it }

        runCatching {
            val url = buildString {
                append("https://api.open-meteo.com/v1/elevation")
                append("?latitude=$latitude")
                append("&longitude=$longitude")
            }

            val response = java.net.URL(url).also {
                val conn = it.openConnection()
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
            }.readText()

            val json = JSONObject(response)
            val elevationArray = json.getJSONObject("elevation").getJSONArray("elevation")
            val elevation = elevationArray.getDouble(0)

            ElevationData(elevationMeters = elevation).also {
                cache[cacheKey] = it
            }
        }.getOrNull()
    }

    fun clearCache() {
        cache.clear()
    }

    private fun Double.toPrecision(decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * factor) / factor
    }
}
