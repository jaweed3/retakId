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
import com.unidagontor.retakid.util.BitmapUtils
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
    VALIDATING,
    ANALYZING,
    ANALYZING_ENV,
    UNCERTAIN,
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
    val error: String? = null,
    val validationError: String? = null
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
        _uiState.update { it.copy(capturedImage = bitmap, stage = DeteksiStage.VALIDATING, validationError = null) }
        analyzeImage(bitmap)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val validation = withContext(Dispatchers.Default) { BitmapUtils.validateImage(bitmap) }
                if (!validation.valid) {
                    _uiState.update { it.copy(
                        stage = DeteksiStage.UNCERTAIN,
                        validationError = validation.message
                    )}
                    return@launch
                }

                _uiState.update { it.copy(stage = DeteksiStage.ANALYZING) }
                val mlResult = mlAnalyzer.analyzeImage(bitmap)
                _uiState.update { it.copy(mlResult = mlResult) }

                if (mlResult.detectionResult == DetectionResult.TIDAK_PASTI) {
                    _uiState.update { it.copy(
                        stage = DeteksiStage.UNCERTAIN,
                        validationError = "Hasil tidak pasti (${(mlResult.confidence * 100).toInt()}% yakin) — ambil foto ulang lebih dekat"
                    )}
                    return@launch
                }

                _uiState.update { it.copy(stage = DeteksiStage.ANALYZING_ENV) }
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
                    val weatherDeferred = async { WeatherApiService.getCurrentWeather().getOrNull() }
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

                val report = hashMapOf(
                    "namaLokasi" to namaLokasi,
                    "status" to finalResult.name,
                    "catatan" to catatan,
                    "latitude" to (finalLocation?.latitude ?: 0.0),
                    "longitude" to (finalLocation?.longitude ?: 0.0),
                    "timestamp" to System.currentTimeMillis(),
                    "pelapor" to "User",
                    "terverifikasi" to 0
                )

                db.collection("laporan")
                    .add(report)
                    .await()

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
