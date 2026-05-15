package com.unidagontor.retakid.data.elevation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

data class ElevationData(val elevationMeters: Double)

object ElevationService {

    private val cache = mutableMapOf<String, ElevationData>()
    private const val TIMEOUT_MS = 3000
    private var initialized = false

    fun initFromAssets(context: Context) {
        if (initialized) return
        try {
            val assetFiles = context.assets.list("dem") ?: emptyArray()
            val hgtFile = assetFiles.firstOrNull { it.endsWith(".hgt", ignoreCase = true) }
            if (hgtFile != null) {
                HgtElevationSource.loadFromAssets(context.assets, hgtFile)
            }

            val demDir = File(context.filesDir, "dem")
            HgtElevationSource.loadFromDirectory(demDir)
        } catch (_: Exception) { }
        initialized = true
    }

    suspend fun getElevation(latitude: Double, longitude: Double): ElevationData? = withContext(Dispatchers.IO) {
        val cacheKey = "${latitude.toPrecision(4)},${longitude.toPrecision(4)}"

        cache[cacheKey]?.let { return@withContext it }

        val hgtResult = HgtElevationSource.getElevation(latitude, longitude)
        if (hgtResult != null) {
            return@withContext ElevationData(elevationMeters = hgtResult).also {
                cache[cacheKey] = it
            }
        }

        val onlineResult = getElevationFromApi(latitude, longitude)
        if (onlineResult != null) {
            cache[cacheKey] = onlineResult
        }
        onlineResult
    }

    private fun getElevationFromApi(latitude: Double, longitude: Double): ElevationData? {
        return runCatching {
            val url = buildString {
                append("https://api.open-meteo.com/v1/elevation")
                append("?latitude=$latitude")
                append("&longitude=$longitude")
            }

            val conn = java.net.URL(url).openConnection()
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val response = conn.getInputStream().bufferedReader().readText()

            val json = JSONObject(response)
            val elevationArray = json.getJSONObject("elevation").getJSONArray("elevation")
            val elevation = elevationArray.getDouble(0)

            ElevationData(elevationMeters = elevation)
        }.getOrNull()
    }

    fun clearCache() {
        cache.clear()
        HgtElevationSource.clear()
    }

    private fun Double.toPrecision(decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * factor) / factor
    }
}
