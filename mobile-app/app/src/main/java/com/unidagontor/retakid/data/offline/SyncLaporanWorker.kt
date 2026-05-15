package com.unidagontor.retakid.data.offline

import android.content.Context
import androidx.work.*
import com.unidagontor.retakid.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
private data class LaporanInsertDto(
    @SerialName("user_id")     val userId     : String,
    @SerialName("nama_lokasi") val namaLokasi : String,
    val status                 : String,
    val catatan                : String,
    val latitude               : Double,
    val longitude              : Double,
    @SerialName("foto_url")    val fotoUrl    : String?,
    val pelapor                : String,
    val terverifikasi          : Int = 0
)

class SyncLaporanWorker(
    appContext: Context,
    params    : WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx     = applicationContext
        val pending = OfflineQueue.getAll(ctx)

        if (pending.isEmpty()) return@withContext Result.success()

        var allSuccess = true

        for (laporan in pending) {
            // Lewati jika sudah terlalu banyak retry
            if (laporan.retryCount >= 5) {
                OfflineQueue.remove(ctx, laporan.localId)
                continue
            }
            try {
                val supabase = SupabaseClient.client

                // 1. Upload foto jika ada
                val fotoUrl: String? = laporan.fotoPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val bytes    = file.readBytes()
                        val fileName = "laporan/${UUID.randomUUID()}.jpg"
                        supabase.storage.from("laporan-images").upload(fileName, bytes) { upsert = false }
                        val url = supabase.storage.from("laporan-images").publicUrl(fileName)
                        file.delete() // hapus file lokal setelah upload
                        url
                    } else null
                }

                // 2. Insert ke Supabase
                supabase.from("laporan").insert(
                    LaporanInsertDto(
                        userId    = laporan.userId,
                        namaLokasi = laporan.namaLokasi,
                        status    = laporan.status,
                        catatan   = laporan.catatan,
                        latitude  = laporan.latitude,
                        longitude = laporan.longitude,
                        fotoUrl   = fotoUrl,
                        pelapor   = laporan.pelapor
                    )
                )

                OfflineQueue.remove(ctx, laporan.localId)

            } catch (e: Exception) {
                OfflineQueue.incrementRetry(ctx, laporan.localId)
                allSuccess = false
            }
        }

        if (allSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "sync_laporan_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncLaporanWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
