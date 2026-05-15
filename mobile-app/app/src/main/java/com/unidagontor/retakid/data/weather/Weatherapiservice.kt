package com.unidagontor.retakid.data.weather

import android.content.Context
import com.unidagontor.retakid.data.offline.HybridCache
import com.unidagontor.retakid.data.offline.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// ─── Model ────────────────────────────────────────────────────────────────────

data class WeatherData(
    val temperatureCelsius : Double,
    val precipitation      : Double,   // mm
    val rain               : Double,   // mm
    val weatherCode        : Int,
    val windspeedKmh       : Double,
    val humidity           : Int,
    val condition          : WeatherCondition,
    /** Apakah data ini berasal dari cache offline (bukan fetch segar) */
    val isFromCache        : Boolean = false,
    /** Timestamp ketika data diambil dari API (Unix ms) */
    val fetchedAt          : Long = System.currentTimeMillis()
)

enum class WeatherCondition(val label: String, val emoji: String, val riskNote: String) {
    CLEAR        ("Cerah",           "☀️",  "Risiko longsor rendah"),
    PARTLY_CLOUDY("Berawan",         "⛅",  "Pantau retakan secara berkala"),
    FOGGY        ("Berkabut",        "🌫️",  "Jarak pandang berkurang"),
    DRIZZLE      ("Gerimis",         "🌦️",  "Waspada jika lereng sudah retak"),
    RAIN         ("Hujan",           "🌧️",  "Tingkat risiko meningkat"),
    HEAVY_RAIN   ("Hujan Lebat",     "⛈️",  "BAHAYA — periksa lereng sekarang"),
    SNOW         ("Salju",           "❄️",  "Tidak relevan di wilayah ini"),
    UNKNOWN      ("Tidak diketahui", "❓",  "")
}

fun Int.toWeatherCondition(): WeatherCondition = when (this) {
    0            -> WeatherCondition.CLEAR
    1, 2         -> WeatherCondition.PARTLY_CLOUDY
    3            -> WeatherCondition.PARTLY_CLOUDY
    45, 48       -> WeatherCondition.FOGGY
    51, 53, 55   -> WeatherCondition.DRIZZLE
    61, 63       -> WeatherCondition.RAIN
    65, 80, 81   -> WeatherCondition.HEAVY_RAIN
    82, 95, 96, 99 -> WeatherCondition.HEAVY_RAIN
    71, 73, 75   -> WeatherCondition.SNOW
    else         -> WeatherCondition.UNKNOWN
}


// ─── WeatherApiService ────────────────────────────────────────────────────────

/**
 * Layanan cuaca dengan arsitektur Hybrid Online-Offline.
 *
 * Strategi:
 *  - Jika ONLINE → fetch API Open-Meteo → simpan ke HybridCache (TTL 6 jam)
 *  - Jika OFFLINE → kembalikan cache terakhir jika umurnya ≤ 6 jam
 *  - Jika cache > 6 jam atau tidak ada → kembalikan null
 *    (MultiFactorRiskEngine akan mengabaikan faktor cuaca, lihat catatan di bawah)
 *
 * Koordinat dikunci ke area Jenangan-Ponorogo. Untuk multi-area, jadikan
 * [getCurrentWeather] menerima lat/lon sebagai parameter.
 */
object WeatherApiService {

    private const val LATITUDE  = -7.8717
    private const val LONGITUDE = 111.4638
    private const val TIMEZONE  = "Asia/Jakarta"
    private const val TIMEOUT_MS = 5_000

    // Cache key: satu titik tetap per instance
    private val cacheKey = "${LATITUDE}_${LONGITUDE}"

    private val BASE_URL = buildString {
        append("https://api.open-meteo.com/v1/forecast")
        append("?latitude=$LATITUDE")
        append("&longitude=$LONGITUDE")
        append("&current=temperature_2m,relative_humidity_2m,precipitation,rain,weathercode,windspeed_10m")
        append("&timezone=$TIMEZONE")
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Mengembalikan [WeatherData] atau null.
     *
     * - Online: fetch segar, simpan cache
     * - Offline + cache ≤ 6 jam: kembalikan cache dengan [WeatherData.isFromCache] = true
     * - Offline + cache > 6 jam atau tidak ada: kembalikan null
     *   → [MultiFactorRiskEngine] harus skip faktor cuaca
     */
    suspend fun getCurrentWeather(): Result<WeatherData> = withContext(Dispatchers.IO) {
        val ctx      = appContext
        val isOnline = ctx != null && NetworkMonitor.isOnline(ctx)

        // ── Online path ───────────────────────────────────────────────────────
        if (isOnline) {
            return@withContext runCatching {
                fetchFromApi().also { data ->
                    ctx?.let { saveToCache(it, data) }
                }
            }
        }

        // ── Offline path ──────────────────────────────────────────────────────
        if (ctx != null) {
            val cached = loadFromCache(ctx)
            if (cached != null) return@withContext Result.success(cached)
        }

        // Tidak ada cache valid
        Result.failure(Exception("Offline dan tidak ada cache cuaca yang valid"))
    }

    /** Usia cache cuaca dalam jam, atau null jika tidak ada cache */
    suspend fun cacheAgeHours(context: Context): Double? {
        val ts = HybridCache.getTimestamp(context, HybridCache.NS_WEATHER, cacheKey) ?: return null
        return (System.currentTimeMillis() - ts) / (1000.0 * 60 * 60)
    }

    /** Representasi waktu fetch terakhir yang bisa ditampilkan di UI */
    suspend fun lastFetchLabel(context: Context): String? {
        val ts = HybridCache.getTimestamp(context, HybridCache.NS_WEATHER, cacheKey) ?: return null
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun fetchFromApi(): WeatherData {
        val conn = URL(BASE_URL).openConnection().apply {
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
        }
        val response = conn.getInputStream().bufferedReader().readText()
        return parseJson(response, isFromCache = false)
    }

    private suspend fun saveToCache(context: Context, data: WeatherData) {
        val json = JSONObject().apply {
            put("temperature",  data.temperatureCelsius)
            put("precipitation",data.precipitation)
            put("rain",         data.rain)
            put("weathercode",  data.weatherCode)
            put("windspeed",    data.windspeedKmh)
            put("humidity",     data.humidity)
        }.toString()
        HybridCache.put(context, HybridCache.NS_WEATHER, cacheKey, json)
    }

    /**
     * Muat dari cache. Kembalikan null jika:
     * - Tidak ada entry
     * - Usia > [HybridCache.TTL_WEATHER_MS] (6 jam)
     */
    private suspend fun loadFromCache(context: Context): WeatherData? {
        // getIgnoreTtl untuk mendapat data mentah, lalu cek usia sendiri
        val ts  = HybridCache.getTimestamp(context, HybridCache.NS_WEATHER, cacheKey) ?: return null
        val age = System.currentTimeMillis() - ts
        if (age > HybridCache.TTL_WEATHER_MS) return null   // terlalu lama

        val raw = HybridCache.getIgnoreTtl(context, HybridCache.NS_WEATHER, cacheKey) ?: return null
        return try {
            parseJsonFromCache(raw, fetchedAt = ts)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJson(jsonText: String, isFromCache: Boolean): WeatherData {
        val json    = JSONObject(jsonText)
        val current = json.getJSONObject("current")
        val code    = current.getInt("weathercode")
        return WeatherData(
            temperatureCelsius = current.getDouble("temperature_2m"),
            precipitation      = current.getDouble("precipitation"),
            rain               = current.getDouble("rain"),
            weatherCode        = code,
            windspeedKmh       = current.getDouble("windspeed_10m"),
            humidity           = current.getInt("relative_humidity_2m"),
            condition          = code.toWeatherCondition(),
            isFromCache        = isFromCache,
            fetchedAt          = System.currentTimeMillis()
        )
    }

    private fun parseJsonFromCache(jsonText: String, fetchedAt: Long): WeatherData {
        val obj  = JSONObject(jsonText)
        val code = obj.getInt("weathercode")
        return WeatherData(
            temperatureCelsius = obj.getDouble("temperature"),
            precipitation      = obj.getDouble("precipitation"),
            rain               = obj.getDouble("rain"),
            weatherCode        = code,
            windspeedKmh       = obj.getDouble("windspeed"),
            humidity           = obj.getInt("humidity"),
            condition          = code.toWeatherCondition(),
            isFromCache        = true,
            fetchedAt          = fetchedAt
        )
    }
}