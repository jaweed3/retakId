package com.unidagontor.retakid.data.notification

import android.content.Context
import com.unidagontor.retakid.data.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object BahayaRealtimeListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening = false

    fun start(context: Context) {
        if (isListening) return
        isListening = true

        scope.launch {
            try {
                SupabaseClient.client.realtime.connect()
            } catch (_: Exception) {
                return@launch
            }

            try {
                SupabaseClient.client.realtime.channel("bahaya-alerts")
                    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "laporan"
                    }
                    .collect { action ->
                        val record = action.record
                        val status = record["status"]?.toString()
                        if (status != "BAHAYA") return@collect

                        val id = record["id"]?.toString() ?: return@collect
                        val lokasi = record["nama_lokasi"]?.toString() ?: "Tidak diketahui"
                        val lat = (record["latitude"] as? Number)?.toDouble() ?: 0.0
                        val lon = (record["longitude"] as? Number)?.toDouble() ?: 0.0

                        NotificationHelper.showBahayaNotification(
                            context = context,
                            id = id,
                            lokasi = lokasi,
                            lat = lat,
                            lon = lon
                        )
                    }
            } catch (_: Exception) {
                isListening = false
            }
        }
    }

    fun stop() {
        if (!isListening) return
        isListening = false

        try {
            SupabaseClient.client.realtime.removeChannel("bahaya-alerts")
        } catch (_: Exception) { }
    }
}
