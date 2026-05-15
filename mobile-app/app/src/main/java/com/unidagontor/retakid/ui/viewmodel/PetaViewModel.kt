package com.unidagontor.retakid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.offline.MapCacheManager
import com.unidagontor.retakid.data.offline.NetworkMonitor
import com.unidagontor.retakid.data.offline.OfflineQueue
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.data.weather.WeatherApiService
import com.unidagontor.retakid.data.weather.WeatherData
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class LaporanMarker(
    val id        : String,
    val latitude  : Double,
    val longitude : Double,
    val status    : String,   // "AMAN" | "WASPADA" | "BAHAYA"
    val namaLokasi: String,
    val timestamp : String,
    val catatan   : String = ""
)

enum class FilterRisiko { SEMUA, AMAN, WASPADA, BAHAYA }

data class PetaUiState(
    val markers         : List<LaporanMarker> = emptyList(),
    val weather         : WeatherData?        = null,
    val isLoadingWeather: Boolean             = true,
    val isLoadingMarkers: Boolean             = true,
    val weatherError    : String?             = null,
    val filterRisiko    : FilterRisiko        = FilterRisiko.SEMUA,
    val showWeatherCard : Boolean             = true,
    val selectedMarker  : LaporanMarker?      = null,
    val isOnline        : Boolean             = true,   // status koneksi realtime
    val pendingCount    : Int                 = 0       // laporan antri offline
)

class PetaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PetaUiState())
    val uiState: StateFlow<PetaUiState> = _uiState

    // Context disimpan via Application — injected saat class dibuat
    private lateinit var appContext: android.content.Context

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        MapCacheManager.configure(appContext)

        // Pantau status network secara realtime
        NetworkMonitor.observe(appContext).onEach { online ->
            _uiState.value = _uiState.value.copy(isOnline = online)
        }.launchIn(viewModelScope)

        // Pantau jumlah laporan pending
        OfflineQueue.init(appContext)
        OfflineQueue.pendingCount.onEach { count ->
            _uiState.value = _uiState.value.copy(pendingCount = count)
        }.launchIn(viewModelScope)

        loadWeather()
        loadMarkers()
    }

    // ── Load laporan dari Supabase sebagai markers ────────────────
    fun loadMarkers() {
        _uiState.value = _uiState.value.copy(isLoadingMarkers = true)
        viewModelScope.launch {
            try {
                val list = SupabaseClient.client
                    .from("laporan")
                    .select {
                        order("created_at", Order.DESCENDING)
                        limit(100)
                    }
                    .decodeList<PetaLaporanDto>()
                    .filter { it.latitude != 0.0 && it.longitude != 0.0 } // skip data tanpa koordinat
                    .map { dto ->
                        LaporanMarker(
                            id         = dto.id,
                            latitude   = dto.latitude,
                            longitude  = dto.longitude,
                            status     = dto.status,
                            namaLokasi = dto.namaLokasi,
                            timestamp  = formatMarkerTime(dto.createdAt),
                            catatan    = dto.catatan
                        )
                    }

                _uiState.value = _uiState.value.copy(
                    markers          = list,
                    isLoadingMarkers = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMarkers = false)
            }
        }
    }

    fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingWeather = true, weatherError = null)
            WeatherApiService.getCurrentWeather()
                .onSuccess { data ->
                    _uiState.value = _uiState.value.copy(weather = data, isLoadingWeather = false)
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingWeather = false,
                        weatherError     = "Gagal memuat cuaca: ${err.message}"
                    )
                }
        }
    }

    fun setFilter(filter: FilterRisiko) {
        _uiState.value = _uiState.value.copy(filterRisiko = filter)
    }

    fun toggleWeatherCard() {
        _uiState.value = _uiState.value.copy(
            showWeatherCard = !_uiState.value.showWeatherCard
        )
    }

    fun selectMarker(marker: LaporanMarker?) {
        _uiState.value = _uiState.value.copy(selectedMarker = marker)
    }

    fun filteredMarkers(): List<LaporanMarker> {
        val all = _uiState.value.markers
        return when (_uiState.value.filterRisiko) {
            FilterRisiko.SEMUA   -> all
            FilterRisiko.AMAN    -> all.filter { it.status == "AMAN" }
            FilterRisiko.WASPADA -> all.filter { it.status == "WASPADA" }
            FilterRisiko.BAHAYA  -> all.filter { it.status == "BAHAYA" }
        }
    }
}

// ─── DTO internal untuk decode Supabase ──────────────────────────────────────
@kotlinx.serialization.Serializable
private data class PetaLaporanDto(
    val id                                              : String,
    @kotlinx.serialization.SerialName("nama_lokasi")
    val namaLokasi  : String,
    val status      : String,
    val catatan     : String = "",
    val latitude    : Double = 0.0,
    val longitude   : Double = 0.0,
    val pelapor     : String = "User",
    val terverifikasi: Int   = 0,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt   : String = ""
)

private fun formatMarkerTime(iso: String): String = try {
    val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val outputFmt = SimpleDateFormat("d MMM, HH:mm", Locale("id"))
    inputFmt.parse(iso.take(19))?.let { outputFmt.format(it) } ?: iso
} catch (_: Exception) { iso }
