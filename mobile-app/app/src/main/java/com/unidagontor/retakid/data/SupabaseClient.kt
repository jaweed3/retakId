package com.unidagontor.retakid.data

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.unidagontor.retakid.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    private lateinit var _client: io.github.jan.supabase.SupabaseClient

    val client: io.github.jan.supabase.SupabaseClient
        get() {
            check(::_client.isInitialized) { "Panggil SupabaseClient.init(context) di Application.onCreate()" }
            return _client
        }

    fun init(context: Context) {
        if (::_client.isInitialized) return

        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                sessionManager = SettingsSessionManager(
                    SharedPreferencesSettings(
                        context.applicationContext.getSharedPreferences(
                            "supabase_session", Context.MODE_PRIVATE
                        )
                    )
                )
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}