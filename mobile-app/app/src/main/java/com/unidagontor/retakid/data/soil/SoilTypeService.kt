package com.unidagontor.retakid.data.soil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class SoilRisk(val label: String, val riskNote: String) {
    RENDAH("Rendah", "Tanah berpasir — retakan kecil"),
    SEDANG("Sedang", "Tanah lempung — retakan mungkin terjadi"),
    TINGGI("Tinggi", "Tanah liat — rawan retak & mengembang"),
    SANGAT_TINGGI("Sangat Tinggi", "Tanah ekspansif — retakan besar & dalam"),
    TIDAK_DIKETAHUI("Tidak Diketahui", "Data tanah tidak tersedia")
}

data class SoilType(
    val name: String,
    val indonesianName: String,
    val risk: SoilRisk
) {
    val riskScore: Double get() = when (risk) {
        SoilRisk.RENDAH -> 0.2
        SoilRisk.SEDANG -> 0.5
        SoilRisk.TINGGI -> 0.8
        SoilRisk.SANGAT_TINGGI -> 1.0
        SoilRisk.TIDAK_DIKETAHUI -> 0.3
    }
}

object SoilTypeService {

    private val cache = mutableMapOf<String, SoilType>()
    private const val TIMEOUT_MS = 5000

    private val wrbRiskMap = mapOf(
        "Vertisols" to SoilRisk.SANGAT_TINGGI,
        "Planosols" to SoilRisk.SANGAT_TINGGI,
        "Acrisols" to SoilRisk.TINGGI,
        "Lixisols" to SoilRisk.TINGGI,
        "Nitisols" to SoilRisk.TINGGI,
        "Alisols" to SoilRisk.TINGGI,
        "Luvisols" to SoilRisk.TINGGI,
        "Cambisols" to SoilRisk.SEDANG,
        "Ferralsols" to SoilRisk.SEDANG,
        "Fluvisols" to SoilRisk.SEDANG,
        "Leptosols" to SoilRisk.SEDANG,
        "Regosols" to SoilRisk.SEDANG,
        "Umbrisols" to SoilRisk.SEDANG,
        "Andosols" to SoilRisk.SEDANG,
        "Arenosols" to SoilRisk.RENDAH,
        "Podzols" to SoilRisk.RENDAH,
        "Gleysols" to SoilRisk.RENDAH,
        "Histosols" to SoilRisk.RENDAH
    )

    private val wrbIndonesianNames = mapOf(
        "Vertisols" to "Tanah Liat Ekspansif",
        "Planosols" to "Tanah Liat Planosol",
        "Acrisols" to "Tanah Liat Merah",
        "Lixisols" to "Tanah Liat Berpasir",
        "Nitisols" to "Tanah Liat Nitrosol",
        "Alisols" to "Tanah Liat Alisol",
        "Luvisols" to "Tanah Liat Luvisol",
        "Cambisols" to "Tanah Lempung",
        "Ferralsols" to "Tanah Laterit",
        "Fluvisols" to "Tanah Endapan",
        "Leptosols" to "Tanah Tipis",
        "Regosols" to "Tanah Regosol",
        "Umbrisols" to "Tanah Humus",
        "Andosols" to "Tanah Vulkanik",
        "Arenosols" to "Tanah Pasir",
        "Podzols" to "Tanah Podsol",
        "Gleysols" to "Tanah Lembab",
        "Histosols" to "Tanah Gambut"
    )

    suspend fun getSoilType(latitude: Double, longitude: Double): SoilType? = withContext(Dispatchers.IO) {
        val cacheKey = "${latitude.toPrecision(3)},${longitude.toPrecision(3)}"

        cache[cacheKey]?.let { return@withContext it }

        val apiResult = runCatching {
            fetchFromIsric(latitude, longitude)
        }.getOrNull()

        val result = apiResult ?: fallbackByRegion(latitude, longitude)

        result?.let { cache[cacheKey] = it }
        result
    }

    private fun fetchFromIsric(latitude: Double, longitude: Double): SoilType? {
        val url = URL("https://rest.isric.org/soilgrids/v2.0/classification/query?lat=$latitude&lon=$longitude")

        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)

        val wrbClass = json.optString("wrb_class_name", null) ?: return null
        val risk = wrbRiskMap[wrbClass] ?: SoilRisk.SEDANG
        val indonesianName = wrbIndonesianNames[wrbClass] ?: wrbClass

        return SoilType(name = wrbClass, indonesianName = indonesianName, risk = risk)
    }

    private fun fallbackByRegion(latitude: Double, longitude: Double): SoilType? {
        val region = regions.firstOrNull { r ->
            latitude in r.minLat..r.maxLat && longitude in r.minLon..r.maxLon
        } ?: return null
        return SoilType(
            name = region.soilName,
            indonesianName = region.soilIndonesian,
            risk = region.risk
        )
    }

    private data class RegionData(
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double,
        val soilName: String, val soilIndonesian: String, val risk: SoilRisk
    )

    private val regions = listOf(
        RegionData(-8.0, -7.7, 111.3, 111.6, "Ferralsols", "Tanah Laterit", SoilRisk.SEDANG),
        RegionData(-8.2, -8.0, 111.3, 111.6, "Cambisols", "Tanah Lempung", SoilRisk.SEDANG),
        RegionData(-7.7, -7.5, 111.3, 111.6, "Acrisols", "Tanah Liat Merah", SoilRisk.TINGGI),
        RegionData(-8.0, -7.7, 111.6, 111.9, "Vertisols", "Tanah Liat Ekspansif", SoilRisk.SANGAT_TINGGI)
    )

    fun clearCache() { cache.clear() }

    private fun Double.toPrecision(decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * factor) / factor
    }
}
