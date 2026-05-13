package com.unidagontor.retakid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unidagontor.retakid.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id             : String,
    @SerialName("nama_lengkap") val namaLengkap : String = "",
    @SerialName("no_telepon")   val noTelepon   : String? = null,
    val alamat         : String? = null,
    val poin           : Int    = 0,
    val badge          : String = "Warga"
)

@Serializable
data class LaporanRiwayatDto(
    val id: String,
    @SerialName("nama_lokasi") val namaLokasi: String = "",
    val status: String = "AMAN",
    val catatan: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val terverifikasi: Int = 0
)

data class ProfilState(
    val namaLengkap : String = "",
    val email       : String = "",
    val noTelepon   : String = "",
    val alamat      : String = "",
    val badge       : String = "Warga",
    val poin        : Int = 0,
    val totalLaporan: Int = 0,
    val riwayatList : List<LaporanRiwayatDto> = emptyList(),
    val isLoading   : Boolean = true,
    val error       : String? = null
) {
    val nextBadge: String get() = when {
        totalLaporan < 5 -> "Relawan (5 laporan)"
        totalLaporan < 20 -> "Pelindung (20 laporan)"
        totalLaporan < 50 -> "Pahlawan Desa (50 laporan)"
        else -> "★ Pahlawan Desa — maksimal"
    }
}

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
                val user = supabase.auth.currentUserOrNull()
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "Sesi tidak ditemukan.") }
                        return@launch
                    }

                val profileDeferred = async { 
                    supabase.from("profiles")
                        .select { filter { eq("id", user.id) } }
                        .decodeSingle<ProfileDto>()
                }

                val riwayatDeferred = async {
                    supabase.from("laporan")
                        .select {
                            filter { eq("pelapor", user.id) }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(20)
                        }
                        .decodeList<LaporanRiwayatDto>()
                }

                val profile = profileDeferred.await()
                val riwayat = riwayatDeferred.await()
                val totalLaporan = riwayat.size

                val badge = when {
                    totalLaporan >= 50 -> "Pahlawan Desa"
                    totalLaporan >= 20 -> "Pelindung"
                    totalLaporan >= 5 -> "Relawan"
                    else -> "Pemula"
                }

                _uiState.update {
                    it.copy(
                        namaLengkap = profile.namaLengkap.ifEmpty { "Pengguna" },
                        email = user.email ?: "",
                        noTelepon = profile.noTelepon ?: "",
                        alamat = profile.alamat ?: "",
                        badge = badge,
                        poin = profile.poin,
                        totalLaporan = totalLaporan,
                        riwayatList = riwayat,
                        isLoading = false
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
            } catch (_: Exception) {
                onSuccess()
            }
        }
    }
}