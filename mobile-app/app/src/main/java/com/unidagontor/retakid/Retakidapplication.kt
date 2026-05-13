package com.unidagontor.retakid

import android.app.Application
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.data.notification.BahayaRealtimeListener
import com.unidagontor.retakid.data.notification.NotificationHelper


class RetakIdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.init(this)
        NotificationHelper.createChannel(this)
        BahayaRealtimeListener.start(this)
    }
}