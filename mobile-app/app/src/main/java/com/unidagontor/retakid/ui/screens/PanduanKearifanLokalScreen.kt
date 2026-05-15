package com.unidagontor.retakid.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.ui.theme.*

// ─── Data model lokal kearifan ─────────────────────────────────────────────
data class TandaAlam(
    val emoji  : String,
    val judul  : String,
    val deskripsi : String,
    val tingkat   : String,   // "WASPADA" | "BAHAYA"
    val aksi      : String
)

data class PanduanSection(
    val icon  : ImageVector,
    val judul : String,
    val warna : Color,
    val items : List<TandaAlam>
)

// ─── Data konten ────────────────────────────────────────────────────────────
private val panduanSections = listOf(
    PanduanSection(
        icon  = Icons.Default.Terrain,
        judul = "Tanda di Tanah",
        warna = Color(0xFF795548),
        items = listOf(
            TandaAlam("🪨", "Retakan Memanjang di Tanah",
                "Tanah pecah membentuk garis panjang mengikuti kontur lereng. Makin lebar retakan, makin besar risiko longsor.",
                "BAHAYA", "Segera tinggalkan area dan laporkan ke RT setempat"),
            TandaAlam("💧", "Mata Air Baru atau Mendadak Kering",
                "Munculnya mata air baru di lereng atau hilangnya mata air yang ada menandakan pergerakan tanah di bawah.",
                "WASPADA", "Pantau kondisi selama 24 jam, hindari area di bawah mata air"),
            TandaAlam("🌀", "Tanah Berlumpur Tiba-tiba",
                "Jika tanah yang biasanya kering tiba-tiba menjadi berlumpur tanpa hujan, ada rembesan air dari dalam.",
                "WASPADA", "Jauhi area dan perhatikan pergerakan tanah"),
            TandaAlam("⛰️", "Lereng Berubah Bentuk",
                "Permukaan lereng terlihat menggembung atau mencekung tanpa sebab jelas — tanda tanah bergerak.",
                "BAHAYA", "Evakuasi segera, jangan kembali sebelum ada pemeriksaan ahli")
        )
    ),
    PanduanSection(
        icon  = Icons.Default.Park,
        judul = "Tanda pada Vegetasi",
        warna = Color(0xFF388E3C),
        items = listOf(
            TandaAlam("🌲", "Pohon Miring Serentak",
                "Beberapa pohon besar tampak miring ke arah yang sama secara mendadak — tanda tanah bergerak perlahan.",
                "BAHAYA", "Jauhi area pohon miring, segera laporkan"),
            TandaAlam("🌿", "Akar Pohon Terangkat",
                "Akar pohon besar tampak keluar dari tanah tanpa alasan angin — tanah di bawahnya bergerak.",
                "WASPADA", "Perhatikan arah kemiringan, siapkan jalur evakuasi"),
            TandaAlam("🍂", "Semak Gugur Serentak",
                "Tanaman semak yang biasanya hijau tiba-tiba layu bersama — bisa ada gas atau pergerakan tanah.",
                "WASPADA", "Hindari menghirup udara di area tersebut, menjauh")
        )
    ),
    PanduanSection(
        icon  = Icons.Default.Water,
        judul = "Tanda di Sungai & Air",
        warna = Color(0xFF1565C0),
        items = listOf(
            TandaAlam("🌊", "Sungai Mendadak Keruh",
                "Air sungai tiba-tiba berwarna cokelat pekat tanpa hujan — ada longsor kecil di hulu atau erosi aktif.",
                "BAHAYA", "Jauhi bantaran sungai, waspada banjir bandang"),
            TandaAlam("🐟", "Ikan Naik ke Permukaan",
                "Ikan di kolam atau sungai kecil naik ke permukaan secara massal — ada gas atau perubahan tekanan tanah.",
                "WASPADA", "Perhatikan perubahan lain di sekitar area"),
            TandaAlam("💦", "Suara Gemuruh dari Bawah Tanah",
                "Suara gemuruh atau dentuman dari dalam tanah tanpa gempa — ada rongga atau aliran air bawah tanah.",
                "BAHAYA", "Evakuasi segera seluruh penghuni di area sekitar")
        )
    ),
    PanduanSection(
        icon  = Icons.Default.Home,
        judul = "Tanda pada Bangunan",
        warna = Color(0xFFE65100),
        items = listOf(
            TandaAlam("🏠", "Retakan pada Dinding",
                "Dinding rumah retak diagonal atau melebar tiba-tiba tanpa gempa — pondasi bergerak mengikuti tanah.",
                "BAHAYA", "Keluar dari bangunan, periksa kerusakan struktural"),
            TandaAlam("🚪", "Pintu & Jendela Sulit Ditutup",
                "Kusen berubah bentuk menyebabkan pintu atau jendela tidak bisa ditutup sempurna — tanda tanah bergerak.",
                "WASPADA", "Pantau kondisi setiap hari, siapkan tas darurat"),
            TandaAlam("⚡", "Tiang Listrik Miring",
                "Tiang listrik atau pagar beton miring serentak ke satu arah tanpa ada tabrakan — tanah bergerak.",
                "BAHAYA", "Laporkan ke PLN & BPBD, jauhi tiang miring")
        )
    )
)

// ─── Screen ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanduanKearifanLokalScreen(onBack: () -> Unit) {
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor      = Color(0xFFF5F5F5),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GreenPrimary)
            ) {
                Column {
                    Row(
                        modifier          = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Panduan Kearifan Lokal", fontWeight = FontWeight.Bold,
                                fontSize = 19.sp, color = Color.White)
                            Text("Tanda alam & akses offline", fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f))
                        }
                        // Chip offline
                        Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WifiOff, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Offline", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Info banner
                    Surface(
                        color    = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Panduan ini tersedia offline. Kenali tanda alam sebagai deteksi dini tanpa alat.",
                                fontSize = 12.sp, color = Color.White, lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tagline
            item {
                Surface(
                    color  = Color(0xFFE8F5E9),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌱", fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Warga sebagai Sensor Pertama", fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = GreenPrimary)
                            Text("Kearifan lokal dapat mendeteksi bencana lebih cepat dari alat manapun.",
                                fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // Section cards (accordion)
            items(panduanSections) { section ->
                PanduanSectionCard(
                    section    = section,
                    isExpanded = expandedSection == section.judul,
                    onToggle   = {
                        expandedSection = if (expandedSection == section.judul) null else section.judul
                    }
                )
            }

            // Prosedur evakuasi
            item { EvaluasiCard() }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PanduanSectionCard(section: PanduanSection, isExpanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Header section
            Row(
                modifier          = Modifier.fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier.size(40.dp).clip(CircleShape)
                        .background(section.warna.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(section.icon, null, tint = section.warna, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.judul, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text("${section.items.size} tanda alam", fontSize = 12.sp, color = TextSecondary)
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = TextSecondary
                )
            }

            // Animated content
            AnimatedVisibility(visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    section.items.forEach { tanda ->
                        TandaAlamItem(tanda = tanda, sectionColor = section.warna)
                    }
                }
            }
        }
    }
}

@Composable
private fun TandaAlamItem(tanda: TandaAlam, sectionColor: Color) {
    val (badgeBg, badgeColor) = if (tanda.tingkat == "BAHAYA")
        StatusBahayaBg to StatusBahaya
    else StatusWaspadaBg to StatusWaspada

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tanda.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tanda.judul, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
            }
            Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                Text(tanda.tingkat, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(tanda.deskripsi, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        Surface(color = Color(0xFFF1F8E9), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowForward, null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(tanda.aksi, fontSize = 12.sp, color = GreenPrimary, fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun EvaluasiCard() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prosedur Evakuasi Darurat", fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            listOf(
                "1️⃣" to "Tetap tenang — panik memperburuk situasi",
                "2️⃣" to "Bawa dokumen penting & obat-obatan",
                "3️⃣" to "Gunakan jalur evakuasi yang sudah disepakati RT",
                "4️⃣" to "Jauhi lereng, pohon besar, dan tepi sungai",
                "5️⃣" to "Hubungi BPBD: 119 ext 8 | Polisi: 110"
            ).forEach { (num, teks) ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(num, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(teks, fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 18.sp)
                }
            }
        }
    }
}
