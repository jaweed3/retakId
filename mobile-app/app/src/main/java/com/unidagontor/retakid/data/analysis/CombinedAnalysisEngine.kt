package com.unidagontor.retakid.data.analysis

import android.content.Context
import android.graphics.Bitmap
import com.unidagontor.retakid.data.elevation.ElevationService
import com.unidagontor.retakid.data.elevation.SlopeCalculator
import com.unidagontor.retakid.data.elevation.SlopeData
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.ml.TFLiteMLAnalyzer
import com.unidagontor.retakid.data.offline.NetworkMonitor
import com.unidagontor.retakid.data.risk.MultiFactorRiskEngine
import com.unidagontor.retakid.data.risk.RiskFactorReport
import com.unidagontor.retakid.data.soil.SoilType
import com.unidagontor.retakid.data.soil.SoilTypeService
import com.unidagontor.retakid.data.weather.WeatherApiService
import com.unidagontor.retakid.data.weather.WeatherData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

// ─── Hasil lengkap dari analisis gabungan ─────────────────────────────────────
data class CombinedAnalysisResult(
    // Data mentah tiap sumber
    val mlResult       : DetectionResult,
    val mlConfidence   : Float,
    val weather        : WeatherData?,
    val slopeData      : SlopeData?,
    val soilType       : SoilType?,
    val elevationMeters: Double?,

    // Laporan risiko gabungan dari MultiFactorRiskEngine
    val riskReport     : RiskFactorReport,

    // Status pengumpulan data
    val dataStatus     : DataFetchStatus
)

data class DataFetchStatus(
    val mlOk       : Boolean = false,
    val weatherOk  : Boolean = false,
    val elevationOk: Boolean = false,
    val slopeOk    : Boolean = false,
    val soilOk     : Boolean = false,
    val locationOk : Boolean = false,
    /** true jika perangkat dalam mode offline saat analisis dijalankan */
    val isOffline  : Boolean = false,
    /** true jika cuaca diskip karena offline/cache kadaluarsa */
    val weatherSkipped: Boolean = false
) {
    val completedCount: Int get() =
        listOf(mlOk, weatherOk, elevationOk, slopeOk, soilOk, locationOk).count { it }

    val totalCount: Int get() = 6

    val summaryText: String get() {
        val base = "$completedCount/$totalCount sumber data berhasil"
        return if (isOffline) "$base (mode offline)" else base
    }
}

// ─── Orkestrator Analisis Gabungan ────────────────────────────────────────────
class CombinedAnalysisEngine(context: Context) {

    private val appContext = context.applicationContext
    private val mlAnalyzer = TFLiteMLAnalyzer(appContext)

    init {
        // Inisialisasi service dengan context agar bisa akses NetworkMonitor & cache
        ElevationService.init(appContext)
        SoilTypeService.init(appContext)
        WeatherApiService.init(appContext)
    }

    /**
     * Menjalankan analisis penuh secara paralel:
     * 1. ML image analysis (TFLite)
     * 2. Weather (Open-Meteo, dengan cache offline ≤6 jam)
     * 3. Elevation (Open-Meteo → disk cache → SRTM .hgt)
     * 4. Slope (berdasarkan elevasi 4 titik sekitar)
     * 5. Soil type (ISRIC → disk cache → JSON lokal → hardcoded)
     *
     * Prinsip Fail-Fast: status network dicek SEKALI di awal.
     * Timeout per sumber disesuaikan: lebih ketat saat online (API bisa slow),
     * sangat cepat saat offline (hanya baca cache/file).
     *
     * @param bitmap    Gambar dari kamera
     * @param latitude  Koordinat user
     * @param longitude Koordinat user
     */
    suspend fun analyze(
        bitmap   : Bitmap,
        latitude : Double,
        longitude: Double
    ): CombinedAnalysisResult = coroutineScope {

        val isOnline = NetworkMonitor.isOnline(appContext)

        // Timeout lebih ketat saat offline (tidak perlu menunggu jaringan)
        val weatherTimeout   = if (isOnline) 6_000L else 1_000L
        val elevationTimeout = if (isOnline) 6_000L else 2_000L
        val soilTimeout      = if (isOnline) 6_000L else 2_000L
        val slopeTimeout     = if (isOnline) 10_000L else 4_000L

        // ── Jalankan semua sumber data SECARA PARALEL ─────────────────────
        val mlJob        = async { runML(bitmap) }
        val weatherJob   = async { runWeather(weatherTimeout) }
        val elevationJob = async { runElevation(latitude, longitude, elevationTimeout) }
        val slopeJob     = async { runSlope(latitude, longitude, slopeTimeout) }
        val soilJob      = async { runSoil(latitude, longitude, soilTimeout) }

        // ── Tunggu semua hasil ────────────────────────────────────────────
        val mlPair     = mlJob.await()
        val weather    = weatherJob.await()
        val elevation  = elevationJob.await()
        val slope      = slopeJob.await()
        val soil       = soilJob.await()

        val mlResult     = mlPair.first
        val mlConfidence = mlPair.second

        // Cuaca dianggap "offline" jika tidak ada data DAN perangkat memang offline
        val weatherIsOffline = weather == null && !isOnline

        // ── Hitung risiko gabungan ─────────────────────────────────────────
        val riskReport = MultiFactorRiskEngine.analyze(
            mlResult        = mlResult,
            mlConfidence    = mlConfidence,
            slopeDegrees    = slope?.degrees,
            rainMm          = weather?.rain,
            elevationMeters = elevation,
            soilType        = soil,
            weatherIsOffline = weatherIsOffline
        )

        val status = DataFetchStatus(
            mlOk           = true,
            weatherOk      = weather != null,
            elevationOk    = elevation != null,
            slopeOk        = slope    != null,
            soilOk         = soil     != null,
            locationOk     = true,
            isOffline      = !isOnline,
            weatherSkipped = weatherIsOffline
        )

        CombinedAnalysisResult(
            mlResult        = mlResult,
            mlConfidence    = mlConfidence,
            weather         = weather,
            slopeData       = slope,
            soilType        = soil,
            elevationMeters = elevation,
            riskReport      = riskReport,
            dataStatus      = status
        )
    }

    // ── Runner tiap sumber data ───────────────────────────────────────────────

    private suspend fun runML(bitmap: Bitmap): Pair<DetectionResult, Float> {
        return withTimeoutOrNull(10_000L) {
            val result = mlAnalyzer.analyzeImage(bitmap)
            result to 0.75f
        } ?: (DetectionResult.AMAN to 0.4f)
    }

    private suspend fun runWeather(timeoutMs: Long): WeatherData? {
        return withTimeoutOrNull(timeoutMs) {
            WeatherApiService.getCurrentWeather().getOrNull()
        }
    }

    private suspend fun runElevation(lat: Double, lon: Double, timeoutMs: Long): Double? {
        return withTimeoutOrNull(timeoutMs) {
            ElevationService.getElevation(lat, lon)?.elevationMeters
        }
    }

    private suspend fun runSlope(lat: Double, lon: Double, timeoutMs: Long): SlopeData? {
        return withTimeoutOrNull(timeoutMs) {
            SlopeCalculator.calculateSlope(lat, lon)
        }
    }

    private suspend fun runSoil(lat: Double, lon: Double, timeoutMs: Long): SoilType? {
        return withTimeoutOrNull(timeoutMs) {
            SoilTypeService.getSoilType(lat, lon)
        }
    }
}
