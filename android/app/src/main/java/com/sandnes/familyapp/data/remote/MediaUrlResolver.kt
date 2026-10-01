package com.sandnes.familyapp.data.remote

import java.net.URI
import java.net.URLDecoder

data class StorageMediaReference(
    val bucket: String,
    val path: String,
)

/** Stable database URLs are locators, never proof that the object is public. */
class MediaUrlResolver(
    projectUrl: String,
    private val accountId: () -> String?,
    private val sign: suspend (StorageMediaReference) -> String,
) {
    private val project = URI(projectUrl)

    fun reference(url: String): StorageMediaReference? {
        val source = URI(url)
        if (!source.host.equals(project.host, ignoreCase = true)) return null
        val rawPath = source.rawPath ?: return null
        if (!rawPath.startsWith(STORAGE_ROOT)) return null
        require(source.scheme == project.scheme && effectivePort(source) == effectivePort(project)) { INVALID_URL }
        require(source.rawUserInfo == null && source.rawFragment == null) { INVALID_URL }
        require(rawPath.startsWith(OBJECT_ROOT)) { INVALID_URL }
        val parts = rawPath.removePrefix(OBJECT_ROOT).split('/')
        require(parts.size >= MIN_REFERENCE_PARTS && parts.first() in MODES) { INVALID_URL }
        val bucket = decodeSegment(parts[1])
        require(bucket in BUCKETS) { INVALID_URL }
        val path = parts.drop(2).joinToString("/", transform = ::decodeSegment)
        return StorageMediaReference(bucket, path)
    }

    suspend fun resolve(url: String): String {
        val reference = reference(url) ?: return url
        val account = checkNotNull(accountId()) { "Media requires an authenticated session" }
        val signed = sign(reference)
        require(reference(signed) == reference && URI(signed).rawPath.startsWith(SIGNED_ROOT)) { INVALID_URL }
        require(URI(signed).rawQuery?.split('&')?.any { it.startsWith("token=") && it.length > TOKEN_PREFIX_LENGTH } == true) { INVALID_URL }
        check(accountId() == account) { "Media account changed" }
        val nonce = URI(url).rawQuery?.split('&')?.firstOrNull { it.matches(CACHE_NONCE) }
        return if (nonce == null) signed else "$signed&$nonce"
    }

    fun currentAccountId(): String? = accountId()

    private fun decodeSegment(raw: String): String {
        val decoded = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        require(decoded.isNotEmpty() && decoded !in setOf(".", "..")) { INVALID_URL }
        require(decoded.none { it == '/' || it == '\\' || it == '%' || it.isISOControl() }) { INVALID_URL }
        return decoded
    }

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) HTTPS_PORT else uri.port

    private companion object {
        const val STORAGE_ROOT = "/storage/v1/"
        const val OBJECT_ROOT = "${STORAGE_ROOT}object/"
        const val SIGNED_ROOT = "${OBJECT_ROOT}sign/"
        const val HTTPS_PORT = 443
        const val MIN_REFERENCE_PARTS = 3
        const val TOKEN_PREFIX_LENGTH = 6
        const val INVALID_URL = "Invalid media URL"
        val MODES = setOf("public", "authenticated", "sign")
        val BUCKETS = setOf("avatars", "group-images", "wish-images", "chat-media")
        val CACHE_NONCE = Regex("t=[0-9]{1,20}")
    }
}
