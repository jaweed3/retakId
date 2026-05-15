package com.unidagontor.retakid

import android.app.Application
import com.unidagontor.retakid.data.SupabaseClient
import com.unidagontor.retakid.data.elevation.ElevationService

class RetakIdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.init(this)
        ElevationService.initFromAssets(this)
    }
}