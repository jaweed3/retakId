package com.unidagontor.retakid.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.ui.screens.LaporanItem
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

// ─── DTO dari Supabase ─────────────────────────────────────────────────────────
@Serializable
data class LaporanDto(
    val id              : String,
    @SerialName("nama_lokasi")  val namaLokasi   : String,
    val status          : String,
    val catatan         : String        = "",
    val latitude        : Double        = 0.0,
    val longitude       : Double        = 0.0,
    @SerialName("foto_url")     val fotoUrl      : String?      = null,
    val pelapor         : String        = "User",
    val terverifikasi   : Int           = 0,
    @SerialName("created_at")   val createdAt    : String       = ""
) {
    fun toLaporanItem() = LaporanItem(
        id            = id,
        namaLokasi    = namaLokasi,
        status        = status,
        catatan       = catatan,
        timestamp     = formatTimestamp(createdAt),
        pelapor       = pelapor,
        terverifikasi = terverifikasi,
        fotoUrl       = fotoUrl
    )
}

private fun formatTimestamp(iso: String): String = try {
    val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val outputFmt = SimpleDateFormat("d MMM, HH:mm", Locale("id"))
    inputFmt.parse(iso.take(19))?.let { outputFmt.format(it) } ?: iso
} catch (_: Exception) { iso }

// ─── UI State ──────────────────────────────────────────────────────────────────
data class BerandaState(
    val laporanList  : List<LaporanItem> = emptyList(),
    val isLoading    : Boolean           = false,
    val isOffline    : Boolean           = false,   // true = tampil dari cache
    val lastUpdated  : String            = "",      // waktu data terakhir diambil online
    val error        : String?           = null
)

// ─── Cache helper ──────────────────────────────────────────────────────────────
private const val BERANDA_PREFS    = "beranda_cache"
private const val KEY_LAPORAN      = "laporan_list"
private const val KEY_LAST_UPDATE  = "last_updated"

private val cacheJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ─── ViewModel ────────────────────────────────────────────────────────────────
class BerandaViewModel(private val prefs: SharedPreferences) : ViewModel() {

    private val _uiState = MutableStateFlow(BerandaState())
    val uiState: StateFlow<BerandaState> = _uiState.asStateFlow()

    init { fetchLaporan() }

    fun fetchLaporan() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {

            // 1. Tampilkan cache dulu agar UI tidak kosong
            val cached = loadCache()
            if (cached.isNotEmpty()) {
                val lastUpdate = prefs.getString(KEY_LAST_UPDATE, "") ?: ""
                _uiState.update {
                    it.copy(
                        laporanList = cached,
                        lastUpdated = lastUpdate,
                        isOffline   = false   // belum tahu, tunggu fetch
                    )
                }
            }

            // 2. Fetch dari Supabase
            try {
                val statusOrder = mapOf("BAHAYA" to 0, "WASPADA" to 1, "AMAN" to 2)
                val list = SupabaseClient.client
                    .from("laporan")
                    .select {
                        order("created_at", Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<LaporanDto>()
                    .sortedWith(
                        compareBy<LaporanDto> { statusOrder[it.status] ?: 3 }
                            .thenByDescending { it.createdAt }
                    )
                    .map { it.toLaporanItem() }

                val now = SimpleDateFormat("d MMM HH:mm", Locale("id")).format(Date())
                saveCache(list)
                prefs.edit().putString(KEY_LAST_UPDATE, now).apply()

                _uiState.update {
                    it.copy(
                        laporanList = list,
                        isLoading   = false,
                        isOffline   = false,
                        lastUpdated = now
                    )
                }
            } catch (e: Exception) {
                // Fetch gagal → mode offline dengan data cache
                val cached2 = loadCache()
                val lastUpdate = prefs.getString(KEY_LAST_UPDATE, "") ?: ""
                _uiState.update {
                    it.copy(
                        laporanList = cached2,
                        isLoading   = false,
                        isOffline   = true,
                        lastUpdated = lastUpdate,
                        error       = null  // tidak tampil error mentah
                    )
                }
            }
        }
    }

    fun konfirmasiLaporan(laporanId: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.postgrest.rpc(
                    "konfirmasi_laporan",
                    mapOf("lid" to laporanId)
                )
                fetchLaporan()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal mengkonfirmasi: periksa koneksi internet Anda.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Cache read/write ──────────────────────────────────────────────────────
    private fun saveCache(list: List<LaporanItem>) = try {
        // Simpan sebagai DTO-like json yang sederhana
        val json = cacheJson.encodeToString(list.map { item ->
            LaporanCacheItem(
                id            = item.id,
                namaLokasi    = item.namaLokasi,
                status        = item.status,
                catatan       = item.catatan,
                timestamp     = item.timestamp,
                pelapor       = item.pelapor,
                terverifikasi = item.terverifikasi,
                fotoUrl       = item.fotoUrl
            )
        })
        prefs.edit().putString(KEY_LAPORAN, json).apply()
    } catch (_: Exception) {}

    private fun loadCache(): List<LaporanItem> = try {
        val raw = prefs.getString(KEY_LAPORAN, null) ?: return emptyList()
        cacheJson.decodeFromString<List<LaporanCacheItem>>(raw).map { c ->
            LaporanItem(c.id, c.namaLokasi, c.status, c.catatan,
                c.timestamp, c.pelapor, c.terverifikasi, c.fotoUrl)
        }
    } catch (_: Exception) { emptyList() }

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val prefs = context.getSharedPreferences(BERANDA_PREFS, Context.MODE_PRIVATE)
            return BerandaViewModel(prefs) as T
        }
    }
}

// ─── Model cache serializable ─────────────────────────────────────────────────
@Serializable
private data class LaporanCacheItem(
    val id            : String,
    val namaLokasi    : String,
    val status        : String,
    val catatan       : String,
    val timestamp     : String,
    val pelapor       : String,
    val terverifikasi : Int,
    val fotoUrl       : String? = null
)