package com.unidagontor.retakid.data.weather


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL



data class WeatherData(
    val temperatureCelsius: Double,
    val precipitation: Double,       // mm
    val rain: Double,                // mm
    val weatherCode: Int,
    val windspeedKmh: Double,
    val humidity: Int,
    val condition: WeatherCondition
)

enum class WeatherCondition(val label: String, val emoji: String, val riskNote: String) {
    CLEAR       ("Cerah",           "☀️",  "Risiko longsor rendah"),
    PARTLY_CLOUDY("Berawan",        "⛅",  "Pantau retakan secara berkala"),
    FOGGY       ("Berkabut",        "🌫️",  "Jarak pandang berkurang"),
    DRIZZLE     ("Gerimis",         "🌦️",  "Waspada jika lereng sudah retak"),
    RAIN        ("Hujan",           "🌧️",  "Tingkat risiko meningkat"),
    HEAVY_RAIN  ("Hujan Lebat",     "⛈️",  "BAHAYA — periksa lereng sekarang"),
    SNOW        ("Salju",           "❄️",  "Tidak relevan di wilayah ini"),
    UNKNOWN     ("Tidak diketahui", "❓",  "")
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

object WeatherApiService {

    private const val LATITUDE  = -7.8717
    private const val LONGITUDE = 111.4638
    private const val TIMEZONE  = "Asia/Jakarta"

    /** Jenangan default — used by Peta screen (legacy). */
    suspend fun getCurrentWeather(): Result<WeatherData> =
        getCurrentWeather(LATITUDE, LONGITUDE)

    /** Fetch weather for actual GPS coordinates. */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherData> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append("https://api.open-meteo.com/v1/forecast")
                append("?latitude=$latitude")
                append("&longitude=$longitude")
                append("&current=temperature_2m,relative_humidity_2m,precipitation,rain,weathercode,windspeed_10m")
                append("&timezone=$TIMEZONE")
            }
            val response = URL(url).readText()
            val json     = JSONObject(response)
            val current  = json.getJSONObject("current")

            val code = current.getInt("weathercode")
            WeatherData(
                temperatureCelsius = current.getDouble("temperature_2m"),
                precipitation      = current.getDouble("precipitation"),
                rain               = current.getDouble("rain"),
                weatherCode        = code,
                windspeedKmh       = current.getDouble("windspeed_10m"),
                humidity           = current.getInt("relative_humidity_2m"),
                condition          = code.toWeatherCondition()
            )
        }
    }
}