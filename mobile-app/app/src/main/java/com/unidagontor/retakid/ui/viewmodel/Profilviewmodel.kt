package com.unidagontor.retakid.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── DTO dari tabel profiles ──────────────────────────────────────────────────
@Serializable
data class ProfileDto(
    val id             : String,
    @SerialName("nama_lengkap") val namaLengkap : String = "",
    @SerialName("no_telepon")   val noTelepon   : String? = null,
    val alamat         : String? = null,
    val poin           : Int    = 0,
    val badge          : String = "Warga"
)

// ─── UI State ─────────────────────────────────────────────────────────────────
data class ProfilState(
    val namaLengkap : String  = "",
    val email       : String  = "",
    val noTelepon   : String  = "",
    val alamat      : String  = "",
    val badge       : String  = "Warga",
    val poin        : Int     = 0,
    val isLoading   : Boolean = true,
    val isOffline   : Boolean = false,  // true = tampil dari cache
    val error       : String? = null
)

// ─── Cache key constants ───────────────────────────────────────────────────────
private const val PROFIL_PREFS   = "profil_cache"
private const val KEY_NAMA       = "nama_lengkap"
private const val KEY_EMAIL      = "email"
private const val KEY_NO_TELP    = "no_telepon"
private const val KEY_ALAMAT     = "alamat"
private const val KEY_BADGE      = "badge"
private const val KEY_POIN       = "poin"

// ─── ViewModel ────────────────────────────────────────────────────────────────
class ProfilViewModel(private val prefs: SharedPreferences) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilState())
    val uiState: StateFlow<ProfilState> = _uiState.asStateFlow()

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. Load cache lokal dulu agar tidak kosong
            val cached = loadFromCache()
            if (cached != null) {
                _uiState.update { cached.copy(isLoading = true, isOffline = false) }
            }

            // 2. Fetch dari Supabase
            try {
                val supabase = SupabaseClient.client
                val user     = supabase.auth.currentUserOrNull()
                    ?: run {
                        // Jika ada cache, tampilkan itu daripada error
                        if (cached != null) {
                            _uiState.update { cached.copy(isLoading = false, isOffline = true) }
                        } else {
                            _uiState.update { it.copy(isLoading = false, isOffline = false,
                                error = null, namaLengkap = "Pengguna") }
                        }
                        return@launch
                    }

                val profile = supabase
                    .from("profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingle<ProfileDto>()

                val newState = ProfilState(
                    namaLengkap = profile.namaLengkap.ifEmpty { "Pengguna" },
                    email       = user.email ?: "",
                    noTelepon   = profile.noTelepon ?: "",
                    alamat      = profile.alamat    ?: "",
                    badge       = profile.badge,
                    poin        = profile.poin,
                    isLoading   = false,
                    isOffline   = false
                )
                saveToCache(newState)
                _uiState.update { newState }

            } catch (_: Exception) {
                // Gagal fetch (offline) → tampilkan cache, tidak tampilkan error
                if (cached != null) {
                    _uiState.update { cached.copy(isLoading = false, isOffline = true) }
                } else {
                    // Tidak ada cache sama sekali — belum pernah online
                    _uiState.update { it.copy(isLoading = false, isOffline = true,
                        namaLengkap = "Pengguna", badge = "Warga") }
                }
            }
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
            } catch (_: Exception) { /* tetap logout */ }
            clearCache()
            onSuccess()
        }
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────
    private fun saveToCache(state: ProfilState) {
        prefs.edit()
            .putString(KEY_NAMA,    state.namaLengkap)
            .putString(KEY_EMAIL,   state.email)
            .putString(KEY_NO_TELP, state.noTelepon)
            .putString(KEY_ALAMAT,  state.alamat)
            .putString(KEY_BADGE,   state.badge)
            .putInt(KEY_POIN,       state.poin)
            .apply()
    }

    private fun loadFromCache(): ProfilState? {
        val nama = prefs.getString(KEY_NAMA, null) ?: return null
        return ProfilState(
            namaLengkap = nama,
            email       = prefs.getString(KEY_EMAIL,   "") ?: "",
            noTelepon   = prefs.getString(KEY_NO_TELP, "") ?: "",
            alamat      = prefs.getString(KEY_ALAMAT,  "") ?: "",
            badge       = prefs.getString(KEY_BADGE,   "Warga") ?: "Warga",
            poin        = prefs.getInt(KEY_POIN,       0)
        )
    }

    private fun clearCache() = prefs.edit().clear().apply()

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val prefs = context.getSharedPreferences(PROFIL_PREFS, Context.MODE_PRIVATE)
            return ProfilViewModel(prefs) as T
        }
    }
}