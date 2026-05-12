package com.unidagontor.retakid.ui.screens


import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unidagontor.retakid.data.weather.WeatherCondition
import com.unidagontor.retakid.data.weather.WeatherData
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.FilterRisiko
import com.unidagontor.retakid.ui.viewmodel.LaporanMarker
import com.unidagontor.retakid.ui.viewmodel.PetaViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


@Composable
fun PetaTab(vm: PetaViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {


        OsmdroidMapView(
            markers  = vm.filteredMarkers(),
            modifier = Modifier.fillMaxSize()
        )


        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            FilterChipRow(
                current  = state.filterRisiko,
                onSelect = { vm.setFilter(it) }
            )
        }


        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            // Toggle button
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                SmallFloatingActionButton(
                    onClick            = { vm.toggleWeatherCard() },
                    containerColor     = Color.White,
                    contentColor       = GreenPrimary,
                    modifier           = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        if (state.showWeatherCard) Icons.Default.KeyboardArrowDown
                        else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle cuaca"
                    )
                }
            }

            AnimatedVisibility(
                visible = state.showWeatherCard,
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut()
            ) {
                WeatherCard(
                    weather        = state.weather,
                    isLoading      = state.isLoadingWeather,
                    error          = state.weatherError,
                    onRetry        = { vm.loadWeather() }
                )
            }
        }
    }
}

@Composable
fun OsmdroidMapView(
    markers: List<LaporanMarker>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Konfigurasi osmdroid user-agent (wajib, atau tiles tidak muncul)
    Configuration.getInstance().userAgentValue = context.packageName

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK) // OpenStreetMap
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                // Pusatkan ke Jenangan, Ponorogo
                controller.setCenter(GeoPoint(-7.876, 111.470))
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            markers.forEach { laporan ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(laporan.latitude, laporan.longitude)
                    title    = laporan.namaLokasi
                    snippet  = "Status: ${laporan.status} · ${laporan.timestamp}"
                    // Warna pin sesuai status
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(marker)
            }
            mapView.invalidate()
        },
        modifier = modifier
    )
}

@Composable
fun FilterChipRow(
    current: FilterRisiko,
    onSelect: (FilterRisiko) -> Unit
) {
    val filters = listOf(
        FilterRisiko.SEMUA   to "Semua",
        FilterRisiko.BAHAYA  to "🔴 Bahaya",
        FilterRisiko.WASPADA to "🟡 Waspada",
        FilterRisiko.AMAN    to "🟢 Aman"
    )

    Row(
        modifier            = Modifier
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
            .border(1.dp, Divider, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        filters.forEach { (filter, label) ->
            val isSelected = current == filter
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(filter) },
                label    = { Text(label, fontSize = 12.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenPrimary,
                    selectedLabelColor     = Color.White,
                    containerColor         = Color.Transparent,
                    labelColor             = TextSecondary
                ),
                border   = FilterChipDefaults.filterChipBorder(
                    enabled  = true,
                    selected = isSelected,
                    borderColor         = Color.Transparent,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}


@Composable
fun WeatherCard(
    weather: WeatherData?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        when {
            isLoading -> WeatherLoading()
            error != null -> WeatherError(error, onRetry)
            weather != null -> WeatherContent(weather)
        }
    }
}

@Composable
private fun WeatherLoading() {
    Row(
        modifier            = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GreenPrimary, strokeWidth = 2.dp)
        Text("Memuat data cuaca Jenangan…", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun WeatherError(message: String, onRetry: () -> Unit) {
    Row(
        modifier            = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(message, color = StatusBahaya, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Coba lagi", color = GreenPrimary) }
    }
}

@Composable
private fun WeatherContent(data: WeatherData) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

        // Baris atas: ikon + suhu + kondisi
        Row(
            verticalAlignment      = Alignment.CenterVertically,
            horizontalArrangement  = Arrangement.SpaceBetween,
            modifier               = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.condition.emoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "${data.temperatureCelsius.toInt()}°C",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 24.sp,
                        color      = TextPrimary
                    )
                    Text(data.condition.label, color = TextSecondary, fontSize = 13.sp)
                }
            }

            // Risk note chip
            val (chipBg, chipText) = when {
                data.rain > 10 || data.condition == WeatherCondition.HEAVY_RAIN ->
                    StatusBahayaBg to StatusBahaya
                data.rain > 2 || data.condition == WeatherCondition.RAIN ->
                    StatusWaspadaBg to StatusWaspada
                else ->
                    StatusAmanBg to StatusAman
            }

            Surface(
                color  = chipBg,
                shape  = RoundedCornerShape(8.dp)
            ) {
                Text(
                    data.condition.riskNote,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color      = chipText,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Divider)
        Spacer(modifier = Modifier.height(10.dp))

        // Baris bawah: 3 metrik
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            WeatherMetric("💧", "Hujan", "${data.rain} mm")
            WeatherMetric("💨", "Angin", "${data.windspeedKmh.toInt()} km/h")
            WeatherMetric("🌫️", "Kelembapan", "${data.humidity}%")
        }

        // Peringatan curah hujan tinggi
        if (data.rain > 5) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(color = StatusBahayaBg, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusBahaya, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Curah hujan tinggi — periksa lereng di sekitar Anda",
                        color    = StatusBahaya,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(icon: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 18.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}