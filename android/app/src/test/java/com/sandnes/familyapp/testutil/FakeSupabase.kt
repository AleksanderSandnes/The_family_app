package com.sandnes.familyapp.testutil

import com.sandnes.familyapp.data.remote.SupabaseManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
import java.util.concurrent.CopyOnWriteArrayList

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
 * mapping — run for real while no network is touched. Realtime/Auth/Storage are not installed;
 * code that needs them must already tolerate their absence (it wraps them in runCatching).
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

    val client: SupabaseClient by lazy {
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
        }
    }

    /** Points [SupabaseManager.client] at this fake. Call [uninstall] in `@After`. */
    fun install(): FakeSupabase {
        mockkObject(SupabaseManager)
        every { SupabaseManager.client } returns client
        return this
    }

    fun uninstall() {
        unmockkObject(SupabaseManager)
    }
}
