package com.presyohan.app

import android.app.Application
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseProvider {
    lateinit var client: SupabaseClient

    fun init(url: String, key: String) {
        client = createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "presyohan"
                host = "auth-callback"
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            // Default Ktor engine from dependencies; no manual HttpClient override needed
        }
    }
}

class PresyohanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseProvider.init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }
}