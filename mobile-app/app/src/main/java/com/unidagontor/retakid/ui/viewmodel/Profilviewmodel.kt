package com.unidagontor.retakid.ui.viewmodel

import androidx.lifecycle.ViewModel
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

// ─── DTO dari tabel profiles ─────────────────────────────────
@Serializable
data class ProfileDto(
    val id             : String,
    @SerialName("nama_lengkap") val namaLengkap : String = "",
    @SerialName("no_telepon")   val noTelepon   : String? = null,
    val alamat         : String? = null,
    val poin           : Int    = 0,
    val badge          : String = "Warga"
)

// ─── UI State ─────────────────────────────────────────────────
data class ProfilState(
    val namaLengkap : String  = "",
    val email       : String  = "",
    val noTelepon   : String  = "",
    val alamat      : String  = "",
    val badge       : String  = "Warga",
    val poin        : Int     = 0,
    val isLoading   : Boolean = true,
    val error       : String? = null
)

// ─── ViewModel ────────────────────────────────────────────────
class ProfilViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilState())
    val uiState: StateFlow<ProfilState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val supabase = SupabaseClient.client
                val user     = supabase.auth.currentUserOrNull()
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "Sesi tidak ditemukan.") }
                        return@launch
                    }

                val profile = supabase
                    .from("profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingle<ProfileDto>()

                _uiState.update {
                    it.copy(
                        namaLengkap = profile.namaLengkap.ifEmpty { "Pengguna" },
                        email       = user.email ?: "",
                        noTelepon   = profile.noTelepon ?: "",
                        alamat      = profile.alamat    ?: "",
                        badge       = profile.badge,
                        poin        = profile.poin,
                        isLoading   = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Gagal memuat profil: ${e.message}") }
            }
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
                onSuccess()
            } catch (e: Exception) {
                // Tetap logout dari sisi client meski network error
                onSuccess()
            }
        }
    }
}