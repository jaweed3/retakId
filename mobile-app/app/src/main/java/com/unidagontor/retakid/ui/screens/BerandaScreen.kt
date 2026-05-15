package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage              // tambahkan: implementation("io.coil-kt:coil-compose:2.7.0")
import com.unidagontor.retakid.data.notification.NotifStore
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.BerandaViewModel

// ─── Data model ───────────────────────────────────────────────
data class LaporanItem(
    val id            : String,
    val namaLokasi    : String,
    val status        : String,         // "AMAN" | "WASPADA" | "BAHAYA"
    val catatan       : String,
    val timestamp     : String,
    val pelapor       : String,
    val terverifikasi : Int,
    val fotoUrl       : String? = null  // URL gambar dari Supabase Storage
)

// ─── Beranda Tab ──────────────────────────────────────────────
@Composable
fun BerandaTab(
    viewModel    : BerandaViewModel = viewModel(),
    onNotifClick : () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notifCount = remember { NotifStore.getAll(context).size }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {

        BerandaHeader(notifCount = notifCount, onNotifClick = onNotifClick)
        StatsSummaryRow(laporan = uiState.laporanList)

        uiState.error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.fetchLaporan() }) { Text("Coba Lagi") }
                }
            }
        }

        if (uiState.isLoading && uiState.laporanList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Laporan Terbaru", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GreenPrimary)
                        } else {
                            TextButton(onClick = { viewModel.fetchLaporan() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Perbarui", fontSize = 12.sp)
                            }
                        }
                    }
                }
                items(uiState.laporanList, key = { it.id }) { laporan ->
                    LaporanCard(
                        laporan       = laporan,
                        onKonfirmasi  = { viewModel.konfirmasiLaporan(laporan.id) }
                    )
                }
            }
        }
    }
}

// ─── Header ──────────────────────────────────────────────
@Composable
fun BerandaHeader(notifCount: Int = 0, onNotifClick: () -> Unit = {}) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(GreenPrimary)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Retak.id", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Jenangan, Ponorogo", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        // Tombol notifikasi dengan badge merah jika ada notif bahaya
        Box {
            IconButton(onClick = onNotifClick) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifikasi", tint = Color.White)
            }
            if (notifCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (notifCount > 9) "9+" else notifCount.toString(),
                        fontSize   = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            }
        }
    }
}

// ─── Stats ────────────────────────────────────────────────────
@Composable
fun StatsSummaryRow(laporan: List<LaporanItem>) {
    val bahaya  = laporan.count { it.status == "BAHAYA" }
    val waspada = laporan.count { it.status == "WASPADA" }
    val aman    = laporan.count { it.status == "AMAN" }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatChip(count = bahaya,  label = "Bahaya",  color = StatusBahaya,  bg = StatusBahayaBg)
        StatChip(count = waspada, label = "Waspada", color = StatusWaspada, bg = StatusWaspadaBg)
        StatChip(count = aman,    label = "Aman",    color = StatusAman,    bg = StatusAmanBg)
    }
}

@Composable
fun StatChip(count: Int, label: String, color: Color, bg: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = color)
    }
}

// ─── Laporan Card ─────────────────────────────────────────────
@Composable
fun LaporanCard(laporan: LaporanItem, onKonfirmasi: () -> Unit) {
    val (statusBg, statusColor) = when (laporan.status) {
        "BAHAYA"  -> StatusBahayaBg  to StatusBahaya
        "WASPADA" -> StatusWaspadaBg to StatusWaspada
        else      -> StatusAmanBg    to StatusAman
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // ── Foto (full width di atas) ──────────────────────────
            laporan.fotoUrl?.let { url ->
                AsyncImage(
                    model              = url,
                    contentDescription = "Foto laporan",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )
            }

            // ── Garis status + konten ──────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Garis warna status di sisi kiri
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(statusColor)
                )

                // Konten teks
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Baris 1: Lokasi + Badge status
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint     = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                laporan.namaLokasi,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp,
                                color      = TextPrimary,
                                maxLines   = 1,
                                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = laporan.status, bg = statusBg, color = statusColor)
                    }

                    // Baris 2: Catatan
                    if (laporan.catatan.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            laporan.catatan,
                            fontSize = 12.sp,
                            color    = TextSecondary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Baris 3: Pelapor + Waktu | Tombol konfirmasi
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Info pelapor & waktu
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint     = TextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    laporan.pelapor,
                                    fontSize = 11.sp,
                                    color    = TextSecondary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(laporan.timestamp, fontSize = 11.sp, color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Tombol konfirmasi — lebar cukup, teks tidak wrap
                        Surface(
                            onClick = onKonfirmasi,
                            color   = GreenSurface,
                            shape   = RoundedCornerShape(50.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint     = GreenPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${laporan.terverifikasi} konfirmasi",
                                    fontSize   = 11.sp,
                                    color      = GreenPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines   = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, bg: Color, color: Color) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            status,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color      = color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}