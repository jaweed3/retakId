package com.unidagontor.retakid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.screens.LaporanItem
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class LaporanDto(
    val id: String,
    @SerialName("nama_lokasi") val namaLokasi: String = "",
    val status: String = "AMAN",
    val catatan: String = "",
    @SerialName("foto_url") val fotoUrl: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
    val pelapor: String = "",
    val terverifikasi: Int = 0
)

data class BerandaState(
    val laporanList: List<LaporanItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class BerandaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BerandaState())
    val uiState: StateFlow<BerandaState> = _uiState.asStateFlow()

    init {
        fetchLaporan()
        startRealtimeSubscription()
    }

    private fun startRealtimeSubscription() {
        viewModelScope.launch {
            try {
                SupabaseClient.client.realtime["beranda"]
                    .postgresChangeFlow<PostgresAction.All>(schema = "public") {
                        table = "laporan"
                    }
                    .collect {
                        fetchLaporan()
                    }
            } catch (_: Exception) { }
        }
    }

    fun fetchLaporan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val supabase = SupabaseClient.client
                val items = supabase.from("laporan")
                    .select { order("created_at", Order.DESCENDING) }
                    .decodeList<LaporanDto>()
                    .map { dto ->
                        LaporanItem(
                            id = dto.id,
                            namaLokasi = dto.namaLokasi,
                            status = dto.status,
                            catatan = dto.catatan,
                            fotoUrl = dto.fotoUrl,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            timestamp = formatTimestamp(parseIsoTimestamp(dto.createdAt)),
                            pelapor = dto.pelapor,
                            terverifikasi = dto.terverifikasi
                        )
                    }
                _uiState.value = BerandaState(laporanList = items, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = BerandaState(isLoading = false, error = e.message)
            }
        }
    }

    fun confirmLaporan(laporanId: String) {
        viewModelScope.launch {
            try {
                val supabase = SupabaseClient.client
                val current = supabase.from("laporan")
                    .select { filter { eq("id", laporanId) } }
                    .decodeSingle<LaporanDto>()
                supabase.from("laporan").update({
                    "terverifikasi" to (current.terverifikasi + 1)
                }) { filter { eq("id", laporanId) } }
            } catch (_: Exception) { }
        }
    }

    private fun parseIsoTimestamp(iso: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.parse(iso.take(19))?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Baru saja"
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60000 -> "Baru saja"
            diff < 3600000 -> "${diff / 60000} mnt lalu"
            diff < 86400000 -> "${diff / 3600000} jam lalu"
            else -> {
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
