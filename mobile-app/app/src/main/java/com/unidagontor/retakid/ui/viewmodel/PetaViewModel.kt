package com.unidagontor.retakid.ui.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.weather.WeatherApiService
import com.unidagontor.retakid.data.weather.WeatherData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LaporanMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,   // "AMAN" | "WASPADA" | "BAHAYA"
    val namaLokasi: String,
    val timestamp: String
)

enum class FilterRisiko { SEMUA, AMAN, WASPADA, BAHAYA }

data class PetaUiState(
    val markers: List<LaporanMarker>    = emptyList(),
    val weather: WeatherData?           = null,
    val isLoadingWeather: Boolean       = true,
    val weatherError: String?           = null,
    val filterRisiko: FilterRisiko      = FilterRisiko.SEMUA,
    val showWeatherCard: Boolean        = true
)

class PetaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PetaUiState())
    val uiState: StateFlow<PetaUiState> = _uiState

    init {
        loadWeather()
        loadDummyMarkers()
    }

    fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingWeather = true, weatherError = null)
            WeatherApiService.getCurrentWeather()
                .onSuccess { data ->
                    _uiState.value = _uiState.value.copy(
                        weather = data,
                        isLoadingWeather = false
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingWeather = false,
                        weatherError = "Gagal memuat cuaca: ${err.message}"
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

    fun filteredMarkers(): List<LaporanMarker> {
        val all = _uiState.value.markers
        return when (_uiState.value.filterRisiko) {
            FilterRisiko.SEMUA   -> all
            FilterRisiko.AMAN    -> all.filter { it.status == "AMAN" }
            FilterRisiko.WASPADA -> all.filter { it.status == "WASPADA" }
            FilterRisiko.BAHAYA  -> all.filter { it.status == "BAHAYA" }
        }
    }

    // TODO: Ganti dengan query Supabase Postgrest — dummy untuk development
    private fun loadDummyMarkers() {
        _uiState.value = _uiState.value.copy(
            markers = listOf(
                LaporanMarker("1", -7.876, 111.470, "BAHAYA",  "Lereng Jenangan Utara", "2 jam lalu"),
                LaporanMarker("2", -7.865, 111.455, "WASPADA", "Jalan Perbatasan",      "5 jam lalu"),
                LaporanMarker("3", -7.882, 111.478, "AMAN",    "Desa Setono",           "1 hari lalu"),
                LaporanMarker("4", -7.870, 111.462, "WASPADA", "Tambang Timur",         "3 jam lalu"),
                LaporanMarker("5", -7.858, 111.480, "BAHAYA",  "Lereng Selatan",        "30 menit lalu"),
            )
        )
    }
}