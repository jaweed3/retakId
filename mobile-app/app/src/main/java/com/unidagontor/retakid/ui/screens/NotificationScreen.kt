package com.unidagontor.retakid.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.data.notification.NotifItem
import com.unidagontor.retakid.data.notification.NotifStore
import com.unidagontor.retakid.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Warna kustom halaman notif ───────────────────────────────────────────────
private val RedDark    = Color(0xFFB71C1C)
private val RedMid     = Color(0xFFD32F2F)
private val RedLight   = Color(0xFFEF5350)
private val OrangeTint = Color(0xFFFFF3F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var notifList by remember { mutableStateOf(NotifStore.getAll(context)) }

    Scaffold(
        // Tidak ada bottomBar — sengaja dikosongkan
        containerColor = Color(0xFFF7F7F7)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Gradient Header ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(RedDark, RedMid))
                    )
                    .padding(top = 16.dp, bottom = 20.dp, start = 8.dp, end = 16.dp)
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol kembali
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Notifikasi Bahaya",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp,
                            color      = Color.White
                        )
                        Text(
                            "Laporan BAHAYA dalam radius 100 m",
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Tombol hapus semua
                    if (notifList.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                NotifStore.clear(context)
                                notifList = emptyList()
                            }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus semua", tint = Color.White)
                        }
                    }
                }
            }

            // ── Konten ────────────────────────────────────────────────────
            if (notifList.isEmpty()) {
                EmptyNotifState()
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary banner
                    item {
                        SummaryBanner(count = notifList.size)
                    }

                    itemsIndexed(
                        items = notifList,
                        key   = { _, item -> "${item.id}_${item.timestamp}" }
                    ) { _, item ->
                        NotifCard(item = item)
                    }
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────
@Composable
private fun EmptyNotifState() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            // Lingkaran animasi pulsing
            Box(
                modifier = Modifier
                    .size((100 * scale).dp)
                    .clip(CircleShape)
                    .background(StatusBahaya.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint     = StatusBahaya.copy(alpha = 0.35f),
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Tidak Ada Peringatan",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Peringatan akan muncul secara otomatis\nketika ada laporan BAHAYA dalam\nradius 100 meter dari Anda.",
                fontSize  = 13.sp,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info chip
            Surface(
                color = StatusAmanBg,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = StatusAman,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Area Anda aman saat ini",
                        fontSize   = 13.sp,
                        color      = StatusAman,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─── Summary Banner ───────────────────────────────────────────────────────────
@Composable
private fun SummaryBanner(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(RedMid, RedLight)),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$count Peringatan Terdeteksi",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Text(
                        "Laporan BAHAYA pernah terdeteksi dekat Anda",
                        fontSize = 11.sp,
                        color    = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ─── Notif Card ───────────────────────────────────────────────────────────────
@Composable
private fun NotifCard(item: NotifItem) {
    val jarakStr = if (item.jarakMeter < 1000)
        "~%.0f meter".format(item.jarakMeter)
    else
        "~%.1f km".format(item.jarakMeter / 1000)

    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("EEEE, d MMM yyyy • HH:mm", Locale("id")).format(Date(item.timestamp))
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp)),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {

            // Garis merah kiri (tebal & rounded)
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(listOf(RedMid, RedLight)),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {

                // Baris atas: ikon + judul + badge jarak
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ikon lingkaran
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(OrangeTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint     = RedMid,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.namaLokasi,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = TextPrimary,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            timeStr,
                            fontSize = 11.sp,
                            color    = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Badge BAHAYA
                    Surface(
                        color = RedMid,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "BAHAYA",
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Baris bawah: jarak info
                Surface(
                    color  = OrangeTint,
                    shape  = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NearMe,
                            contentDescription = null,
                            tint     = RedMid,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Terdeteksi $jarakStr dari lokasi Anda",
                            fontSize   = 12.sp,
                            color      = RedDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
