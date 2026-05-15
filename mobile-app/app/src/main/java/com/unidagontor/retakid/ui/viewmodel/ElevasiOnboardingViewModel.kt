package com.unidagontor.retakid.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.elevation.HgtElevationSource
import com.unidagontor.retakid.data.elevation.TileDownloader
import com.unidagontor.retakid.data.elevation.TileRegionCalculator
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.data.location.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ElevasiOnboardingState {
    data object Idle : ElevasiOnboardingState()
    data object RequestingPermission : ElevasiOnboardingState()
    data class Detecting(val message: String) : ElevasiOnboardingState()
    data class FoundLocation(val location: LocationData, val daerah: String) : ElevasiOnboardingState()
    data class Downloading(val progress: Int, val total: Int, val tileName: String) : ElevasiOnboardingState()
    data class Ready(val totalDownloaded: Int, val totalFailed: Int) : ElevasiOnboardingState()
    data class Error(val message: String) : ElevasiOnboardingState()
    data object Done : ElevasiOnboardingState()
}

class ElevasiOnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val locationService = LocationService(application)
    private val prefs = application.getSharedPreferences("retakid", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<ElevasiOnboardingState>(ElevasiOnboardingState.Idle)
    val uiState: StateFlow<ElevasiOnboardingState> = _uiState.asStateFlow()

    private var detectedLocation: LocationData? = null
    private var detectedTiles: List<String> = emptyList()

    fun startDetection() {
        _uiState.value = ElevasiOnboardingState.Detecting("Mendeteksi lokasi...")
        viewModelScope.launch {
            val location = locationService.getCurrentLocation()
            if (location == null) {
                _uiState.value = ElevasiOnboardingState.Error(
                    "Gagal mendapatkan lokasi. Pastikan GPS aktif dan izin lokasi diberikan."
                )
                return@launch
            }
            detectedLocation = location
            detectedTiles = TileRegionCalculator.tilesForLocation(location.latitude, location.longitude)
            val daerah = TileRegionCalculator.estimateKabupaten(location.latitude, location.longitude)
            _uiState.value = ElevasiOnboardingState.FoundLocation(location, daerah)
        }
    }

    fun requestPermissionAndDetect() {
        _uiState.value = ElevasiOnboardingState.RequestingPermission
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startDetection()
        } else {
            _uiState.value = ElevasiOnboardingState.Error(
                "Izin lokasi diperlukan untuk menentukan peta elevasi area Anda."
            )
        }
    }

    fun startDownload() {
        val location = detectedLocation ?: return
        val tiles = detectedTiles
        if (tiles.isEmpty()) {
            skip()
            return
        }

        _uiState.value = ElevasiOnboardingState.Downloading(0, tiles.size, "")

        viewModelScope.launch {
            val demDir = File(getApplication<Application>().filesDir, "dem")
            demDir.mkdirs()

            var success = 0
            var failed = 0
            for ((i, tileName) in tiles.withIndex()) {
                _uiState.value = ElevasiOnboardingState.Downloading(i + 1, tiles.size, tileName)
                val ok = TileDownloader.downloadTile(tileName, demDir)
                if (ok) success++ else failed++
            }

            HgtElevationSource.loadFromDirectory(demDir)

            _uiState.value = ElevasiOnboardingState.Ready(success, failed)
        }
    }

    fun skip() {
        savePrefs()
        _uiState.value = ElevasiOnboardingState.Done
    }

    fun finish() {
        savePrefs()
        _uiState.value = ElevasiOnboardingState.Done
    }

    private fun savePrefs() {
        prefs.edit().putBoolean("elevasi_ready", true).apply()
    }
}
