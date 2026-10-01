package com.sandnes.familyapp.data.remote

import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal interface SecretStore {
    suspend fun read(name: String): String?

    suspend fun write(
        name: String,
        value: String,
    )

    suspend fun remove(name: String)
}

/** Persist encryption before removing the legacy session; never fall back after corruption. */
internal class EncryptedAuthStorage(
    private val secrets: SecretStore,
    private val legacySessions: SessionManager = SettingsSessionManager(),
    private val legacyVerifiers: CodeVerifierCache = SettingsCodeVerifierCache(),
) : SessionManager,
    CodeVerifierCache {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    override suspend fun saveSession(session: UserSession) =
        mutex.withLock {
            secrets.write(SESSION, json.encodeToString(session))
            legacySessions.deleteSession()
        }

    override suspend fun loadSession(): UserSession =
        mutex.withLock {
            val encrypted = secrets.read(SESSION)
            val session =
                if (encrypted != null) {
                    json.decodeFromString<UserSession>(encrypted)
                } else {
                    legacySessions.loadSession().also { secrets.write(SESSION, json.encodeToString(it)) }
                }
            legacySessions.deleteSession()
            session
        }

    override suspend fun deleteSession() =
        mutex.withLock {
            try {
                secrets.remove(SESSION)
            } finally {
                legacySessions.deleteSession()
            }
        }

    override suspend fun saveCodeVerifier(codeVerifier: String) =
        mutex.withLock {
            secrets.write(VERIFIER, codeVerifier)
            legacyVerifiers.deleteCodeVerifier()
        }

    override suspend fun loadCodeVerifier(): String? =
        mutex.withLock {
            val verifier =
                secrets.read(VERIFIER) ?: legacyVerifiers.loadCodeVerifier()?.also {
                    secrets.write(VERIFIER, it)
                }
            legacyVerifiers.deleteCodeVerifier()
            verifier
        }

    override suspend fun deleteCodeVerifier() =
        mutex.withLock {
            try {
                secrets.remove(VERIFIER)
            } finally {
                legacyVerifiers.deleteCodeVerifier()
            }
        }

    private companion object {
        const val SESSION = "session"
        const val VERIFIER = "pkce_verifier"
    }
}
