package com.unidagontor.retakid.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.data.location.LocationService
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.ml.TFLiteMLAnalyzer
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
import java.util.UUID

enum class DeteksiStage {
    INITIAL, CAMERA, ANALYZING, RESULT, REPORT_FORM, SUCCESS
}

data class DeteksiState(
    val stage           : DeteksiStage     = DeteksiStage.INITIAL,
    val capturedImage   : Bitmap?          = null,
    val detectionResult : DetectionResult? = null,
    val location        : LocationData?    = null,
    val isSubmitting    : Boolean          = false,
    val uploadProgress  : Float            = 0f,
    val error           : String?          = null
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

    private val mlAnalyzer      = TFLiteMLAnalyzer(application)
    private val locationService = LocationService(application)

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
                _uiState.update {
                    it.copy(error = "Gagal menganalisis gambar: ${e.message}", stage = DeteksiStage.INITIAL)
                }
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

        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            try {
                val supabase = SupabaseClient.client

                // ── Ambil session dengan cara robust ─────────────────
                // Dengan SettingsSessionManager di SupabaseClient,
                // sesi tersimpan di SharedPreferences dan auto-load.
                // loadFromStorage() sebagai fallback kalau belum ter-load.
                val session = supabase.auth.currentSessionOrNull()
                    ?: run {
                        supabase.auth.loadFromStorage()
                        supabase.auth.currentSessionOrNull()
                    }
                    ?: throw Exception("Sesi berakhir, silakan login ulang.")

                val user = session.user
                    ?: throw Exception("Data user tidak tersedia, silakan login ulang.")

                // 1. Upload foto
                val fotoUrl = state.capturedImage?.let { bitmap -> uploadFoto(bitmap) }

                // 2. Pastikan lokasi tersedia
                val finalLocation = state.location ?: locationService.getCurrentLocation()

                // 3. Insert laporan
                val laporan = LaporanInsert(
                    userId     = user.id,
                    namaLokasi = namaLokasi,
                    status     = state.detectionResult.name,
                    catatan    = catatan,
                    latitude   = finalLocation?.latitude  ?: 0.0,
                    longitude  = finalLocation?.longitude ?: 0.0,
                    fotoUrl    = fotoUrl,
                    pelapor    = user.email ?: "User",
                )

                supabase.from("laporan").insert(laporan)

                // 4. Tambah poin (+10 per laporan) via RPC
                tambahPoin(user.id, poin = 10)

                _uiState.update { it.copy(isSubmitting = false, stage = DeteksiStage.SUCCESS) }

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