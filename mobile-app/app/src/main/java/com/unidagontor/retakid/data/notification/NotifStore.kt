package com.unidagontor.retakid.data.notification

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NotifItem(
    val id         : String,
    val namaLokasi : String,
    val jarakMeter : Double,
    val timestamp  : Long = System.currentTimeMillis()
)

/** Simpan riwayat notifikasi bahaya ke SharedPreferences */
object NotifStore {

    private const val PREFS_NAME = "notif_store"
    private const val KEY_LIST   = "notif_list"
    private const val MAX_SIZE   = 50

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<NotifItem> = try {
        val raw = prefs(context).getString(KEY_LIST, "[]") ?: "[]"
        Json.decodeFromString<List<NotifItem>>(raw)
    } catch (_: Exception) { emptyList() }

    fun add(context: Context, item: NotifItem) {
        val current = getAll(context).toMutableList()
        // Hindari duplikat (id + timestamp mirip dalam 10 menit)
        val isDup = current.any { it.id == item.id && (item.timestamp - it.timestamp) < 10 * 60_000L }
        if (isDup) return
        current.add(0, item)
        if (current.size > MAX_SIZE) current.removeAt(current.lastIndex)
        prefs(context).edit().putString(KEY_LIST, Json.encodeToString(current)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LIST).apply()
    }
}
