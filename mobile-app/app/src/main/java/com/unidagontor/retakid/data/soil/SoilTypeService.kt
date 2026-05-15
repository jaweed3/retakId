package com.unidagontor.retakid.data.soil

import android.content.Context
import com.unidagontor.retakid.data.offline.HybridCache
import com.unidagontor.retakid.data.offline.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class SoilRisk(val label: String, val riskNote: String) {
    RENDAH        ("Rendah",        "Tanah berpasir — retakan kecil"),
    SEDANG        ("Sedang",        "Tanah lempung — retakan mungkin terjadi"),
    TINGGI        ("Tinggi",        "Tanah liat — rawan retak & mengembang"),
    SANGAT_TINGGI ("Sangat Tinggi", "Tanah ekspansif — retakan besar & dalam"),
    TIDAK_DIKETAHUI("Tidak Diketahui", "Data tanah tidak tersedia")
}

data class SoilType(
    val name           : String,
    val indonesianName : String,
    val risk           : SoilRisk,
    val source         : SoilSource = SoilSource.API
) {
    val riskScore: Double get() = when (risk) {
        SoilRisk.RENDAH         -> 0.2
        SoilRisk.SEDANG         -> 0.5
        SoilRisk.TINGGI         -> 0.8
        SoilRisk.SANGAT_TINGGI  -> 1.0
        SoilRisk.TIDAK_DIKETAHUI -> 0.3
    }
}

/** Sumber data yang digunakan untuk menentukan jenis tanah */
enum class SoilSource { API, DISK_CACHE, LOCAL_JSON, HARDCODED_REGION }

/**
 * Layanan jenis tanah dengan arsitektur Hybrid Online-Offline.
 *
 * Urutan strategi:
 *  1. Memory cache (in-process)
 *  2. Jika online → ISRIC API, simpan ke disk cache
 *  3. Jika offline / API gagal → disk cache (HybridCache, TTL 90 hari)
 *  4. File JSON lokal di assets/soil_regions.json (dinamis, dapat diupdate)
 *  5. Tabel region hardcoded (ultimate fallback)
 *
 * Prinsip Fail-Fast: NetworkMonitor dicek SEBELUM membuka koneksi.
 */
object SoilTypeService {

    private const val TIMEOUT_MS    = 5_000
    private const val GRID_DECIMALS = 2   // ~1.1 km per 0.01°

    private val memCache = mutableMapOf<String, SoilType>()
    private var appContext: Context? = null

    // ── WRB klasifikasi → risiko & nama Indonesia ─────────────────────────────

    private val wrbRiskMap = mapOf(
        "Vertisols"    to SoilRisk.SANGAT_TINGGI,
        "Planosols"    to SoilRisk.SANGAT_TINGGI,
        "Acrisols"     to SoilRisk.TINGGI,
        "Lixisols"     to SoilRisk.TINGGI,
        "Nitisols"     to SoilRisk.TINGGI,
        "Alisols"      to SoilRisk.TINGGI,
        "Luvisols"     to SoilRisk.TINGGI,
        "Cambisols"    to SoilRisk.SEDANG,
        "Ferralsols"   to SoilRisk.SEDANG,
        "Fluvisols"    to SoilRisk.SEDANG,
        "Leptosols"    to SoilRisk.SEDANG,
        "Regosols"     to SoilRisk.SEDANG,
        "Umbrisols"    to SoilRisk.SEDANG,
        "Andosols"     to SoilRisk.SEDANG,
        "Arenosols"    to SoilRisk.RENDAH,
        "Podzols"      to SoilRisk.RENDAH,
        "Gleysols"     to SoilRisk.RENDAH,
        "Histosols"    to SoilRisk.RENDAH
    )

    private val wrbIndonesianNames = mapOf(
        "Vertisols"    to "Tanah Liat Ekspansif",
        "Planosols"    to "Tanah Liat Planosol",
        "Acrisols"     to "Tanah Liat Merah",
        "Lixisols"     to "Tanah Liat Berpasir",
        "Nitisols"     to "Tanah Liat Nitrosol",
        "Alisols"      to "Tanah Liat Alisol",
        "Luvisols"     to "Tanah Liat Luvisol",
        "Cambisols"    to "Tanah Lempung",
        "Ferralsols"   to "Tanah Laterit",
        "Fluvisols"    to "Tanah Endapan",
        "Leptosols"    to "Tanah Tipis",
        "Regosols"     to "Tanah Regosol",
        "Umbrisols"    to "Tanah Humus",
        "Andosols"     to "Tanah Vulkanik",
        "Arenosols"    to "Tanah Pasir",
        "Podzols"      to "Tanah Podsol",
        "Gleysols"     to "Tanah Lembab",
        "Histosols"    to "Tanah Gambut"
    )

    // ── Inisialisasi ──────────────────────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Entrypoint utama ──────────────────────────────────────────────────────

    suspend fun getSoilType(latitude: Double, longitude: Double): SoilType? =
        withContext(Dispatchers.IO) {
            val cacheKey = gridKey(latitude, longitude)

            // 1. Memory cache
            memCache[cacheKey]?.let { return@withContext it }

            val ctx      = appContext
            val isOnline = ctx != null && NetworkMonitor.isOnline(ctx)

            // 2. Online path: ISRIC API
            if (isOnline && ctx != null) {
                fetchFromIsric(latitude, longitude)?.let { result ->
                    val data = result.copy(source = SoilSource.API)
                    memCache[cacheKey] = data
                    HybridCache.put(ctx, HybridCache.NS_SOIL, cacheKey, serializeSoilType(data))
                    return@withContext data
                }
            }

            // 3. Disk cache
            if (ctx != null) {
                HybridCache.get(ctx, HybridCache.NS_SOIL, cacheKey)
                    ?.let { deserializeSoilType(it, SoilSource.DISK_CACHE) }
                    ?.let { data ->
                        memCache[cacheKey] = data
                        return@withContext data
                    }
            }

            // 4. File JSON lokal (assets/soil_regions.json)
            if (ctx != null) {
                readFromLocalJson(ctx, latitude, longitude)?.let { data ->
                    memCache[cacheKey] = data
                    return@withContext data
                }
            }

            // 5. Hardcoded region table
            fallbackHardcoded(latitude, longitude)?.also { data ->
                memCache[cacheKey] = data
            }
        }

    fun clearMemCache() = memCache.clear()

    // ── Strategi 2: ISRIC REST API ────────────────────────────────────────────

    private fun fetchFromIsric(latitude: Double, longitude: Double): SoilType? {
        return try {
            val url  = URL("https://rest.isric.org/soilgrids/v2.0/classification/query?lat=$latitude&lon=$longitude")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout    = TIMEOUT_MS

            val response = conn.inputStream.bufferedReader().readText()
            val json     = JSONObject(response)
            val wrbClass = json.optString("wrb_class_name").takeIf { it.isNotEmpty() } ?: return null

            val risk          = wrbRiskMap[wrbClass] ?: SoilRisk.SEDANG
            val indonesianName = wrbIndonesianNames[wrbClass] ?: wrbClass

            SoilType(name = wrbClass, indonesianName = indonesianName, risk = risk)
        } catch (_: Exception) {
            null
        }
    }

    // ── Strategi 4: File JSON lokal ───────────────────────────────────────────

    /**
     * Membaca file assets/soil_regions.json dengan format:
     * ```json
     * [
     *   {
     *     "min_lat": -8.0, "max_lat": -7.7,
     *     "min_lon": 111.3, "max_lon": 111.6,
     *     "soil_name": "Ferralsols",
     *     "soil_indonesian": "Tanah Laterit",
     *     "risk": "SEDANG"
     *   },
     *   ...
     * ]
     * ```
     * File ini bisa diupdate tanpa perlu rilis versi baru (tersimpan di assets).
     */
    private fun readFromLocalJson(context: Context, latitude: Double, longitude: Double): SoilType? {
        return try {
            val jsonText = context.assets.open("soil_regions.json").bufferedReader().readText()
            val array    = JSONArray(jsonText)

            for (i in 0 until array.length()) {
                val obj    = array.getJSONObject(i)
                val minLat = obj.getDouble("min_lat")
                val maxLat = obj.getDouble("max_lat")
                val minLon = obj.getDouble("min_lon")
                val maxLon = obj.getDouble("max_lon")

                if (latitude in minLat..maxLat && longitude in minLon..maxLon) {
                    val soilName       = obj.getString("soil_name")
                    val soilIndonesian = obj.getString("soil_indonesian")
                    val riskStr        = obj.getString("risk")
                    val risk           = SoilRisk.entries.firstOrNull { it.name == riskStr } ?: SoilRisk.SEDANG

                    return SoilType(
                        name           = soilName,
                        indonesianName = soilIndonesian,
                        risk           = risk,
                        source         = SoilSource.LOCAL_JSON
                    )
                }
            }
            null
        } catch (_: Exception) {
            null   // file tidak ada — normal, fallback ke step berikutnya
        }
    }

    // ── Strategi 5: Hardcoded regions ─────────────────────────────────────────

    private data class HardcodedRegion(
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double,
        val soilName: String, val soilIndonesian: String, val risk: SoilRisk
    )

    private val hardcodedRegions = listOf(
        HardcodedRegion(-8.0, -7.7, 111.3, 111.6, "Ferralsols",  "Tanah Laterit",       SoilRisk.SEDANG),
        HardcodedRegion(-8.2, -8.0, 111.3, 111.6, "Cambisols",   "Tanah Lempung",        SoilRisk.SEDANG),
        HardcodedRegion(-7.7, -7.5, 111.3, 111.6, "Acrisols",    "Tanah Liat Merah",     SoilRisk.TINGGI),
        HardcodedRegion(-8.0, -7.7, 111.6, 111.9, "Vertisols",   "Tanah Liat Ekspansif", SoilRisk.SANGAT_TINGGI),
        // Jawa Tengah umum
        HardcodedRegion(-8.0, -6.5, 108.0, 111.3, "Andosols",    "Tanah Vulkanik",       SoilRisk.SEDANG),
        // Jawa Timur lereng gunung
        HardcodedRegion(-8.5, -7.0, 111.5, 114.5, "Cambisols",   "Tanah Lempung",        SoilRisk.SEDANG),
        // Indonesia umum (catch-all)
        HardcodedRegion(-11.0, 6.0, 95.0, 141.0,  "Cambisols",   "Tanah Lempung",        SoilRisk.SEDANG)
    )

    private fun fallbackHardcoded(latitude: Double, longitude: Double): SoilType? {
        val region = hardcodedRegions.firstOrNull { r ->
            latitude in r.minLat..r.maxLat && longitude in r.minLon..r.maxLon
        } ?: return null
        return SoilType(
            name           = region.soilName,
            indonesianName = region.soilIndonesian,
            risk           = region.risk,
            source         = SoilSource.HARDCODED_REGION
        )
    }

    // ── Serialisasi untuk disk cache ──────────────────────────────────────────

    private fun serializeSoilType(soil: SoilType): String =
        JSONObject().apply {
            put("name",          soil.name)
            put("indonesian",    soil.indonesianName)
            put("risk",          soil.risk.name)
        }.toString()

    private fun deserializeSoilType(json: String, source: SoilSource): SoilType? = try {
        val obj  = JSONObject(json)
        val risk = SoilRisk.entries.firstOrNull { it.name == obj.getString("risk") } ?: SoilRisk.SEDANG
        SoilType(
            name           = obj.getString("name"),
            indonesianName = obj.getString("indonesian"),
            risk           = risk,
            source         = source
        )
    } catch (_: Exception) { null }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun gridKey(lat: Double, lon: Double): String {
        val factor = Math.pow(10.0, GRID_DECIMALS.toDouble())
        val rLat   = Math.round(lat * factor) / factor
        val rLon   = Math.round(lon * factor) / factor
        return "$rLat,$rLon"
    }
}
