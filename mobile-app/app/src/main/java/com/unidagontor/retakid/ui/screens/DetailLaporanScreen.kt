package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.LaporanDto
import io.github.jan.supabase.postgrest.from
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailLaporanScreen(
    laporanId: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var laporan by remember { mutableStateOf<LaporanItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(laporanId) {
        try {
            val result = SupabaseClient.client.from("laporan")
                .select { filter { eq("id", laporanId) } }
                .decodeSingle<LaporanDto>()

            laporan = LaporanItem(
                id = result.id,
                namaLokasi = result.namaLokasi,
                status = result.status,
                catatan = result.catatan,
                fotoUrl = result.fotoUrl,
                latitude = result.latitude,
                longitude = result.longitude,
                timestamp = formatDetailTimestamp(parseIsoTimestamp(result.createdAt)),
                pelapor = result.pelapor,
                terverifikasi = result.terverifikasi
            )
        } catch (_: Exception) { }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Laporan", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else if (laporan == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Laporan tidak ditemukan", color = TextSecondary)
            }
        } else {
            DetailContent(
                laporan = laporan!!,
                isConfirming = isConfirming,
                onConfirm = {
                    isConfirming = true
                    scope.launch {
                        try {
                            val supabase = SupabaseClient.client
                            val current = supabase.from("laporan")
                                .select { filter { eq("id", laporanId) } }
                                .decodeSingle<LaporanDto>()
                            supabase.from("laporan").update({
                                "terverifikasi" to (current.terverifikasi + 1)
                            }) { filter { eq("id", laporanId) } }
                        } catch (_: Exception) { }
                        onConfirm()
                    }
                },
                padding = padding
            )
        }
    }
}

@Composable
private fun DetailContent(
    laporan: LaporanItem,
    isConfirming: Boolean,
    onConfirm: () -> Unit,
    padding: PaddingValues
) {
    val context = LocalContext.current

    val (statusColor, statusBg) = when (laporan.status) {
        "BAHAYA" -> StatusBahaya to StatusBahayaBg
        "WASPADA" -> StatusWaspada to StatusWaspadaBg
        else -> StatusAman to StatusAmanBg
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        if (!laporan.fotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(laporan.fotoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto laporan",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(laporan.namaLokasi, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                }
                Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        laporan.status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            Text("Catatan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(laporan.catatan.ifBlank { "Tidak ada catatan" }, fontSize = 14.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailInfoRow(icon = Icons.Default.Person, label = "Pelapor", value = laporan.pelapor)
                DetailInfoRow(icon = Icons.Default.Schedule, label = "Waktu", value = laporan.timestamp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${"%.6f".format(laporan.latitude)}, ${"%.6f".format(laporan.longitude)}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = GreenSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Konfirmasi Warga", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${laporan.terverifikasi} konfirmasi",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = !isConfirming,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isConfirming) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Konfirmasi Laporan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

private fun parseIsoTimestamp(iso: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        sdf.parse(iso.take(19))?.time ?: 0L
    } catch (_: Exception) { 0L }
}

private fun formatDetailTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Baru saja"
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
