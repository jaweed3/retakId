package com.unidagontor.retakid.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.unidagontor.retakid.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// DTO minimal laporan BAHAYA dari Supabase
@Serializable
private data class BahayaDto(
    val id           : String,
    @SerialName("nama_lokasi") val namaLokasi: String,
    val status       : String,
    val latitude     : Double,
    val longitude    : Double,
    @SerialName("created_at") val createdAt : String = ""
)

/**
 * Worker periodik — jalan setiap ~15 menit meski aplikasi ditutup.
 * Langkah:
 *  1. Ambil lokasi terakhir user
 *  2. Fetch laporan BAHAYA dari Supabase
 *  3. Filter yang jaraknya ≤ 100 m
 *  4. Kirim notifikasi sistem + simpan ke NotifStore
 */
class ProximityNotifWorker(
    appContext: Context,
    params    : WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME    = "proximity_notif_worker"
        const val RADIUS_METER         = 100.0

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProximityNotifWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext

        // 1. Cek izin lokasi
        val hasLocation = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) return@withContext Result.success() // skip, tak bisa ambil lokasi

        // 2. Ambil lokasi user
        val userLoc = getUserLocation(ctx) ?: return@withContext Result.retry()

        // 3. Fetch laporan BAHAYA dari Supabase
        val bahayaList = try {
            SupabaseClient.client
                .from("laporan")
                .select {
                    filter { eq("status", "BAHAYA") }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<BahayaDto>()
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        // 4. Hitung jarak & kirim notif untuk yang ≤ 100 m
        for (laporan in bahayaList) {
            val results = FloatArray(1)
            Location.distanceBetween(
                userLoc.latitude, userLoc.longitude,
                laporan.latitude, laporan.longitude,
                results
            )
            val jarakMeter = results[0].toDouble()

            if (jarakMeter <= RADIUS_METER) {
                val item = NotifItem(
                    id         = laporan.id,
                    namaLokasi = laporan.namaLokasi,
                    jarakMeter = jarakMeter
                )
                // Simpan ke riwayat
                NotifStore.add(ctx, item)
                // Kirim push notification
                NotifHelper.sendProximityAlert(ctx, laporan.namaLokasi, jarakMeter)
            }
        }

        Result.success()
    }

    /** Ambil lokasi terakhir / request single update dengan timeout 10 detik */
    @SuppressLint("MissingPermission")
    private suspend fun getUserLocation(context: Context): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // Coba lastLocation dulu (cepat)
        val last = withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }
        if (last != null) return last

        // Kalau null, request fresh location dengan timeout
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000)
                    .setMaxUpdates(1)
                    .build()

                val cb = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        cont.resume(result.lastLocation)
                    }
                }

                fusedClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
                cont.invokeOnCancellation { fusedClient.removeLocationUpdates(cb) }
            }
        }
    }
}
