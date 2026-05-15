package com.unidagontor.retakid.data.analysis

import android.content.Context
import android.graphics.Bitmap
import com.unidagontor.retakid.data.elevation.ElevationService
import com.unidagontor.retakid.data.elevation.SlopeCalculator
import com.unidagontor.retakid.data.elevation.SlopeData
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.ml.TFLiteMLAnalyzer
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
    val locationOk : Boolean = false
) {
    val completedCount: Int get() =
        listOf(mlOk, weatherOk, elevationOk, slopeOk, soilOk, locationOk).count { it }

    val totalCount: Int get() = 6

    val summaryText: String get() = "$completedCount/$totalCount sumber data berhasil"
}

// ─── Orkestrator Analisis Gabungan ────────────────────────────────────────────
class CombinedAnalysisEngine(context: Context) {

    private val mlAnalyzer = TFLiteMLAnalyzer(context)

    /**
     * Menjalankan analisis penuh secara paralel:
     * 1. ML image analysis (TFLite)
     * 2. Weather (Open-Meteo)
     * 3. Elevation (Open-Meteo)
     * 4. Slope (berdasarkan elevasi 4 titik sekitar)
     * 5. Soil type (ISRIC / fallback regional)
     *
     * Semua dijalankan [coroutineScope] async agar paralel.
     * Setiap sumber diberi timeout agar tidak memblokir hasil.
     * Hasil akhir dihitung oleh [MultiFactorRiskEngine].
     *
     * @param bitmap       Gambar yang diambil dari kamera
     * @param latitude     Koordinat lokasi user
     * @param longitude    Koordinat lokasi user
     */
    suspend fun analyze(
        bitmap   : Bitmap,
        latitude : Double,
        longitude: Double
    ): CombinedAnalysisResult = coroutineScope {

        // ── Jalankan semua sumber data SECARA PARALEL ─────────────────────
        val mlJob        = async { runML(bitmap) }
        val weatherJob   = async { runWeather() }
        val elevationJob = async { runElevation(latitude, longitude) }
        val slopeJob     = async { runSlope(latitude, longitude) }
        val soilJob      = async { runSoil(latitude, longitude) }

        // ── Tunggu semua hasil ────────────────────────────────────────────
        val mlPair       = mlJob.await()         // Pair<DetectionResult, Float>
        val weather      = weatherJob.await()
        val elevation    = elevationJob.await()
        val slope        = slopeJob.await()
        val soil         = soilJob.await()

        val mlResult     = mlPair.first
        val mlConfidence = mlPair.second

        // ── Hitung risiko gabungan ─────────────────────────────────────────
        val riskReport = MultiFactorRiskEngine.analyze(
            mlResult        = mlResult,
            mlConfidence    = mlConfidence,
            slopeDegrees    = slope?.degrees,
            rainMm          = weather?.rain,
            elevationMeters = elevation,
            soilType        = soil
        )

        val status = DataFetchStatus(
            mlOk        = true,
            weatherOk   = weather   != null,
            elevationOk = elevation != null,
            slopeOk     = slope     != null,
            soilOk      = soil      != null,
            locationOk  = true   // latitude/longitude sudah tersedia jika dipanggil
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
            // TFLiteMLAnalyzer tidak return confidence secara langsung.
            // Kita ambil result dan pakai fixed confidence 0.75f sebagai proxy
            // (dapat direfine jika MLAnalyzer diubah agar return confidence).
            val result = mlAnalyzer.analyzeImage(bitmap)
            result to 0.75f
        } ?: (DetectionResult.AMAN to 0.4f)   // fallback aman jika timeout
    }

    private suspend fun runWeather(): WeatherData? {
        return withTimeoutOrNull(5_000L) {
            WeatherApiService.getCurrentWeather().getOrNull()
        }
    }

    private suspend fun runElevation(lat: Double, lon: Double): Double? {
        return withTimeoutOrNull(5_000L) {
            ElevationService.getElevation(lat, lon)?.elevationMeters
        }
    }

    private suspend fun runSlope(lat: Double, lon: Double): SlopeData? {
        return withTimeoutOrNull(8_000L) {
            SlopeCalculator.calculateSlope(lat, lon)
        }
    }

    private suspend fun runSoil(lat: Double, lon: Double): SoilType? {
        return withTimeoutOrNull(8_000L) {
            SoilTypeService.getSoilType(lat, lon)
        }
    }
}
