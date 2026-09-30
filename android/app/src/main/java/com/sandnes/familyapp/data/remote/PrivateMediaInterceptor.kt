package com.sandnes.familyapp.data.remote

import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult

/** Resolve before Coil's cache lookup; protected bytes must never cross account boundaries. */
class PrivateMediaInterceptor(
    private val resolver: MediaUrlResolver,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            return chain.proceed()
        }
        if (resolver.reference(url) == null) return chain.proceed()
        val account = resolver.currentAccountId()
        val signed = resolver.resolve(url)
        val protectedRequest =
            request
                .newBuilder()
                .data(signed)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        val result = chain.withRequest(protectedRequest).proceed()
        check(resolver.currentAccountId() == account) { "Media account changed" }
        return result
    }
}
