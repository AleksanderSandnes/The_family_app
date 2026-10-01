package com.sandnes.familyapp.data

import com.sandnes.familyapp.testutil.FakeSupabase
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FamilyRepositorySharesTest {
    private val backend = FakeSupabase()
    private val repo = mockk<FamilyRepository>(relaxed = true)

    @Before
    fun setUp() {
        backend.install()
    }

    @After
    fun tearDown() = backend.uninstall()

    @Test
    fun `relations are listed for a family and default to empty on failure`() =
        runBlocking {
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/family_relations",
                """[{"id":"r1","family_id":"f1","from_user_id":"a","to_user_id":"b","relation":"Mother"}]""",
            )
            assertEquals(1, repo.getFamilyRelations("f1").size)
            backend.onJson(HttpMethod.Get, "/rest/v1/family_relations", "{}", HttpStatusCode.InternalServerError)
            assertTrue(repo.getFamilyRelations("f1").isEmpty())
        }

    @Test
    fun `setting a relation upserts and a blank relation deletes`() =
        runBlocking {
            assertTrue(repo.setFamilyRelation("a", "b", "f1", " Mother ").isSuccess)
            val upsert = backend.requestsTo("family_relations", HttpMethod.Post).single()
            assertTrue(upsert.body.contains("\"relation\":\"Mother\""))
            assertTrue(upsert.query.contains("on_conflict=from_user_id%2Cto_user_id"))
            assertTrue(repo.setFamilyRelation("a", "b", "f1", "  ").isSuccess)
            val delete = backend.requestsTo("family_relations", HttpMethod.Delete).single()
            assertTrue(delete.query.contains("from_user_id=eq.a") && delete.query.contains("to_user_id=eq.b"))
            backend.onJson(HttpMethod.Delete, "/rest/v1/family_relations", "{}", HttpStatusCode.Forbidden)
            assertTrue(repo.setFamilyRelation("a", "b", "f1", "").isFailure)
        }

    @Test
    fun `share token rpc returns the token or a failure`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/ensure_wishlist_share_token", "\"tok\"")
            assertEquals("tok", repo.ensureWishlistShareToken("w1").getOrNull())
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/ensure_wishlist_share_token", "null")
            assertTrue(repo.ensureWishlistShareToken("w1").isFailure)
        }

    @Test
    fun `accepting a share returns the wishlist id or null`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/accept_wishlist_share", "\"w9\"")
            assertEquals("w9", repo.acceptWishlistShare("tok"))
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/accept_wishlist_share", "{}", HttpStatusCode.BadRequest)
            assertNull(repo.acceptWishlistShare("bad"))
        }

    @Test
    fun `shared wishlists are flagged and empty shares skip the second query`() =
        runBlocking {
            assertTrue(repo.getSharedWishlists().isEmpty())
            assertTrue(backend.requestsTo("rest/v1/wishlists").isEmpty())
            backend.onJson(HttpMethod.Get, "/rest/v1/wishlist_shares", """[{"wishlist_id":"w3"}]""")
            backend.onJson(HttpMethod.Get, "/rest/v1/wishlists", """[{"id":"w3","owner_user_id":"u9","name":"Elsewhere"}]""")
            val shared = repo.getSharedWishlists()
            assertTrue(shared.single().sharedWithMe)
            backend.onJson(HttpMethod.Get, "/rest/v1/wishlist_shares", "{}", HttpStatusCode.InternalServerError)
            assertTrue(repo.getSharedWishlists().isEmpty())
        }
}
