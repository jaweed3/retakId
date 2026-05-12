package com.unidagontor.retakid

import android.app.Application
import com.unidagontor.retakid.data.SupabaseClient


class RetakIdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Init Supabase client sekali saat app start
        // Session tersimpan otomatis ke SharedPreferences
        SupabaseClient.init(this)
    }
}