package com.unidagontor.retakid.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unidagontor.retakid.data.elevation.TileRegionCalculator
import com.unidagontor.retakid.ui.theme.GreenPrimary
import com.unidagontor.retakid.ui.theme.StatusAman
import com.unidagontor.retakid.ui.viewmodel.ElevasiOnboardingState
import com.unidagontor.retakid.ui.viewmodel.ElevasiOnboardingViewModel

@Composable
fun ElevasiOnboardingScreen(
    onFinished: () -> Unit,
    vm: ElevasiOnboardingViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is ElevasiOnboardingState.Done) {
            onFinished()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        when (val s = state) {
            is ElevasiOnboardingState.Idle -> IdleContent(vm)
            is ElevasiOnboardingState.RequestingPermission -> RequestingPermissionContent(vm)
            is ElevasiOnboardingState.Detecting -> DetectingContent(s.message)
            is ElevasiOnboardingState.FoundLocation -> FoundLocationContent(s, vm)
            is ElevasiOnboardingState.Downloading -> DownloadingContent(s)
            is ElevasiOnboardingState.Ready -> ReadyContent(s, vm)
            is ElevasiOnboardingState.Error -> ErrorContent(s.message, vm)
            is ElevasiOnboardingState.Done -> { }
        }
    }
}

@Composable
private fun IdleContent(vm: ElevasiOnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Peta Elevasi Offline",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = GreenPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Aktifkan GPS untuk mengunduh peta elevasi daerah Anda. " +
                    "Tanpa internet pun analisis risiko longsor tetap akurat.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ukuran: ~11 MB • 4 file • Sekali unduh",
            textAlign = TextAlign.Center,
            color = Color.LightGray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { vm.requestPermissionAndDetect() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Deteksi Lokasi Saya", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { vm.skip() }) {
            Text("Lewati", color = Color.Gray)
        }
    }
}

@Composable
private fun RequestingPermissionContent(vm: ElevasiOnboardingViewModel) {
    val launcher = rememberLauncherForActivityResult(
        contracts = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        vm.onPermissionResult(fine || coarse)
    }

    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mengakses GPS...", fontSize = 18.sp, color = Color.Gray)
    }
}

@Composable
private fun DetectingContent(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = GreenPrimary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color.Gray, fontSize = 16.sp)
    }
}

@Composable
private fun FoundLocationContent(
    state: ElevasiOnboardingState.FoundLocation,
    vm: ElevasiOnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Lokasi Terdeteksi",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = GreenPrimary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            state.daerah,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333)
        )
        Text(
            TileRegionCalculator.formatCoordinates(
                state.location.latitude,
                state.location.longitude
            ),
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Area cakupan:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                Text(
                    formatTiles(TileRegionCalculator.tilesForLocation(
                        state.location.latitude,
                        state.location.longitude
                    )),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Download peta elevasi untuk akses offline. " +
                    "Sekali unduh, data bisa dipakai tanpa internet.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.startDownload() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Download Peta Elevasi", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { vm.skip() }) {
            Text("Lewati — pakai data awal", color = Color.Gray)
        }
    }
}

@Composable
private fun DownloadingContent(state: ElevasiOnboardingState.Downloading) {
    val progress = if (state.total > 0) state.progress.toFloat() / state.total else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Mengunduh Peta Elevasi",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = GreenPrimary
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "${state.progress} / ${state.total} file",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        if (state.tileName.isNotBlank()) {
            Text(
                state.tileName,
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = GreenPrimary,
            trackColor = Color(0xFFE0E0E0)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Jangan tutup halaman ini sampai selesai.",
            color = Color.LightGray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ReadyContent(state: ElevasiOnboardingState.Ready, vm: ElevasiOnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Siap!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = StatusAman
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.totalFailed == 0) "Semua peta elevasi berhasil diunduh."
            else "${state.totalDownloaded} file berhasil, ${state.totalFailed} gagal.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Analisis risiko longsor bisa berjalan offline.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { vm.finish() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Lanjutkan", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorContent(message: String, vm: ElevasiOnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Gagal",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))
            Button(
                    onClick = { vm.requestPermissionAndDetect() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Coba Lagi", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { vm.skip() }) {
            Text("Lewati", color = Color.Gray)
        }
    }
}

private fun formatTiles(tiles: List<String>): String {
    return tiles.chunked(2).joinToString("\n") { row ->
        row.joinToString(" • ")
    }
}
