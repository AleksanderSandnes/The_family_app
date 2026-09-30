package com.sandnes.familyapp.data.remote

import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EncryptedAuthStorageTest {
    private val session = UserSession("fictional-access", "fictional-refresh", expiresIn = 3600, tokenType = "bearer")

    @Test
    fun `migration persists session before clearing plaintext`() =
        runTest {
            val secrets = FakeSecrets()
            val legacy = MemorySessionManager(session)
            val storage = EncryptedAuthStorage(secrets, legacy, MemoryCodeVerifierCache())
            assertEquals(session, storage.loadSession())
            assertTrue(secrets.values.containsKey("session"))
            assertNull(legacy.loadSessionOrNull())
            assertEquals(session, storage.loadSession())
        }

    @Test
    fun `failed persistence keeps the legacy session for retry`() =
        runTest {
            val secrets = FakeSecrets().apply { failWrite = true }
            val legacy = MemorySessionManager(session)
            val storage = EncryptedAuthStorage(secrets, legacy, MemoryCodeVerifierCache())
            expectFailure { storage.loadSession() }
            assertEquals(session, legacy.loadSession())
            assertFalse(secrets.values.containsKey("session"))
            secrets.failWrite = false
            assertEquals(session, storage.loadSession())
            assertNull(legacy.loadSessionOrNull())
        }

    @Test
    fun `corrupt encrypted session does not fall back to legacy plaintext`() =
        runTest {
            val secrets = FakeSecrets().apply { values["session"] = "invalid-json" }
            val legacy = MemorySessionManager(session)
            val storage = EncryptedAuthStorage(secrets, legacy, MemoryCodeVerifierCache())
            assertNull(storage.loadSessionOrNull())
            assertEquals(session, legacy.loadSession())
            secrets.failRead = true
            assertNull(storage.loadSessionOrNull())
            assertEquals(session, legacy.loadSession())
        }

    @Test
    fun `saved refresh replaces encrypted session and removes leftover plaintext`() =
        runTest {
            val secrets = FakeSecrets()
            val legacy = MemorySessionManager(session)
            val storage = EncryptedAuthStorage(secrets, legacy, MemoryCodeVerifierCache())
            val refreshed = session.copy(accessToken = "fictional-refreshed")
            storage.saveSession(refreshed)
            assertNull(legacy.loadSessionOrNull())
            assertEquals(refreshed, storage.loadSession())
            storage.deleteSession()
            assertNull(storage.loadSessionOrNull())
            assertTrue(secrets.values.isEmpty())
        }

    @Test
    fun `PKCE verifier migrates and deletes without plaintext fallback after corruption`() =
        runTest {
            val secrets = FakeSecrets()
            val legacy = MemoryCodeVerifierCache("fictional-verifier")
            val storage = EncryptedAuthStorage(secrets, MemorySessionManager(), legacy)
            assertEquals("fictional-verifier", storage.loadCodeVerifier())
            assertNull(legacy.loadCodeVerifier())
            storage.saveCodeVerifier("new-fictional-verifier")
            assertEquals("new-fictional-verifier", storage.loadCodeVerifier())
            legacy.saveCodeVerifier("stale-fictional-verifier")
            secrets.failRead = true
            expectFailure { storage.loadCodeVerifier() }
            secrets.failRead = false
            storage.deleteCodeVerifier()
            assertNull(storage.loadCodeVerifier())
            assertNull(legacy.loadCodeVerifier())
        }

    @Test
    fun `logout waits for an in-flight refresh then removes both copies`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            val secrets =
                FakeSecrets().apply {
                    beforeWrite = {
                        entered.complete(Unit)
                        proceed.await()
                    }
                }
            val legacy = MemorySessionManager(session)
            val storage = EncryptedAuthStorage(secrets, legacy, MemoryCodeVerifierCache())
            val refresh = launch { storage.saveSession(session) }
            entered.await()
            val logout = launch { storage.deleteSession() }
            proceed.complete(Unit)
            refresh.join()
            logout.join()
            assertTrue(secrets.values.isEmpty())
            assertNull(legacy.loadSessionOrNull())
        }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected storage failure")
        } catch (_: IllegalStateException) {
            // Storage errors must propagate rather than pretending persistence succeeded.
        }
    }

    private class FakeSecrets : SecretStore {
        val values = mutableMapOf<String, String>()
        var failWrite = false
        var failRead = false
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun read(name: String): String? {
            check(!failRead)
            return values[name]
        }

        override suspend fun write(
            name: String,
            value: String,
        ) {
            beforeWrite()
            check(!failWrite)
            values[name] = value
        }

        override suspend fun remove(name: String) {
            values.remove(name)
        }
    }
}
