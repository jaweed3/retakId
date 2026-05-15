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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// ─── DTO (private, hanya untuk decode Supabase) ───────────────────────────────
@Serializable
private data class RiwayatDto(
    val id             : String,
    @SerialName("nama_lokasi") val namaLokasi : String,
    val status         : String,
    val catatan        : String = "",
    @SerialName("foto_url")    val fotoUrl    : String? = null,
    val terverifikasi  : Int    = 0,
    @SerialName("created_at") val createdAt   : String = ""
)

// ─── UI Model (public) ────────────────────────────────────────────────────────
data class RiwayatUiItem(
    val id            : String,
    val namaLokasi    : String,
    val status        : String,
    val catatan       : String,
    val fotoUrl       : String?,
    val terverifikasi : Int,
    val createdAt     : String
)

// ─── State ────────────────────────────────────────────────────────────────────
data class RiwayatState(
    val items     : List<RiwayatUiItem> = emptyList(),
    val isLoading : Boolean = true,
    val error     : String? = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
class RiwayatViewModel : ViewModel() {
    private val _state = MutableStateFlow(RiwayatState())
    val state: StateFlow<RiwayatState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                    ?: run {
                        _state.update { it.copy(isLoading = false, error = "Sesi tidak ditemukan") }
                        return@launch
                    }
                val list = SupabaseClient.client
                    .from("laporan")
                    .select {
                        filter { eq("user_id", userId) }
                        order("created_at", Order.DESCENDING)
                        limit(100)
                    }
                    .decodeList<RiwayatDto>()
                    .map { dto ->
                        RiwayatUiItem(
                            id            = dto.id,
                            namaLokasi    = dto.namaLokasi,
                            status        = dto.status,
                            catatan       = dto.catatan,
                            fotoUrl       = dto.fotoUrl,
                            terverifikasi = dto.terverifikasi,
                            createdAt     = dto.createdAt
                        )
                    }
                _state.update { it.copy(items = list, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Gagal memuat: ${e.message}") }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun RiwayatLaporanScreen(
    onBack    : () -> Unit,
    viewModel : RiwayatViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ── Header solid GreenPrimary ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(horizontal = 8.dp, vertical = 14.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Riwayat Laporan",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 19.sp,
                        color      = Color.White
                    )
                    Text(
                        "Semua laporan yang pernah Anda buat",
                        fontSize = 12.sp,
                        color    = Color.White.copy(alpha = 0.85f)
                    )
                }
                // Tombol refresh
                IconButton(onClick = { viewModel.load() }) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
        }

        // ── Konten ───────────────────────────────────────────────────────────
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GreenPrimary, strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Memuat riwayat laporan...", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint     = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(68.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            state.error!!,
                            color     = StatusBahaya,
                            textAlign = TextAlign.Center,
                            fontSize  = 14.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { viewModel.load() },
                            colors  = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape   = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Coba Lagi")
                        }
                    }
                }
            }

            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = null,
                                tint     = GreenPrimary.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Belum Ada Laporan",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp,
                            color      = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Laporan retakan yang Anda kirimkan\nakan muncul di sini.",
                            color     = TextSecondary,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            else -> {
                // Stats bar + list laporan
                LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stats ringkasan
                    item { RiwayatStatsBar(items = state.items) }

                    // Kartu laporan
                    items(state.items, key = { it.id }) { item ->
                        RiwayatCard(item = item)
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ─── Stats Bar ────────────────────────────────────────────────────────────────
@Composable
private fun RiwayatStatsBar(items: List<RiwayatUiItem>) {
    val total   = items.size
    val bahaya  = items.count { it.status == "BAHAYA" }
    val waspada = items.count { it.status == "WASPADA" }
    val aman    = items.count { it.status == "AMAN" }

    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip("$total",   "Total",   GreenPrimary, GreenSurface,    Modifier.weight(1f))
        StatChip("$bahaya",  "Bahaya",  StatusBahaya, StatusBahayaBg,  Modifier.weight(1f))
        StatChip("$waspada", "Waspada", StatusWaspada,StatusWaspadaBg, Modifier.weight(1f))
        StatChip("$aman",    "Aman",    StatusAman,   StatusAmanBg,    Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(
    value    : String,
    label    : String,
    color    : Color,
    bg       : Color,
    modifier : Modifier = Modifier
) {
    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(
            modifier            = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 11.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

// ─── Riwayat Card ─────────────────────────────────────────────────────────────
@Composable
private fun RiwayatCard(item: RiwayatUiItem) {
    val (statusBg, statusColor) = when (item.status) {
        "BAHAYA"  -> StatusBahayaBg  to StatusBahaya
        "WASPADA" -> StatusWaspadaBg to StatusWaspada
        else      -> StatusAmanBg    to StatusAman
    }

    val dateStr = remember(item.createdAt) {
        try {
            val parser  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val display = SimpleDateFormat("d MMM yyyy  •  HH:mm", Locale("id"))
            parser.parse(item.createdAt.take(19))?.let { display.format(it) } ?: item.createdAt
        } catch (_: Exception) { item.createdAt }
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp)),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            // Foto laporan (jika ada)
            item.fotoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model            = url,
                    contentDescription = "Foto laporan",
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale     = ContentScale.Crop
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Garis status kiri
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(statusColor)
                )

                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {

                    // Baris judul + badge status
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint     = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            item.namaLokasi,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            color      = TextPrimary,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                item.status,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color      = statusColor,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Catatan (jika ada)
                    if (item.catatan.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.catatan,
                            fontSize = 12.sp,
                            color    = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(Modifier.height(8.dp))

                    // Baris bawah: tanggal + konfirmasi
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            tint     = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            dateStr,
                            fontSize = 11.sp,
                            color    = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )

                        // Konfirmasi
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                null,
                                tint     = if (item.terverifikasi > 0) GreenPrimary else Color(0xFFCCCCCC),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${item.terverifikasi} konfirmasi",
                                fontSize = 11.sp,
                                color    = if (item.terverifikasi > 0) GreenPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
