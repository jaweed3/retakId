package com.unidagontor.retakid.data.offline

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cache persisten ringan berbasis SharedPreferences untuk data geo-saintifik.
 *
 * Digunakan oleh ElevationService, SoilTypeService, dan WeatherApiService
 * sebagai lapisan cache disk lintas sesi (bertahan saat app dimatikan/dibuka).
 *
 * Format key: "<namespace>:<cacheKey>"
 * Format value: JSON object dengan "value" (data) dan "ts" (timestamp Unix ms)
 */
object HybridCache {

    private const val PREF_NAME = "retakid_hybrid_cache"

    // ── Namespace ─────────────────────────────────────────────────────────────
    const val NS_ELEVATION = "elev"
    const val NS_SOIL      = "soil"
    const val NS_WEATHER   = "wthr"

    // ── TTL (Time-To-Live) per namespace ─────────────────────────────────────
    /** Elevasi: 30 hari (data DEM sangat stabil) */
    private const val TTL_ELEVATION_MS = 30L * 24 * 60 * 60 * 1000

    /** Jenis tanah: 90 hari (hampir tidak berubah) */
    private const val TTL_SOIL_MS = 90L * 24 * 60 * 60 * 1000

    /** Cuaca: 6 jam */
    const val TTL_WEATHER_MS = 6L * 60 * 60 * 1000

    // ── API ───────────────────────────────────────────────────────────────────

    /** Simpan nilai string ke cache dengan namespace tertentu */
    suspend fun put(context: Context, namespace: String, key: String, value: String) =
        withContext(Dispatchers.IO) {
            val entry = JSONObject().apply {
                put("value", value)
                put("ts", System.currentTimeMillis())
            }.toString()
            prefs(context).edit().putString("$namespace:$key", entry).apply()
        }

    /**
     * Ambil nilai dari cache. Kembalikan null jika:
     * - Tidak ada entry
     * - Entry sudah melewati TTL yang sesuai namespace
     */
    suspend fun get(context: Context, namespace: String, key: String): String? =
        withContext(Dispatchers.IO) {
            val raw = prefs(context).getString("$namespace:$key", null) ?: return@withContext null
            try {
                val json = JSONObject(raw)
                val ts   = json.getLong("ts")
                val ttl  = ttlForNamespace(namespace)
                if (System.currentTimeMillis() - ts > ttl) return@withContext null
                json.getString("value")
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Sama seperti [get] tapi mengembalikan data MESKI kadaluarsa (untuk cuaca offline).
     * Kembalikan null hanya jika benar-benar tidak ada entry sama sekali.
     */
    suspend fun getIgnoreTtl(context: Context, namespace: String, key: String): String? =
        withContext(Dispatchers.IO) {
            val raw = prefs(context).getString("$namespace:$key", null) ?: return@withContext null
            try { JSONObject(raw).getString("value") } catch (_: Exception) { null }
        }

    /** Kembalikan timestamp (Unix ms) kapan entry terakhir disimpan, atau null */
    suspend fun getTimestamp(context: Context, namespace: String, key: String): Long? =
        withContext(Dispatchers.IO) {
            val raw = prefs(context).getString("$namespace:$key", null) ?: return@withContext null
            try { JSONObject(raw).getLong("ts") } catch (_: Exception) { null }
        }

    /** Hapus semua entry cache */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun ttlForNamespace(namespace: String): Long = when (namespace) {
        NS_ELEVATION -> TTL_ELEVATION_MS
        NS_SOIL      -> TTL_SOIL_MS
        NS_WEATHER   -> TTL_WEATHER_MS
        else         -> TTL_WEATHER_MS   // default konservatif
    }
}
