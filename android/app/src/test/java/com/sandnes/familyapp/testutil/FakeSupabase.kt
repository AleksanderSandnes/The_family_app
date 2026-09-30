package com.sandnes.familyapp.testutil

import com.sandnes.familyapp.data.remote.SupabaseManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.resumable.MemoryResumableCache
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory [SessionManager] whose session can be seeded before the client is built. */
private class SeededSessionManager : SessionManager {
    @Volatile
    var session: UserSession? = null

    override suspend fun saveSession(session: UserSession) {
        this.session = session
    }

    override suspend fun loadSession(): UserSession = session ?: throw NoSuchElementException("No stored session")

    override suspend fun deleteSession() {
        session = null
    }
}

/** A request the code under test sent to the fake backend. */
data class RecordedRequest(
    val method: HttpMethod,
    val path: String,
    val query: String,
    val body: String,
)

/** A canned backend answer. */
data class FakeResponse(
    val status: HttpStatusCode = HttpStatusCode.OK,
    val json: String = "[]",
)

/**
 * In-memory Supabase backend for unit tests. It installs a real [SupabaseClient] (Postgrest only)
 * backed by a Ktor MockEngine, so production code paths — query building, JSON decoding, error
 * mapping — run for real while no network is touched. Postgrest, Storage and an in-memory Auth
 * session are installed; Realtime is not, so code that needs it must already tolerate its absence
 * (it wraps those calls in runCatching). Use [signIn] to give the client a current session.
 *
 * Register answers with [on]; the most recently registered matching rule wins and unmatched requests answer `[]`.
 */
class FakeSupabase {
    private data class Rule(
        val method: HttpMethod?,
        val pathContains: String,
        val queryContains: String?,
        val respond: (RecordedRequest) -> FakeResponse,
    )

    private val rules = CopyOnWriteArrayList<Rule>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val sessionManager = SeededSessionManager()

    fun on(
        method: HttpMethod?,
        pathContains: String,
        queryContains: String? = null,
        respond: (RecordedRequest) -> FakeResponse,
    ) {
        rules += Rule(method, pathContains, queryContains, respond)
    }

    fun onJson(
        method: HttpMethod?,
        pathContains: String,
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        queryContains: String? = null,
    ) = on(method, pathContains, queryContains) { FakeResponse(status, json) }

    fun requestsTo(
        pathContains: String,
        method: HttpMethod? = null,
    ) = requests.filter { it.path.contains(pathContains) && (method == null || it.method == method) }

    private fun answer(request: HttpRequestData): FakeResponse {
        val recorded =
            RecordedRequest(
                method = request.method,
                path = request.url.encodedPath,
                query = request.url.encodedQuery,
                body = bodyText(request).orEmpty(),
            )
        requests += recorded
        val rule =
            rules.lastOrNull {
                (it.method == null || it.method == recorded.method) &&
                    recorded.path.contains(it.pathContains) &&
                    (it.queryContains == null || recorded.query.contains(it.queryContains))
            }
        return rule?.respond?.invoke(recorded) ?: FakeResponse()
    }

    private fun bodyText(request: HttpRequestData): String? =
        (request.body as? io.ktor.http.content.TextContent)?.text
            ?: (request.body as? io.ktor.http.content.ByteArrayContent)?.bytes()?.decodeToString()

    private var built = false

    val client: SupabaseClient by lazy {
        built = true
        createSupabaseClient(supabaseUrl = "https://fake.supabase.co", supabaseKey = "fake-anon-key") {
            httpEngine =
                MockEngine { request ->
                    val response = answer(request)
                    respond(
                        content = ByteReadChannel(response.json),
                        status = response.status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            install(Postgrest)
            install(Storage) {
                // The default resumable-upload cache needs an Android Settings backend.
                resumable { cache = MemoryResumableCache() }
            }
            // Auth is only present when a session was seeded before first use: without a stored
            // session its start-up never completes, which would stall every Postgrest request.
            if (sessionManager.session != null) {
                install(Auth) {
                    sessionManager = this@FakeSupabase.sessionManager
                    codeVerifierCache = MemoryCodeVerifierCache()
                    alwaysAutoRefresh = false
                }
            }
        }
    }

    /**
     * Makes [userId] the client's current auth user, optionally via an OAuth [provider]. Call it
     * before [install] so the plugin loads the seeded session on start-up; calling it afterwards
     * replaces the session on the live client.
     */
    fun signIn(
        userId: String,
        provider: String = "email",
    ): FakeSupabase {
        val session =
            UserSession(
                accessToken = "fake-access-token",
                refreshToken = "fake-refresh-token",
                expiresIn = 3600,
                tokenType = "bearer",
                user =
                    UserInfo(
                        id = userId,
                        aud = "authenticated",
                        email = "$userId@example.com",
                        appMetadata = JsonObject(mapOf("provider" to JsonPrimitive(provider))),
                    ),
            )
        sessionManager.session = session
        if (built) runBlocking { client.auth.importSession(session, autoRefresh = false) }
        return this
    }

    /** Waits (real time) until the seeded session is the client's current one. */
    private fun awaitSession() {
        val expected = sessionManager.session?.user?.id ?: return
        val deadline = System.nanoTime() + 5_000_000_000L
        while (client.auth
                .currentSessionOrNull()
                ?.user
                ?.id != expected
        ) {
            check(System.nanoTime() < deadline) { "Fake auth session was not loaded" }
            Thread.sleep(5)
        }
    }

    /** Points [SupabaseManager.client] at this fake. Call [uninstall] in `@After`. */
    fun install(): FakeSupabase {
        mockkObject(SupabaseManager)
        every { SupabaseManager.client } returns client
        awaitSession()
        return this
    }

    fun uninstall() {
        unmockkObject(SupabaseManager)
    }
}
