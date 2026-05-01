package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.ui.theme.*

// ─── Data model sementara (nanti dari Firestore) ────────────────
data class LaporanItem(
    val id: String,
    val namaLokasi: String,
    val status: String,          // "AMAN" | "WASPADA" | "BAHAYA"
    val catatan: String,
    val timestamp: String,
    val pelapor: String,
    val terverifikasi: Int       // jumlah konfirmasi warga
)

val dummyLaporan = listOf(
    LaporanItem("1", "Lereng Jenangan Utara",  "BAHAYA",  "Retakan lebar ±15cm, tanah bergerak",       "30 mnt lalu",  "Adam T.",   5),
    LaporanItem("2", "Jalan Perbatasan Ngrayun","WASPADA", "Retakan memanjang di bahu jalan",           "2 jam lalu",   "Budi S.",   2),
    LaporanItem("3", "Tambang Timur RT 04",    "BAHAYA",  "Suara gemuruh bawah tanah, warga mengungsi","1 jam lalu",   "Citra M.",  8),
    LaporanItem("4", "Desa Setono",            "AMAN",    "Retakan kecil akibat kering musim",         "5 jam lalu",   "Dewi R.",   1),
    LaporanItem("5", "Lereng Selatan Pos 2",   "WASPADA", "Pohon miring, tanah lembek pasca hujan",    "3 jam lalu",   "Eko P.",    3),
)



@Composable
fun BerandaTab() {
    Column(modifier = Modifier.fillMaxSize().background(Surface)) {

        // Header
        BerandaHeader()

        // Ringkasan statistik
        StatsSummaryRow(laporan = dummyLaporan)

        // Feed laporan
        LazyColumn(
            contentPadding       = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement  = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Laporan Terbaru",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = TextPrimary,
                    modifier   = Modifier.padding(bottom = 4.dp)
                )
            }
            items(dummyLaporan) { laporan ->
                LaporanCard(laporan = laporan)
            }
        }
    }
}


@Composable
fun BerandaHeader() {
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
        IconButton(onClick = { /* buka notifikasi */ }) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifikasi", tint = Color.White)
        }
    }
}


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


@Composable
fun LaporanCard(laporan: LaporanItem) {
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
        // Garis warna status di sisi kiri
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Column(modifier = Modifier.padding(14.dp)) {

                // Baris: lokasi + badge status
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            color      = TextPrimary
                        )
                    }
                    StatusBadge(status = laporan.status, bg = statusBg, color = statusColor)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Catatan
                Text(laporan.catatan, fontSize = 13.sp, color = TextSecondary, maxLines = 2)

                Spacer(modifier = Modifier.height(10.dp))

                // Baris: pelapor + waktu + verifikasi
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                        Text(" ${laporan.pelapor}", fontSize = 12.sp, color = TextSecondary)
                        Text("  ·  ${laporan.timestamp}", fontSize = 12.sp, color = TextSecondary)
                    }
                    // Tombol verifikasi
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(GreenSurface)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(13.dp))
                        Text(" ${laporan.terverifikasi} konfirmasi", fontSize = 11.sp, color = GreenPrimary)
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