package com.example.mainactivity.data.remote

import com.example.mainactivity.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

const val DEEP_LINK_SCHEME = "familyapp"
const val DEEP_LINK_HOST = "auth"

object SupabaseManager {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                scheme = DEEP_LINK_SCHEME
                host = DEEP_LINK_HOST
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
