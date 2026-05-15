package com.unidagontor.retakid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.screens.LaporanItem
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// ─── DTO dari Supabase ────────────────────────────────────────
@Serializable
data class LaporanDto(
    val id              : String,
    @SerialName("nama_lokasi")  val namaLokasi   : String,
    val status          : String,
    val catatan         : String        = "",
    val latitude        : Double        = 0.0,
    val longitude       : Double        = 0.0,
    @SerialName("foto_url")     val fotoUrl      : String?      = null,
    val pelapor         : String        = "User",
    val terverifikasi   : Int           = 0,
    @SerialName("created_at")   val createdAt    : String       = ""
) {
    fun toLaporanItem() = LaporanItem(
        id            = id,
        namaLokasi    = namaLokasi,
        status        = status,
        catatan       = catatan,
        timestamp     = formatTimestamp(createdAt),
        pelapor       = pelapor,
        terverifikasi = terverifikasi,
        fotoUrl       = fotoUrl
    )
}

private fun formatTimestamp(iso: String): String = try {
    val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val outputFmt = SimpleDateFormat("d MMM, HH:mm", Locale("id"))
    inputFmt.parse(iso.take(19))?.let { outputFmt.format(it) } ?: iso
} catch (_: Exception) { iso }

// ─── UI State ─────────────────────────────────────────────────
data class BerandaState(
    val laporanList : List<LaporanItem> = emptyList(),
    val isLoading   : Boolean           = false,
    val error       : String?           = null
)

// ─── ViewModel ────────────────────────────────────────────────
class BerandaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BerandaState())
    val uiState: StateFlow<BerandaState> = _uiState.asStateFlow()

    init {
        fetchLaporan()
    }

    fun fetchLaporan() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // Urutan prioritas status: BAHAYA (0) → WASPADA (1) → AMAN (2)
                val statusOrder = mapOf("BAHAYA" to 0, "WASPADA" to 1, "AMAN" to 2)

                val list = SupabaseClient.client
                    .from("laporan")
                    .select {
                        order("created_at", Order.DESCENDING)
                        limit(50)                               // ambil 50 terbaru
                    }
                    .decodeList<LaporanDto>()
                    .sortedWith(
                        compareBy<LaporanDto> { statusOrder[it.status] ?: 3 }  // BAHAYA → WASPADA → AMAN
                            .thenByDescending { it.createdAt }                  // dalam tiap grup: terbaru dulu
                    )
                    .map { it.toLaporanItem() }

                _uiState.update { it.copy(laporanList = list, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Gagal memuat data: ${e.message}") }
            }
        }
    }

    /** Tambah +1 konfirmasi dari user lain. */
    fun konfirmasiLaporan(laporanId: String) {
        viewModelScope.launch {
            try {

                SupabaseClient.client.postgrest.rpc(
                    "konfirmasi_laporan",
                    mapOf("lid" to laporanId)
                )
                // Refresh list setelah konfirmasi
                fetchLaporan()
            } catch (_: Exception) { /* silent fail */ }
        }
    }
}