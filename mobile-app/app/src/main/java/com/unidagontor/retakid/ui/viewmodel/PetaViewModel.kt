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
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.data.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Models ──────────────────────────────────────────────────────────────────

@kotlinx.serialization.Serializable
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

// ─── UI State ─────────────────────────────────────────────────────────────────

data class PetaUiState(
    val markers          : List<LaporanMarker> = emptyList(),
    val markersFromCache : Boolean             = false,   // true = marker dari cache lokal
    val weather          : WeatherData?        = null,
    val isLoadingWeather : Boolean             = true,
    val isLoadingMarkers : Boolean             = true,
    val weatherError     : String?             = null,
    val filterRisiko     : FilterRisiko        = FilterRisiko.SEMUA,
    val showWeatherCard  : Boolean             = true,
    val selectedMarker   : LaporanMarker?      = null,
    val isOnline         : Boolean             = true,
    val pendingCount     : Int                 = 0,
    val cachedWeatherAt  : String?             = null,
    val userLocation     : LocationData?       = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class PetaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PetaUiState())
    val uiState: StateFlow<PetaUiState> = _uiState

    private lateinit var appContext: android.content.Context

    private val json = Json { ignoreUnknownKeys = true }
    private val PREF_MARKER_CACHE = "retakid_marker_cache"
    private val KEY_MARKERS       = "cached_markers_v1"

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        MapCacheManager.configure(appContext)
        WeatherApiService.init(appContext)

        viewModelScope.launch {
            val locService = LocationService(appContext)
            val loc = locService.getCurrentLocation()
            _uiState.value = _uiState.value.copy(userLocation = loc)
        }

        // Pantau status network — saat online kembali, refresh data
        NetworkMonitor.observe(appContext).onEach { online ->
            val wasOffline = !_uiState.value.isOnline
            _uiState.value = _uiState.value.copy(isOnline = online)

            if (online && wasOffline) {
                // Kembali online: refresh cuaca dan marker
                loadWeather()
                loadMarkers()
            } else if (online && _uiState.value.weather == null && !_uiState.value.isLoadingWeather) {
                loadWeather()
            }
        }.launchIn(viewModelScope)

        OfflineQueue.init(appContext)
        OfflineQueue.pendingCount.onEach { count ->
            _uiState.value = _uiState.value.copy(pendingCount = count)
        }.launchIn(viewModelScope)

        loadWeather()
        loadMarkers()
    }

    // ─── Markers: Online = Supabase → simpan cache; Offline = baca cache ─────

    fun loadMarkers() {
        _uiState.value = _uiState.value.copy(isLoadingMarkers = true)
        viewModelScope.launch {
            val isOnline = _uiState.value.isOnline

            if (isOnline) {
                loadMarkersFromNetwork()
            } else {
                loadMarkersFromCache()
            }
        }
    }

    private suspend fun loadMarkersFromNetwork() {
        try {
            val list = SupabaseClient.client
                .from("laporan")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<PetaLaporanDto>()
                .filter { it.latitude != 0.0 && it.longitude != 0.0 }
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

            // Simpan ke cache lokal
            saveMarkersToCache(list)

            _uiState.value = _uiState.value.copy(
                markers          = list,
                markersFromCache = false,
                isLoadingMarkers = false
            )
        } catch (e: Exception) {
            // Network gagal — coba gunakan cache
            val cached = loadMarkersFromDisk()
            _uiState.value = _uiState.value.copy(
                markers          = cached,
                markersFromCache = cached.isNotEmpty(),
                isLoadingMarkers = false
            )
        }
    }

    private suspend fun loadMarkersFromCache() {
        val cached = loadMarkersFromDisk()
        _uiState.value = _uiState.value.copy(
            markers          = cached,
            markersFromCache = cached.isNotEmpty(),
            isLoadingMarkers = false
        )
    }

    private suspend fun saveMarkersToCache(markers: List<LaporanMarker>) =
        withContext(Dispatchers.IO) {
            try {
                val encoded = json.encodeToString(markers)
                appContext.getSharedPreferences(PREF_MARKER_CACHE, android.content.Context.MODE_PRIVATE)
                    .edit().putString(KEY_MARKERS, encoded).apply()
            } catch (_: Exception) { /* tidak kritis */ }
        }

    private suspend fun loadMarkersFromDisk(): List<LaporanMarker> =
        withContext(Dispatchers.IO) {
            try {
                val raw = appContext
                    .getSharedPreferences(PREF_MARKER_CACHE, android.content.Context.MODE_PRIVATE)
                    .getString(KEY_MARKERS, null) ?: return@withContext emptyList()
                json.decodeFromString<List<LaporanMarker>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ─── Weather: Online = fetch + cache; Offline = cache ≤6 jam ────────────

    fun loadWeather() {
        if (!_uiState.value.isOnline) {
            val hasCachedData = _uiState.value.weather != null
            _uiState.value = _uiState.value.copy(
                isLoadingWeather = false,
                weatherError     = if (hasCachedData) null else "offline"
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingWeather = true, weatherError = null)
            WeatherApiService.getCurrentWeather()
                .onSuccess { data ->
                    val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    _uiState.value = _uiState.value.copy(
                        weather          = data,
                        isLoadingWeather = false,
                        cachedWeatherAt  = timeLabel
                    )
                }
                .onFailure {
                    val hasCachedData = _uiState.value.weather != null
                    _uiState.value = _uiState.value.copy(
                        isLoadingWeather = false,
                        weatherError     = if (hasCachedData) null else "Gagal memuat cuaca"
                    )
                }
        }
    }

    // ─── Filter & Selection ───────────────────────────────────────────────────

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
    val id           : String,
    @kotlinx.serialization.SerialName("nama_lokasi")
    val namaLokasi   : String,
    val status       : String,
    val catatan      : String = "",
    val latitude     : Double = 0.0,
    val longitude    : Double = 0.0,
    val pelapor      : String = "User",
    val terverifikasi: Int    = 0,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt    : String = ""
)

private fun formatMarkerTime(iso: String): String = try {
    val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val outputFmt = SimpleDateFormat("d MMM, HH:mm", Locale("id"))
    inputFmt.parse(iso.take(19))?.let { outputFmt.format(it) } ?: iso
} catch (_: Exception) { iso }
