package com.unidagontor.retakid.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidagontor.retakid.data.notification.NotifStore
import com.unidagontor.retakid.data.notification.ProximityNotifWorker
import com.unidagontor.retakid.ui.theme.*

private const val PENGATURAN_PREFS = "pengaturan_prefs"

@Composable
fun PengaturanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences(PENGATURAN_PREFS, Context.MODE_PRIVATE) }

    // ── State notifikasi ─────────────────────────────────────────────────────
    var notifBahaya  by remember { mutableStateOf(prefs.getBoolean("notif_bahaya",  true)) }
    var notifRadar   by remember { mutableStateOf(prefs.getBoolean("notif_radar",   true)) }
    var notifOffline by remember { mutableStateOf(prefs.getBoolean("notif_offline", false)) }

    // ── State preferensi akun ────────────────────────────────────────────────
    var namaPublik    by remember { mutableStateOf(prefs.getBoolean("nama_publik",  true)) }
    var kontribusiMap by remember { mutableStateOf(prefs.getBoolean("kontribusi",   true)) }
    var bahasaIdx     by remember { mutableStateOf(prefs.getInt("bahasa",           0)) }

    // Toast / snackbar state
    var toastMsg by remember { mutableStateOf<String?>(null) }

    fun saveB(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    fun saveI(key: String, v: Int)     = prefs.edit().putInt(key, v).apply()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ── Header gradient ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(horizontal = 8.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Pengaturan", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
                    Text("Notifikasi & preferensi akun", fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // ── Konten scrollable ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seksi Notifikasi
            SettingsSection(title = "Notifikasi", icon = Icons.Default.Notifications,
                sectionColor = Color(0xFF1565C0)) {

                SettingsToggle(
                    icon      = Icons.Default.Warning,
                    title     = "Peringatan Bahaya Dekat",
                    subtitle  = "Notifikasi saat ada laporan BAHAYA dalam 100m",
                    checked   = notifBahaya,
                    iconColor = StatusBahaya
                ) {
                    notifBahaya = it; saveB("notif_bahaya", it)
                    if (it) ProximityNotifWorker.schedule(context)
                }
                PengaturanDivider()

                SettingsToggle(
                    icon      = Icons.Default.TrackChanges,
                    title     = "Pemantauan Berkala",
                    subtitle  = "Pengecekan otomatis setiap ±15 menit",
                    checked   = notifRadar,
                    iconColor = Color(0xFF1565C0)
                ) { notifRadar = it; saveB("notif_radar", it) }
                PengaturanDivider()

                SettingsToggle(
                    icon      = Icons.Default.WifiOff,
                    title     = "Banner Offline",
                    subtitle  = "Tampilkan banner saat tidak ada koneksi internet",
                    checked   = notifOffline,
                    iconColor = TextSecondary
                ) { notifOffline = it; saveB("notif_offline", it) }

                if (Build.VERSION.SDK_INT >= 33) {
                    Surface(
                        color  = Color(0xFFE3F2FD),
                        shape  = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0),
                                modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Android 13+ perlu izin notifikasi. Aktifkan di Pengaturan Sistem > Aplikasi > Retak.id.",
                                fontSize = 11.sp, color = Color(0xFF1565C0), lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Seksi Preferensi Akun
            SettingsSection(title = "Preferensi Akun", icon = Icons.Default.ManageAccounts,
                sectionColor = GreenPrimary) {

                SettingsToggle(
                    icon = Icons.Default.Public, title = "Nama Publik",
                    subtitle = "Tampilkan nama Anda pada laporan yang dilihat pengguna lain",
                    checked = namaPublik, iconColor = GreenPrimary
                ) { namaPublik = it; saveB("nama_publik", it) }
                PengaturanDivider()

                SettingsToggle(
                    icon = Icons.Default.Map, title = "Kontribusi ke Peta",
                    subtitle = "Laporan Anda muncul sebagai pin di peta komunitas",
                    checked = kontribusiMap, iconColor = Color(0xFF0288D1)
                ) { kontribusiMap = it; saveB("kontribusi", it) }
                PengaturanDivider()

                // Pilih bahasa
                val bahasa = listOf("Bahasa Indonesia 🇮🇩", "Jawa Lokal 🌾", "English 🇬🇧")
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFF9C4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Translate, null, tint = Color(0xFFF57F17),
                                modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Bahasa Antarmuka", fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = TextPrimary)
                            Text(bahasa[bahasaIdx], fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bahasa.forEachIndexed { idx, label ->
                            val sel = bahasaIdx == idx
                            FilterChip(
                                selected = sel,
                                onClick  = { bahasaIdx = idx; saveI("bahasa", idx) },
                                label    = { Text(label, fontSize = 11.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenPrimary,
                                    selectedLabelColor     = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = sel,
                                    borderColor         = if (sel) GreenPrimary else Color(0xFFDDD),
                                    selectedBorderColor = GreenPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Seksi Privasi & Data
            SettingsSection(title = "Privasi & Data", icon = Icons.Default.Security,
                sectionColor = Color(0xFF6A1B9A)) {

                SettingsActionItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Hapus Riwayat Notifikasi",
                    subtitle = "Bersihkan semua notifikasi bahaya yang tersimpan",
                    iconColor = StatusBahaya
                ) {
                    NotifStore.clear(context)
                    toastMsg = "Riwayat notifikasi dihapus"
                }
                PengaturanDivider()

                SettingsActionItem(
                    icon = Icons.Default.Policy, title = "Kebijakan Privasi",
                    subtitle = "Lihat kebijakan penggunaan data Retak.id",
                    iconColor = Color(0xFF6A1B9A)
                ) { /* TODO buka URL */ }
                PengaturanDivider()

                SettingsActionItem(
                    icon = Icons.Default.Info, title = "Versi Aplikasi",
                    subtitle = "Retak.id v1.0.0 — Build Debug",
                    iconColor = TextSecondary
                ) { }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Snackbar sederhana
    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            toastMsg = null
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color    = Color(0xFF323232),
                shape    = RoundedCornerShape(50),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title        : String,
    icon         : ImageVector,
    sectionColor : Color,
    content      : @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier          = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = sectionColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = sectionColor, letterSpacing = 0.7.sp)
        }
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon      : ImageVector,
    title     : String,
    subtitle  : String,
    checked   : Boolean,
    iconColor : Color,
    onToggle  : (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GreenPrimary
            )
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon      : ImageVector,
    title     : String,
    subtitle  : String,
    iconColor : Color,
    onClick   : () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCCCCCC))
    }
}

@Composable
private fun PengaturanDivider() = HorizontalDivider(
    color    = Color(0xFFF0F0F0),
    modifier = Modifier.padding(horizontal = 16.dp)
)
