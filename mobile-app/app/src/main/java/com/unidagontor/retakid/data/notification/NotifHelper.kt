package com.unidagontor.retakid.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.unidagontor.retakid.MainActivity
import com.unidagontor.retakid.R

object NotifHelper {

    const val CHANNEL_ID      = "bahaya_proximity"
    const val CHANNEL_NAME    = "Peringatan Bahaya Dekat"
    const val EXTRA_OPEN_NOTIF = "open_notif_screen"

    /** Buat notification channel — panggil sekali saat app start */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan saat ada laporan BAHAYA dalam radius 100 meter"
                enableVibration(true)
                enableLights(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Kirim notifikasi push "Ada laporan BAHAYA di dekat Anda".
     * Klik notif → buka app dan langsung ke halaman Notifikasi.
     */
    fun sendProximityAlert(context: Context, namaLokasi: String, jarakMeter: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTIF, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jarakStr = if (jarakMeter < 1000)
            "%.0f m".format(jarakMeter)
        else
            "%.1f km".format(jarakMeter / 1000)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ BAHAYA DEKAT ANDA!")
            .setContentText("Laporan BAHAYA di $namaLokasi (~$jarakStr dari Anda)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Ada laporan retakan BAHAYA di '$namaLokasi' sekitar $jarakStr dari lokasi Anda saat ini. Harap berhati-hati dan cari tempat yang aman!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notif)
        } catch (_: SecurityException) { /* izin belum diberikan */ }
    }
}
