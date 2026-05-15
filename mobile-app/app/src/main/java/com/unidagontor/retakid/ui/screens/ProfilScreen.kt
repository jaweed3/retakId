package com.unidagontor.retakid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unidagontor.retakid.ui.theme.GreenPrimary
import com.unidagontor.retakid.ui.viewmodel.ProfilViewModel

@Composable
fun ProfilScreen(
    onLogout: () -> Unit,
    viewModel: ProfilViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ── HEADER PROFIL ─────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = GreenPrimary,
            shape    = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        ) {
            Column(
                modifier            = Modifier.padding(top = 40.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier        = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(36.dp),
                            color       = GreenPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint     = GreenPrimary,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Nama & email langsung dari Supabase
                Text(
                    text       = if (uiState.isLoading) "Memuat..." else uiState.namaLengkap,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Text(
                    text     = uiState.email,
                    fontSize = 14.sp,
                    color    = Color.White.copy(alpha = 0.8f)
                )

                // Tampilkan error jika ada, dengan tombol retry
                uiState.error?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(err, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.loadProfile() }) {
                            Text("Coba lagi", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── BADGE & POIN dari tabel profiles ──────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BadgeInfoCard(
                        icon  = Icons.Default.Star,
                        title = "Badge",
                        value = uiState.badge
                    )
                    BadgeInfoCard(
                        icon  = Icons.Default.MilitaryTech,
                        title = "Poin",
                        value = uiState.poin.toString()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── MENU ITEM ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxHeight()
        ) {
            MenuListItem(
                icon     = Icons.Default.History,
                title    = "Riwayat Laporan",
                subtitle = "Foto & status laporan lama",
                onClick  = { /* Todo: Navigasi ke Riwayat */ }
            )
            MenuListItem(
                icon     = Icons.Default.MenuBook,
                title    = "Panduan Kearifan Lokal",
                subtitle = "Tanda alam & akses offline",
                onClick  = { /* Todo: Navigasi ke Panduan */ }
            )
            MenuListItem(
                icon     = Icons.Default.Settings,
                title    = "Pengaturan",
                subtitle = "Notifikasi & preferensi akun",
                onClick  = { /* Todo: Navigasi ke Pengaturan */ }
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── TOMBOL LOGOUT ─────────────────────────────────
            // signOut() adalah suspend function, ViewModel yang handle coroutine-nya
            OutlinedButton(
                onClick  = { viewModel.signOut(onLogout) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE53935))
                )
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keluar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Composable helpers (tidak berubah) ────────────────────────

@Composable
fun BadgeInfoCard(icon: ImageVector, title: String, value: String) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = Color.White.copy(alpha = 0.2f),
        modifier = Modifier.width(130.dp)
    ) {
        Row(
            modifier         = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun MenuListItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier         = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier        = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = GreenPrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}