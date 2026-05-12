package com.unidagontor.retakid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unidagontor.retakid.ui.screens.LaporanItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class BerandaState(
    val laporanList: List<LaporanItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class BerandaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BerandaState())
    val uiState: StateFlow<BerandaState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    init {
        fetchLaporan()
    }

    fun fetchLaporan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            db.collection("laporan")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            try {
                                val id = doc.id
                                val namaLokasi = doc.getString("namaLokasi") ?: "Tanpa Nama"
                                val status = doc.getString("status") ?: "AMAN"
                                val catatan = doc.getString("catatan") ?: ""
                                val timestampLong = doc.get("timestamp")?.let {
                                    when (it) {
                                        is Long -> it
                                        is Double -> it.toLong()
                                        else -> 0L
                                    }
                                } ?: 0L
                                val pelapor = doc.getString("pelapor") ?: "User"
                                val terverifikasi = doc.get("terverifikasi")?.let {
                                    when (it) {
                                        is Long -> it.toInt()
                                        is Double -> it.toInt()
                                        else -> 0
                                    }
                                } ?: 0

                                LaporanItem(
                                    id = id,
                                    namaLokasi = namaLokasi,
                                    status = status,
                                    catatan = catatan,
                                    timestamp = formatTimestamp(timestampLong),
                                    pelapor = pelapor,
                                    terverifikasi = terverifikasi
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        _uiState.value = BerandaState(laporanList = items, isLoading = false)
                    }
                }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Baru saja"
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60000 -> "Baru saja"
            diff < 3600000 -> "${diff / 60000} mnt lalu"
            diff < 86400000 -> "${diff / 3600000} jam lalu"
            else -> {
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
