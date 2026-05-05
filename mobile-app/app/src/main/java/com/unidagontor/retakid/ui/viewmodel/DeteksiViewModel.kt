package com.unidagontor.retakid.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.data.location.LocationService
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.ml.MockMLAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

enum class DeteksiStage {
    INITIAL,
    CAMERA,
    ANALYZING,
    RESULT,
    REPORT_FORM,
    SUCCESS
}

data class DeteksiState(
    val stage: DeteksiStage = DeteksiStage.INITIAL,
    val capturedImage: Bitmap? = null,
    val detectionResult: DetectionResult? = null,
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
                val result = mlAnalyzer.analyzeImage(bitmap)
                _uiState.update { it.copy(detectionResult = result, stage = DeteksiStage.RESULT) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal menganalisis gambar: ${e.message}", stage = DeteksiStage.INITIAL) }
            }
        }
    }

    fun proceedToReport() {
        _uiState.update { it.copy(stage = DeteksiStage.REPORT_FORM) }
        fetchLocation()
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            val location = locationService.getCurrentLocation()
            _uiState.update { it.copy(location = location) }
        }
    }

    fun submitReport(namaLokasi: String, catatan: String) {
        val state = _uiState.value
        if (state.detectionResult == null) return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            try {
                // Pastikan lokasi sudah ada, jika belum coba fetch lagi sekali
                val finalLocation = state.location ?: locationService.getCurrentLocation()

                val report = hashMapOf(
                    "namaLokasi" to namaLokasi,
                    "status" to state.detectionResult.name,
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
