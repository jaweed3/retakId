package com.unidagontor.retakid.data.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─── Model laporan pending ────────────────────────────────────────────────────
@Serializable
data class PendingLaporan(
    val localId     : String = java.util.UUID.randomUUID().toString(),
    val userId      : String,
    val namaLokasi  : String,
    val status      : String,
    val catatan     : String,
    val latitude    : Double,
    val longitude   : Double,
    val fotoPath    : String?,
    val pelapor     : String,
    val createdAt   : Long = System.currentTimeMillis(),
    val retryCount  : Int  = 0
)

// ─── SharedPreferences-based Queue ───────────────────────────────────────────
object OfflineQueue {
    private const val PREF_NAME = "retakid_offline_queue"
    private const val KEY_QUEUE = "pending_laporan_list"

    private val json  = Json { ignoreUnknownKeys = true }

    // StateFlow untuk observe count
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: Flow<Int> = _pendingCount.asStateFlow()

    fun init(context: Context) {
        _pendingCount.value = 0  // reset dulu, update async di bawah
        kotlinx.coroutines.MainScope().launch {
            _pendingCount.value = getAll(context).size
        }
    }

    suspend fun enqueue(context: Context, laporan: PendingLaporan) = withContext(Dispatchers.IO) {
        val list = getAll(context).toMutableList()
        list.add(laporan)
        save(context, list)
        _pendingCount.value = list.size
    }

    suspend fun getAll(context: Context): List<PendingLaporan> = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_QUEUE, null) ?: return@withContext emptyList()
        return@withContext try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    suspend fun remove(context: Context, localId: String) = withContext(Dispatchers.IO) {
        val list = getAll(context).filter { it.localId != localId }
        save(context, list)
        _pendingCount.value = list.size
    }

    suspend fun incrementRetry(context: Context, localId: String) = withContext(Dispatchers.IO) {
        val list = getAll(context).map { if (it.localId == localId) it.copy(retryCount = it.retryCount + 1) else it }
        save(context, list)
    }

    private fun save(context: Context, list: List<PendingLaporan>) {
        prefs(context).edit().putString(KEY_QUEUE, json.encodeToString(list)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

// Alias agar OfflineDatabase di kode lama tidak break
typealias OfflineDatabase = OfflineQueue
