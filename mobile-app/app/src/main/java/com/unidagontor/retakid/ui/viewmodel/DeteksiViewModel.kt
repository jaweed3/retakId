package com.unidagontor.retakid.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.analysis.CombinedAnalysisEngine
import com.unidagontor.retakid.data.analysis.CombinedAnalysisResult
import com.unidagontor.retakid.data.analysis.DataFetchStatus
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.data.location.LocationService
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.offline.NetworkMonitor
import com.unidagontor.retakid.data.offline.OfflineQueue
import com.unidagontor.retakid.data.offline.PendingLaporan
import com.unidagontor.retakid.data.offline.SyncLaporanWorker
import com.unidagontor.retakid.data.risk.RiskFactorReport
import com.unidagontor.retakid.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class DeteksiStage {
    INITIAL, CAMERA, ANALYZING, RESULT, REPORT_FORM, SUCCESS
}

data class DeteksiState(
    val stage              : DeteksiStage          = DeteksiStage.INITIAL,
    val capturedImage      : Bitmap?               = null,
    val detectionResult    : DetectionResult?      = null,
    val location           : LocationData?         = null,
    val isSubmitting       : Boolean               = false,
    val uploadProgress     : Float                 = 0f,
    val error              : String?               = null,
    val combinedResult     : CombinedAnalysisResult? = null,
    val riskReport         : RiskFactorReport?     = null,
    val dataStatus         : DataFetchStatus?      = null,
    val isAnalyzingContext : Boolean               = false,
    val sentOffline        : Boolean               = false   // laporan disimpan lokal, belum online
)

@Serializable
private data class LaporanInsert(
    @SerialName("user_id")     val userId      : String,
    @SerialName("nama_lokasi") val namaLokasi  : String,
    val status                 : String,
    val catatan                : String,
    val latitude               : Double,
    val longitude              : Double,
    @SerialName("foto_url")    val fotoUrl     : String?,
    val pelapor                : String,
    val terverifikasi          : Int = 0
)

class DeteksiViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeteksiState())
    val uiState: StateFlow<DeteksiState> = _uiState.asStateFlow()

    private val combinedEngine  = CombinedAnalysisEngine(application)
    private val locationService = LocationService(application)

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
                // Tandai sedang mengambil data konteks lingkungan
                _uiState.update { it.copy(isAnalyzingContext = true) }

                // Ambil lokasi lebih dulu (dibutuhkan oleh engine)
                val location = locationService.getCurrentLocation()
                _uiState.update { it.copy(location = location) }

                val lat = location?.latitude  ?: -7.8717
                val lon = location?.longitude ?: 111.4638

                // Jalankan analisis gabungan: ML + cuaca + elevasi + lereng + tanah
                val combined = combinedEngine.analyze(
                    bitmap    = bitmap,
                    latitude  = lat,
                    longitude = lon
                )

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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzingContext = false,
                        error = "Gagal menganalisis: ${e.message}",
                        stage = DeteksiStage.INITIAL
                    )
                }
            }
        }
    }

    fun proceedToReport() {
        // Lokasi sudah diambil saat analyzeImage, tidak perlu fetch lagi
        _uiState.update { it.copy(stage = DeteksiStage.REPORT_FORM) }
    }

    fun submitReport(namaLokasi: String, catatan: String) {
        val state = _uiState.value
        if (state.detectionResult == null) return

        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            try {
                val supabase = SupabaseClient.client
                val session  = supabase.auth.currentSessionOrNull()
                    ?: run {
                        supabase.auth.loadFromStorage()
                        supabase.auth.currentSessionOrNull()
                    }
                    ?: throw Exception("Sesi berakhir, silakan login ulang.")
                val user = session.user
                    ?: throw Exception("Data user tidak tersedia.")

                val isOnline = NetworkMonitor.isOnline(getApplication())
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

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = "Gagal mengirim laporan: ${e.message}")
                }
            }
        }
    }

    private suspend fun uploadFoto(bitmap: Bitmap): String {
        val supabase   = SupabaseClient.client
        val bucketName = "laporan-images"
        val fileName   = "laporan/${UUID.randomUUID()}.jpg"

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()

        supabase.storage.from(bucketName).upload(fileName, bytes) { upsert = false }
        return supabase.storage.from(bucketName).publicUrl(fileName)
    }

    private suspend fun tambahPoin(userId: String, poin: Int) {
        try {
            SupabaseClient.client.postgrest.rpc(
                "tambah_poin",
                mapOf("uid" to userId, "jumlah" to poin)
            )
        } catch (_: Exception) {
            // Non-critical, tidak block submit
        }
    }

    fun reset() {
        _uiState.update { DeteksiState() }
    }
}