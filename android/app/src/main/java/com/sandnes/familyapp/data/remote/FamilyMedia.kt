package com.sandnes.familyapp.data.remote

import com.sandnes.familyapp.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.minutes

object FamilyMedia {
    private val signedUrlLifetime = 5.minutes
    val resolver by lazy {
        MediaUrlResolver(
            projectUrl = BuildConfig.SUPABASE_URL,
            accountId = {
                SupabaseManager.client.auth
                    .currentSessionOrNull()
                    ?.user
                    ?.id
            },
            sign = { reference ->
                SupabaseManager.client.storage
                    .from(reference.bucket)
                    .createSignedUrl(reference.path, signedUrlLifetime)
            },
        )
    }

    suspend fun resolve(url: String): String = resolver.resolve(url)
}
