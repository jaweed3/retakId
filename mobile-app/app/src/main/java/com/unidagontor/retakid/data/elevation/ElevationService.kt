package com.unidagontor.retakid.data.elevation

import android.content.Context
import com.unidagontor.retakid.data.offline.HybridCache
import com.unidagontor.retakid.data.offline.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.math.floor

data class ElevationData(val elevationMeters: Double)

/**
 * Layanan elevasi dengan arsitektur Hybrid Online-Offline.
 *
 * Urutan strategi:
 *  1. Memory cache (in-process, paling cepat)
 *  2. Jika online → API Open-Meteo, simpan ke disk cache
 *  3. Jika offline / API gagal → disk cache (HybridCache, TTL 30 hari)
 *  4. Jika disk cache juga miss → baca file SRTM .hgt dari assets (Bilinear Interpolation)
 *
 * Prinsip Fail-Fast: NetworkMonitor dicek SEBELUM membuka koneksi
 * agar tidak ada timeout saat offline.
 */
object ElevationService {

    private const val TIMEOUT_MS = 4_000
    private const val GRID_DECIMALS = 3   // presisi grid key (~111m per 0.001°)

    // Memory cache (sesi aktif)
    private val memCache = mutableMapOf<String, ElevationData>()

    // Context; diisi lewat init() dari ViewModel
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Mendapatkan data elevasi untuk koordinat yang diberikan.
     * Mengembalikan null hanya jika SEMUA strategi gagal.
     */
    suspend fun getElevation(latitude: Double, longitude: Double): ElevationData? =
        withContext(Dispatchers.IO) {
            val cacheKey = gridKey(latitude, longitude)

            // 1. Memory cache
            memCache[cacheKey]?.let { return@withContext it }

            val ctx = appContext
            val isOnline = ctx != null && NetworkMonitor.isOnline(ctx)

            // 2. Online path: panggil API → simpan ke disk
            if (isOnline && ctx != null) {
                fetchFromApi(latitude, longitude)?.let { data ->
                    memCache[cacheKey] = data
                    HybridCache.put(ctx, HybridCache.NS_ELEVATION, cacheKey, data.elevationMeters.toString())
                    return@withContext data
                }
            }

            // 3. Disk cache (berlaku saat offline ATAU API gagal)
            if (ctx != null) {
                HybridCache.get(ctx, HybridCache.NS_ELEVATION, cacheKey)
                    ?.toDoubleOrNull()
                    ?.let { elevation ->
                        val data = ElevationData(elevation)
                        memCache[cacheKey] = data
                        return@withContext data
                    }
            }

            // 4. Fallback SRTM .hgt dari assets
            if (ctx != null) {
                readFromHgt(ctx, latitude, longitude)?.let { data ->
                    memCache[cacheKey] = data
                    // Simpan ke disk agar cache terisi untuk sesi berikutnya
                    HybridCache.put(ctx, HybridCache.NS_ELEVATION, cacheKey, data.elevationMeters.toString())
                    return@withContext data
                }
            }

            null
        }

    fun clearMemCache() = memCache.clear()

    // ── Strategi 2: API Open-Meteo ────────────────────────────────────────────

    private fun fetchFromApi(latitude: Double, longitude: Double): ElevationData? {
        return try {
            val url = "https://api.open-meteo.com/v1/elevation?latitude=$latitude&longitude=$longitude"
            val conn = URL(url).openConnection().apply {
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
            }
            val response = conn.getInputStream().bufferedReader().readText()
            val json = JSONObject(response)
            // API mengembalikan { "elevation": [value] } atau { "elevation": value }
            val elevationNode = json.opt("elevation")
            val elevation = when (elevationNode) {
                is org.json.JSONArray -> elevationNode.getDouble(0)
                is Number -> elevationNode.toDouble()
                else -> return null
            }
            ElevationData(elevationMeters = elevation)
        } catch (_: Exception) {
            null
        }
    }

    // ── Strategi 4: SRTM .hgt dari assets ────────────────────────────────────

    /**
     * Membaca file SRTM3 dari assets/dem/<NS><LAT><EW><LON>.hgt
     * Contoh: assets/dem/S08E111.hgt untuk lat -8, lon 111
     *
     * Format SRTM3: 1201×1201 piksel, setiap piksel = Int16 (Big Endian), 3 arc-second.
     *
     * Menggunakan Bilinear Interpolation untuk akurasi sub-piksel.
     *
     * Kembalikan null jika file tidak ada (pengguna tidak memasang DEM).
     */
    private fun readFromHgt(context: Context, latitude: Double, longitude: Double): ElevationData? {
        return try {
            val latTile = floor(latitude).toInt()
            val lonTile = floor(longitude).toInt()

            val ns  = if (latTile >= 0) "N" else "S"
            val ew  = if (lonTile >= 0) "E" else "W"
            val lat = "%02d".format(Math.abs(latTile))
            val lon = "%03d".format(Math.abs(lonTile))
            val fileName = "dem/$ns$lat$ew$lon.hgt"

            val bytes = context.assets.open(fileName).use { it.readBytes() }

            // SRTM3: 1201 samples per row, resolution = 1/1200 degree
            val samples = 1201
            val resolution = 1.0 / (samples - 1)

            // Posisi relatif dalam tile (0.0 – 1200.0)
            val row = (latitude - latTile)  / resolution   // 0 = south, 1200 = north
            val col = (longitude - lonTile) / resolution   // 0 = west,  1200 = east

            val r0 = row.toInt().coerceIn(0, samples - 2)
            val c0 = col.toInt().coerceIn(0, samples - 2)
            val r1 = r0 + 1
            val c1 = c0 + 1

            val dr = row - r0
            val dc = col - c0

            // HGT: row 0 = northernmost, row 1200 = southernmost (inverted)
            fun readSample(r: Int, c: Int): Double {
                val invertedRow = (samples - 1) - r
                val idx = (invertedRow * samples + c) * 2
                val hi  = bytes[idx].toInt() and 0xFF
                val lo  = bytes[idx + 1].toInt() and 0xFF
                val raw = ((hi shl 8) or lo).toShort().toInt()
                return if (raw == -32768) Double.NaN else raw.toDouble()
            }

            val e00 = readSample(r0, c0)
            val e10 = readSample(r1, c0)
            val e01 = readSample(r0, c1)
            val e11 = readSample(r1, c1)

            // Bilinear interpolation (skip jika ada void data)
            if (e00.isNaN() || e10.isNaN() || e01.isNaN() || e11.isNaN()) return null

            val elevation =
                e00 * (1 - dr) * (1 - dc) +
                e10 *      dr  * (1 - dc) +
                e01 * (1 - dr) *      dc  +
                e11 *      dr  *      dc

            ElevationData(elevationMeters = elevation)
        } catch (_: Exception) {
            null   // file tidak ada atau corrupt — normal jika DEM belum dimasang
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Grid key dengan presisi GRID_DECIMALS (default ~111 m resolusi) */
    private fun gridKey(lat: Double, lon: Double): String {
        val factor = Math.pow(10.0, GRID_DECIMALS.toDouble())
        val rLat = Math.round(lat * factor) / factor
        val rLon = Math.round(lon * factor) / factor
        return "$rLat,$rLon"
    }
}
