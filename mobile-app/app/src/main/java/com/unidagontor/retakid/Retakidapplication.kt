package com.unidagontor.retakid

import android.app.Application
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.data.notification.NotifHelper
import com.unidagontor.retakid.data.notification.ProximityNotifWorker


class RetakIdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Init Supabase client sekali saat app start
        // Session tersimpan otomatis ke SharedPreferences
        SupabaseClient.init(this)

        // Buat notification channel (wajib sebelum kirim notif di Android 8+)
        NotifHelper.createChannel(this)

        // Jadwalkan worker pengecekan proximity BAHAYA (berjalan tiap ~15 menit)
        ProximityNotifWorker.schedule(this)
    }
}
