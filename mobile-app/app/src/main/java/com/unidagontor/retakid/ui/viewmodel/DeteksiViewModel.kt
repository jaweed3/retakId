package com.unidagontor.retakid.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.unidagontor.retakid.data.elevation.ElevationService
import com.unidagontor.retakid.data.elevation.SlopeCalculator
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.data.location.LocationService
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.ml.MLResult
import com.unidagontor.retakid.data.risk.MultiFactorRiskEngine
import com.unidagontor.retakid.data.risk.RiskFactorReport
import com.unidagontor.retakid.data.soil.SoilTypeService
import com.unidagontor.retakid.data.weather.WeatherApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*

enum class DeteksiStage {
    INITIAL,
    CAMERA,
    ANALYZING,
    ANALYZING_ENV,
    RESULT,
    REPORT_FORM,
    SUCCESS
}

data class DeteksiState(
    val stage: DeteksiStage = DeteksiStage.INITIAL,
    val capturedImage: Bitmap? = null,
    val mlResult: MLResult? = null,
    val riskFactorReport: RiskFactorReport? = null,
    val location: LocationData? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

class DeteksiViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DeteksiState())
    val uiState: StateFlow<DeteksiState> = _uiState.asStateFlow()

    private val mlAnalyzer = com.unidagontor.retakid.data.ml.TFLiteMLAnalyzer(application)
    private val locationService = LocationService(application)
    private val db = FirebaseFirestore.getInstance()

    fun startDetection() {
        _uiState.update { it.copy(stage = DeteksiStage.CAMERA, error = null) }
    }

    fun onImageCaptured(bitmap: Bitmap) {
        _uiState.update { it.copy(capturedImage = bitmap, stage = DeteksiStage.ANALYZING) }
        analyzeImage(bitmap)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val mlResult = mlAnalyzer.analyzeImage(bitmap)
                _uiState.update { it.copy(mlResult = mlResult, stage = DeteksiStage.ANALYZING_ENV) }
                fetchEnvironmentalFactors(mlResult)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal menganalisis gambar: ${e.message}", stage = DeteksiStage.INITIAL) }
            }
        }
    }

    private suspend fun fetchEnvironmentalFactors(mlResult: MLResult) {
        val location = locationService.getCurrentLocation()
        _uiState.update { it.copy(location = location) }

        val report = withContext(Dispatchers.IO) {
            if (location == null) {
                return@withContext MultiFactorRiskEngine.analyze(
                    mlResult = mlResult.detectionResult,
                    mlConfidence = mlResult.confidence
                )
            }

            val lat = location.latitude
            val lon = location.longitude

            withTimeoutOrNull(5000L) {
                coroutineScope {
                    val elevationDeferred = async { ElevationService.getElevation(lat, lon) }
                    val weatherDeferred = async { WeatherApiService.getCurrentWeather(lat, lon).getOrNull() }
                    val soilDeferred = async { SoilTypeService.getSoilType(lat, lon) }

                    val elevation = elevationDeferred.await()
                    val weather = weatherDeferred.await()
                    val soil = soilDeferred.await()

                    val slope = if (elevation != null) {
                        SlopeCalculator.calculateSlope(lat, lon)
                    } else null

                    MultiFactorRiskEngine.analyze(
                        mlResult = mlResult.detectionResult,
                        mlConfidence = mlResult.confidence,
                        slopeDegrees = slope?.degrees,
                        rainMm = weather?.rain,
                        elevationMeters = elevation?.elevationMeters,
                        soilType = soil
                    )
                }
            } ?: MultiFactorRiskEngine.analyze(
                mlResult = mlResult.detectionResult,
                mlConfidence = mlResult.confidence
            )
        }

        _uiState.update { it.copy(riskFactorReport = report, stage = DeteksiStage.RESULT) }
    }

    fun proceedToReport() {
        _uiState.update { it.copy(stage = DeteksiStage.REPORT_FORM) }
    }

    fun submitReport(namaLokasi: String, catatan: String) {
        val state = _uiState.value
        val finalResult = state.riskFactorReport?.finalResult ?: state.mlResult?.detectionResult ?: return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            try {
                val finalLocation = state.location ?: locationService.getCurrentLocation()

                val json = org.json.JSONObject().apply {
                    put("nama_lokasi", namaLokasi)
                    put("status", finalResult.name)
                    put("catatan", catatan)
                    put("latitude", finalLocation?.latitude ?: 0.0)
                    put("longitude", finalLocation?.longitude ?: 0.0)
                    put("pelapor", "User")
                    put("terverifikasi", 0)
                }.toString()

                withContext(Dispatchers.IO) {
                    val url = URL("${com.unidagontor.retakid.BuildConfig.SUPABASE_URL}/rest/v1/laporan")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("apikey", com.unidagontor.retakid.BuildConfig.SUPABASE_ANON_KEY)
                    conn.setRequestProperty("Authorization", "Bearer ${com.unidagontor.retakid.BuildConfig.SUPABASE_ANON_KEY}")
                    conn.doOutput = true
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 15_000
                    conn.outputStream.use { it.write(json.toByteArray()) }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                        throw RuntimeException("HTTP $code: $error")
                    }
                }

                _uiState.update { it.copy(isSubmitting = false, stage = DeteksiStage.SUCCESS) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = "Gagal mengirim laporan: ${e.message}") }
            }
        }
    }

    fun reset() {
        _uiState.update { DeteksiState() }
    }
}
