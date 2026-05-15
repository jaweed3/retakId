package com.unidagontor.retakid.ui.screens

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unidagontor.retakid.data.weather.WeatherCondition
import com.unidagontor.retakid.data.weather.WeatherData
import com.unidagontor.retakid.data.location.LocationData
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.FilterRisiko
import com.unidagontor.retakid.ui.viewmodel.LaporanMarker
import com.unidagontor.retakid.ui.viewmodel.PetaViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


// ─── Peta Tab ─────────────────────────────────────────────────────────────────
@Composable
fun PetaTab(vm: PetaViewModel = viewModel()) {
    val state   by vm.uiState.collectAsState()
    val context = LocalContext.current

    // Bootstrap sekali saat composable pertama dibuat
    LaunchedEffect(Unit) { vm.init(context) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Peta OSM ──────────────────────────────────────────────
        OsmdroidMapView(
            markers       = vm.filteredMarkers(),
            userLocation  = state.userLocation,
            onMarkerClick = { vm.selectMarker(it) },
            modifier      = Modifier.fillMaxSize()
        )

        // ── Overlay atas ──────────────────────────────────────
        Column(
            modifier            = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Satu Row: chip filter (scrollable) + FAB refresh
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipRow(
                    modifier = Modifier.weight(1f),
                    current  = state.filterRisiko,
                    counts   = markerCounts(state.markers),
                    onSelect = { vm.setFilter(it) }
                )

                SmallFloatingActionButton(
                    onClick        = { if (state.isOnline) vm.loadMarkers() },
                    containerColor = Color.White,
                    contentColor   = if (state.isOnline) GreenPrimary else TextSecondary
                ) {
                    if (state.isLoadingMarkers) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GreenPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            // Offline banner
            AnimatedVisibility(
                visible = !state.isOnline,
                enter   = slideInVertically { -it } + fadeIn(),
                exit    = slideOutVertically { -it } + fadeOut()
            ) {
                OfflineBanner(
                    pendingCount     = state.pendingCount,
                    markersFromCache = state.markersFromCache,
                    markerCount      = state.markers.size
                )
            }
        }

        // ── Panel bawah ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            AnimatedVisibility(
                visible = state.selectedMarker != null,
                enter   = slideInVertically { it / 2 } + fadeIn(),
                exit    = slideOutVertically { it / 2 } + fadeOut()
            ) {
                state.selectedMarker?.let { m ->
                    MarkerInfoCard(marker = m, onDismiss = { vm.selectMarker(null) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                SmallFloatingActionButton(
                    onClick        = { vm.toggleWeatherCard() },
                    containerColor = Color.White,
                    contentColor   = GreenPrimary,
                    modifier       = Modifier.padding(bottom = 6.dp)
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
                    isOnline       = state.isOnline,
                    cachedWeatherAt = state.cachedWeatherAt,
                    onRetry        = { vm.loadWeather() }
                )
            }
        }
    }
}

// ─── Offline Banner ───────────────────────────────────────────────────────────
@Composable
fun OfflineBanner(
    pendingCount    : Int,
    markersFromCache: Boolean = false,
    markerCount     : Int     = 0
) {
    Surface(
        color          = Color(0xFF212121).copy(alpha = 0.88f),
        shape          = RoundedCornerShape(50),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint     = Color(0xFFFFCC02),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = when {
                    pendingCount > 0 -> "Offline · $pendingCount laporan antri"
                    markersFromCache -> "Offline · $markerCount marker dari cache"
                    else             -> "Peta offline — tile dari cache"
                },
                color      = Color.White,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (pendingCount > 0) {
                Surface(
                    color = Color(0xFFFFCC02),
                    shape = CircleShape
                ) {
                    Text(
                        "$pendingCount",
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF212121)
                    )
                }
            }
        }
    }
}

// ─── Info Card saat marker di-tap ─────────────────────────────────────────────
@Composable
fun MarkerInfoCard(marker: LaporanMarker, onDismiss: () -> Unit) {
    val (statusBg, statusColor) = when (marker.status) {
        "BAHAYA"  -> StatusBahayaBg  to StatusBahaya
        "WASPADA" -> StatusWaspadaBg to StatusWaspada
        else      -> StatusAmanBg    to StatusAman
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Garis warna status kiri
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(statusColor))

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            marker.namaLokasi,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = TextPrimary,
                            maxLines   = 1,
                            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            marker.status,
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color      = statusColor,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (marker.catatan.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(marker.catatan, fontSize = 12.sp, color = TextSecondary, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(marker.timestamp, fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "%.4f, %.4f".format(marker.latitude, marker.longitude),
                        fontSize = 10.sp,
                        color    = TextSecondary
                    )
                }
            }

            // Tombol tutup
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Top)) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
            }
        }
    }
}

// ─── Helper: hitung jumlah per status ─────────────────────────────────────────
@Composable
private fun markerCounts(markers: List<LaporanMarker>): Map<FilterRisiko, Int> = remember(markers) {
    mapOf(
        FilterRisiko.SEMUA   to markers.size,
        FilterRisiko.BAHAYA  to markers.count { it.status == "BAHAYA" },
        FilterRisiko.WASPADA to markers.count { it.status == "WASPADA" },
        FilterRisiko.AMAN    to markers.count { it.status == "AMAN" }
    )
}

// ─── OSMdroid MapView dengan pin berwarna ─────────────────────────────────────
@Composable
fun OsmdroidMapView(
    markers      : List<LaporanMarker>,
    userLocation : LocationData?,
    onMarkerClick: (LaporanMarker) -> Unit,
    modifier     : Modifier = Modifier
) {
    val context = LocalContext.current
    var isMapCentered by remember { mutableStateOf(false) }

    Configuration.getInstance().userAgentValue = context.packageName

    // Warna-warna pin (Compose Color → Android ARGB Int)
    val colorBahaya  = StatusBahaya.toArgb()
    val colorWaspada = StatusWaspada.toArgb()
    val colorAman    = StatusAman.toArgb()

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.0)
                controller.setCenter(GeoPoint(-7.876, 111.470))
            }
        },
        update = { mapView ->
            if (userLocation != null && !isMapCentered) {
                mapView.controller.animateTo(GeoPoint(userLocation.latitude, userLocation.longitude))
                mapView.controller.setZoom(15.0)
                isMapCentered = true
            }

            mapView.overlays.clear()

            // Marker untuk lokasi pengguna
            if (userLocation != null) {
                val userCircle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#2196F3")) // Biru
                    setStroke(5, android.graphics.Color.WHITE)
                    setSize(50, 50)
                }
                val userPin = Marker(mapView).apply {
                    position = GeoPoint(userLocation.latitude, userLocation.longitude)
                    title = "Lokasi Anda"
                    icon = userCircle
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { m, _ -> 
                        m.showInfoWindow()
                        true 
                    }
                }
                mapView.overlays.add(userPin)
            }

            markers.forEach { laporan ->
                val pinColor = when (laporan.status) {
                    "BAHAYA"  -> colorBahaya
                    "WASPADA" -> colorWaspada
                    else      -> colorAman
                }

                // Buat drawable lingkaran berwarna untuk pin
                val circle = GradientDrawable().apply {
                    shape  = GradientDrawable.OVAL
                    setColor(pinColor)
                    setStroke(4, android.graphics.Color.WHITE)
                    setSize(60, 60)
                }

                val marker = Marker(mapView).apply {
                    position = GeoPoint(laporan.latitude, laporan.longitude)
                    title    = laporan.namaLokasi
                    snippet  = "${laporan.status} · ${laporan.timestamp}"
                    icon     = circle
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(laporan)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }
            mapView.invalidate()
        },
        modifier = modifier
    )
}

// ─── Filter chip row dengan badge jumlah ──────────────────────────────────────
@Composable
fun FilterChipRow(
    current  : FilterRisiko,
    counts   : Map<FilterRisiko, Int>,
    onSelect : (FilterRisiko) -> Unit,
    modifier : Modifier = Modifier
) {
    val filters = listOf(
        FilterRisiko.SEMUA   to "Semua",
        FilterRisiko.BAHAYA  to "🔴 Bahaya",
        FilterRisiko.WASPADA to "🟠 Waspada",
        FilterRisiko.AMAN    to "🟢 Aman"
    )
    val scrollState = rememberScrollState()

    // modifier (weight) dari luar membatasi lebar, horizontalScroll memungkinkan geser
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(50))
            .border(1.dp, Divider, RoundedCornerShape(50))
            .horizontalScroll(scrollState)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        filters.forEach { (filter, label) ->
            val isSelected = current == filter
            val count      = counts[filter] ?: 0

            val chipColor = when (filter) {
                FilterRisiko.BAHAYA  -> StatusBahaya
                FilterRisiko.WASPADA -> StatusWaspada
                FilterRisiko.AMAN    -> StatusAman
                FilterRisiko.SEMUA   -> GreenPrimary
            }

            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(filter) },
                label    = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 11.sp)
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier        = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White.copy(alpha = 0.3f) else chipColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    count.toString(),
                                    fontSize   = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (isSelected) Color.White else chipColor
                                )
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor,
                    selectedLabelColor     = Color.White,
                    containerColor         = Color.Transparent,
                    labelColor             = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = isSelected,
                    borderColor         = Color.Transparent,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}


// ─── Weather Card ──────────────────────────────────────────────────────────────
@Composable
fun WeatherCard(
    weather        : WeatherData?,
    isLoading      : Boolean,
    error          : String?,
    isOnline       : Boolean,
    cachedWeatherAt: String?,
    onRetry        : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        when {
            isLoading       -> WeatherLoading()
            // Offline + ada data cache → tampilkan data lama dengan badge
            !isOnline && weather != null -> WeatherContent(
                data            = weather,
                isOffline       = true,
                cachedWeatherAt = cachedWeatherAt
            )
            // Offline + tidak ada cache → placeholder ramah
            !isOnline && weather == null -> WeatherOfflinePlaceholder()
            // Online + error + tidak ada cache
            error != null && weather == null -> WeatherError(onRetry)
            // Online + ada data (normal)
            weather != null -> WeatherContent(
                data            = weather,
                isOffline       = false,
                cachedWeatherAt = cachedWeatherAt
            )
        }
    }
}

@Composable
private fun WeatherLoading() {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GreenPrimary, strokeWidth = 2.dp)
        Text("Memuat data cuaca di lokasi Anda…", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun WeatherOfflinePlaceholder() {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            tint     = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(
                "Data cuaca tidak tersedia",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = TextPrimary
            )
            Text(
                "Sambungkan internet untuk melihat cuaca terkini",
                fontSize = 12.sp,
                color    = TextSecondary
            )
        }
    }
}

@Composable
private fun WeatherError(onRetry: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Gagal memuat data cuaca",
            color    = StatusBahaya,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text("Coba lagi", color = GreenPrimary) }
    }
}

@Composable
private fun WeatherContent(
    data            : WeatherData,
    isOffline       : Boolean = false,
    cachedWeatherAt : String? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Badge offline saat tampil data cache
        if (isOffline) {
            Surface(
                color  = Color(0xFF212121).copy(alpha = 0.08f),
                shape  = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint     = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text      = if (cachedWeatherAt != null) "Offline · data pukul $cachedWeatherAt" else "Offline · data terakhir tersimpan",
                        fontSize  = 11.sp,
                        color     = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.condition.emoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("${data.temperatureCelsius.toInt()}°C", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
                    Text(data.condition.label, color = TextSecondary, fontSize = 13.sp)
                }
            }

            val (chipBg, chipText) = when {
                data.rain > 10 || data.condition == WeatherCondition.HEAVY_RAIN ->
                    StatusBahayaBg to StatusBahaya
                data.rain > 2 || data.condition == WeatherCondition.RAIN ->
                    StatusWaspadaBg to StatusWaspada
                else ->
                    StatusAmanBg to StatusAman
            }
            Surface(color = chipBg, shape = RoundedCornerShape(8.dp)) {
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            WeatherMetric("💧", "Hujan",     "${data.rain} mm")
            WeatherMetric("💨", "Angin",     "${data.windspeedKmh.toInt()} km/h")
            WeatherMetric("🌫️", "Kelembapan", "${data.humidity}%")
        }

        if (data.rain > 5) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(color = StatusBahayaBg, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusBahaya, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Curah hujan tinggi — periksa lereng di sekitar Anda", color = StatusBahaya, fontSize = 12.sp)
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