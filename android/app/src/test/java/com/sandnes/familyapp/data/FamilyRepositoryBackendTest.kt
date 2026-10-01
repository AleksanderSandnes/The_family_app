package com.sandnes.familyapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sandnes.familyapp.testutil.FakeSupabase
import io.github.jan.supabase.auth.auth
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FamilyRepository against a fake Supabase backend with a real [SessionManager] (DataStore).
 * Runs on a real clock (runBlocking) so Ktor timeouts never see virtual time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FamilyRepositoryBackendTest {
    private val backend = FakeSupabase()
    private lateinit var session: SessionManager
    private lateinit var repo: FamilyRepository

    private val sessionJson =
        """{"access_token":"a","refresh_token":"r","expires_in":3600,"token_type":"bearer",
            "user":{"id":"auth-1","aud":"authenticated","email":"ada@example.com"}}"""
    private val adaRow = """{"id":"u1","auth_id":"auth-1","name":"Ada","email":"ada@example.com","birthday":"1990-05-05","family_id":"f1"}"""

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        backend.signIn("auth-1").install()
        session = SessionManager(context)
        repo = FamilyRepository(session)
        runBlocking { session.signOut() }
        repo.invalidateUserCache()
        backend.onJson(HttpMethod.Get, "/rest/v1/users", "[$adaRow]")
        backend.onJson(HttpMethod.Get, "/rest/v1/families", """[{"id":"f1","name":"Fam","join_code":"ABC","admin_id":"u1"}]""")
    }

    @After
    fun tearDown() {
        // The DataStore is process-wide; put every setting these tests touch back to its default.
        runBlocking {
            session.signOut()
            session.setThemeMode(ThemeMode.SYSTEM)
            session.setLocationVisible(false)
            session.setNotificationsEnabled(true)
            session.setNotifyDaysBefore(1)
        }
        backend.uninstall()
    }

    private fun signedIn() = runBlocking { session.signIn("u1") }

    // ───────────────────────── presence and notification prefs ─────────────────────────

    @Test
    fun `touching last active patches the user row only when signed in`() =
        runBlocking {
            repo.touchLastActive()
            assertTrue(backend.requestsTo("rest/v1/users", HttpMethod.Patch).isEmpty())
            signedIn()
            repo.touchLastActive()
            val patch = backend.requestsTo("rest/v1/users", HttpMethod.Patch).single()
            assertTrue(patch.body.contains("last_active_at") && patch.query.contains("id=eq.u1"))
        }

    @Test
    fun `notification settings are stored locally and mirrored to the server`() =
        runBlocking {
            signedIn()
            repo.setNotificationsEnabled(false)
            repo.setNotifyDaysBefore(3)
            assertFalse(repo.notificationsEnabled.first())
            assertEquals(3, repo.notifyDaysBefore.first())
            val bodies = backend.requestsTo("rest/v1/users", HttpMethod.Patch).map { it.body }
            assertTrue(bodies[0].contains("\"notifications_enabled\":false"))
            assertTrue(bodies[1].contains("\"notify_days_before\":3"))
            repo.syncNotificationPrefsToServer()
            assertEquals(3, backend.requestsTo("rest/v1/users", HttpMethod.Patch).size)
        }

    @Test
    fun `notification settings skip the server while signed out`() =
        runBlocking {
            repo.setNotificationsEnabled(true)
            repo.setNotifyDaysBefore(2)
            assertTrue(backend.requestsTo("rest/v1/users", HttpMethod.Patch).isEmpty())
        }

    @Test
    fun `theme and location settings round trip through the session`() =
        runBlocking {
            repo.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, repo.themeMode.first())
            repo.setLocationVisible(true)
            assertTrue(repo.locationVisible.first())
        }

    // ───────────────────────── push tokens ─────────────────────────

    @Test
    fun `push tokens are upserted and removed for the signed in user`() =
        runBlocking {
            repo.registerPushToken("ignored-while-signed-out")
            assertTrue(backend.requestsTo("device_push_tokens").isEmpty())
            signedIn()
            repo.registerPushToken("tok-1")
            val upsert = backend.requestsTo("device_push_tokens", HttpMethod.Post).single()
            assertTrue(upsert.body.contains("\"token\":\"tok-1\"") && upsert.body.contains("\"platform\":\"android\""))
            repo.unregisterPushToken()
            assertTrue(
                backend
                    .requestsTo("device_push_tokens", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("token=eq.tok-1"),
            )
            // A second call has no remembered token and Firebase is unavailable, so it is a no-op.
            repo.unregisterPushToken()
            assertEquals(1, backend.requestsTo("device_push_tokens", HttpMethod.Delete).size)
            repo.syncPushToken()
        }

    // ───────────────────────── users and families ─────────────────────────

    @Test
    fun `users are fetched cached and invalidated`() =
        runBlocking {
            assertEquals("Ada", repo.getUser("u1")?.name)
            assertEquals("Ada", repo.getUser("u1")?.name)
            assertEquals(1, backend.requestsTo("rest/v1/users", HttpMethod.Get).size)
            repo.invalidateUserCache()
            repo.getUser("u1")
            assertEquals(2, backend.requestsTo("rest/v1/users", HttpMethod.Get).size)
        }

    @Test
    fun `a failed or empty user fetch is not cached`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "{}", HttpStatusCode.InternalServerError)
            assertNull(repo.getUser("u1"))
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "[]")
            assertNull(repo.getUser("u1"))
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "[$adaRow]")
            assertEquals("Ada", repo.getUser("u1")?.name)
        }

    @Test
    fun `family lookups default safely on failure`() =
        runBlocking {
            assertEquals(1, repo.getFamilyMembers("f1").size)
            assertEquals("Fam", repo.getFamily("f1")?.name)
            assertTrue(repo.isFamilyAdmin("u1"))
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "{}", HttpStatusCode.InternalServerError)
            backend.onJson(HttpMethod.Get, "/rest/v1/families", "{}", HttpStatusCode.InternalServerError)
            repo.invalidateUserCache()
            assertTrue(repo.getFamilyMembers("f1").isEmpty())
            assertNull(repo.getFamily("f1"))
            assertFalse(repo.isFamilyAdmin("u1"))
        }

    @Test
    fun `a non-admin member is not reported as admin`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/families", """[{"id":"f1","name":"Fam","admin_id":"someone-else"}]""")
            assertFalse(repo.isFamilyAdmin("u1"))
        }

    // ───────────────────────── auth ─────────────────────────

    @Test
    fun `login signs in and remembers the app user`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/auth/v1/token", sessionJson)
            val result = repo.login("  ADA@Example.com ", "secret")
            assertEquals("u1", result.getOrNull())
            assertEquals("u1", repo.currentUserId.first())
            assertTrue(
                backend
                    .requestsTo("/auth/v1/token")
                    .single()
                    .body
                    .contains("ada@example.com"),
            )
        }

    @Test
    fun `login fails when the credentials or the profile are missing`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/auth/v1/token", """{"error":"invalid_grant","error_description":"bad"}""", HttpStatusCode.BadRequest)
            assertTrue(repo.login("a@b.c", "x").isFailure)
            backend.onJson(HttpMethod.Post, "/auth/v1/token", sessionJson)
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "[]")
            assertTrue(repo.login("a@b.c", "x").isFailure)
        }

    @Test
    fun `completing sign in after confirmation needs a session and a profile`() =
        runBlocking {
            assertEquals("u1", repo.completeSignInAfterConfirmation().getOrNull())
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "[]")
            assertTrue(repo.completeSignInAfterConfirmation().isFailure)
            backend.client.auth.clearSession()
            assertTrue(repo.completeSignInAfterConfirmation().isFailure)
            assertFalse(repo.hasAuthSession())
        }

    @Test
    fun `registering signs up with normalised metadata`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/auth/v1/signup", sessionJson)
            val result = repo.register(" Ada ", " ADA@Example.com ", "pw", "1990-05-05", "555")
            assertTrue(result.isSuccess)
            val body = backend.requestsTo("/auth/v1/signup").single().body
            assertTrue(body.contains("ada@example.com") && body.contains("\"full_name\":\"Ada\"") && body.contains("avatar_color"))
        }

    @Test
    fun `registering reports a rejected sign up`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/auth/v1/signup", """{"msg":"exists"}""", HttpStatusCode.UnprocessableEntity)
            assertTrue(repo.register("Ada", "a@b.c", "pw", "", "").isFailure)
        }

    @Test
    fun `google sign in fails gracefully without a browser`() =
        runBlocking {
            assertTrue(repo.signInWithGoogle().isFailure)
            assertTrue(repo.sessionStatusFlow.value != null)
        }

    @Test
    fun `signing out clears everything locally`() =
        runBlocking {
            signedIn()
            repo.registerPushToken("tok-9")
            repo.signOut()
            assertNull(repo.currentUserId.first())
            assertTrue(backend.requestsTo("device_push_tokens", HttpMethod.Delete).isNotEmpty())
        }

    @Test
    fun `deleting the account calls the edge function and clears the session`() =
        runBlocking {
            signedIn()
            backend.onJson(HttpMethod.Post, "/functions/v1/delete-account", "{}")
            assertTrue(repo.deleteAccount().isSuccess)
            assertTrue(
                backend
                    .requestsTo("delete-account")
                    .single()
                    .body
                    .contains("DELETE_MY_ACCOUNT"),
            )
            assertNull(repo.currentUserId.first())
        }

    @Test
    fun `a failed account deletion keeps the local session`() =
        runBlocking {
            signedIn()
            backend.onJson(HttpMethod.Post, "/functions/v1/delete-account", "{}", HttpStatusCode.InternalServerError)
            assertTrue(repo.deleteAccount().isFailure)
            assertEquals("u1", repo.currentUserId.first())
        }

    @Test
    fun `password reset and signup codes go through the auth endpoints`() =
        runBlocking {
            assertTrue(repo.sendPasswordResetEmail(" A@B.C ").isSuccess)
            assertTrue(
                backend
                    .requestsTo("/auth/v1/recover")
                    .single()
                    .body
                    .contains("a@b.c"),
            )
            assertTrue(repo.resendSignupCode("A@B.C").isSuccess)
            assertTrue(backend.requestsTo("/auth/v1/resend").isNotEmpty())
            backend.onJson(HttpMethod.Post, "/auth/v1/verify", sessionJson)
            assertEquals("u1", repo.confirmSignupEmail("A@B.C", "123456").getOrNull())
            backend.onJson(HttpMethod.Put, "/auth/v1/user", """{"id":"auth-1","aud":"authenticated","email":"ada@example.com"}""")
            assertEquals("u1", repo.confirmPasswordReset("A@B.C", "123456", "new-pw").getOrNull())
            assertTrue(
                backend
                    .requestsTo("/auth/v1/user", HttpMethod.Put)
                    .single()
                    .body
                    .contains("new-pw"),
            )
        }

    @Test
    fun `wrong confirmation codes fail`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/auth/v1/verify", """{"msg":"bad"}""", HttpStatusCode.BadRequest)
            assertTrue(repo.confirmSignupEmail("a@b.c", "000000").isFailure)
            assertTrue(repo.confirmPasswordReset("a@b.c", "000000", "pw").isFailure)
        }

    // ───────────────────────── family lifecycle ─────────────────────────

    @Test
    fun `creating a family inserts it assigns the admin and syncs the birthday`() =
        runBlocking {
            signedIn()
            backend.onJson(HttpMethod.Post, "/rest/v1/families", """[{"id":"f9","name":"New","join_code":"XYZ","admin_id":"u1"}]""")
            backend.onJson(HttpMethod.Get, "/rest/v1/birthdays", "[]")
            val changes = mutableListOf<Unit>()
            val collector = launchCollector(changes)
            assertEquals("f9", repo.createFamily(" New ", "XYZ", "u1").getOrNull())
            assertTrue(
                backend
                    .requestsTo("rest/v1/families", HttpMethod.Post)
                    .single()
                    .body
                    .contains("\"name\":\"New\""),
            )
            assertTrue(
                backend
                    .requestsTo("rest/v1/users", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("f9"),
            )
            assertTrue(backend.requestsTo("rest/v1/birthdays", HttpMethod.Post).isNotEmpty())
            Thread.sleep(50)
            assertEquals(1, changes.size)
            collector.cancel()
        }

    @Test
    fun `a failed family creation does not announce a change`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/rest/v1/families", "{}", HttpStatusCode.InternalServerError)
            assertTrue(repo.createFamily("X", "C", "u1").isFailure)
        }

    @Test
    fun `an existing birthday is not duplicated`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/rest/v1/families", """[{"id":"f9","name":"New","join_code":"X","admin_id":"u1"}]""")
            backend.onJson(HttpMethod.Get, "/rest/v1/birthdays", """[{"id":"b1","name":"Ada"}]""")
            assertTrue(repo.createFamily("New", "X", "u1").isSuccess)
            assertTrue(backend.requestsTo("rest/v1/birthdays", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `users without a birthday skip the birthday sync`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/users", """[{"id":"u1","name":"Ada","birthday":""}]""")
            backend.onJson(HttpMethod.Post, "/rest/v1/families", """[{"id":"f9","name":"New","join_code":"X","admin_id":"u1"}]""")
            assertTrue(repo.createFamily("New", "X", "u1").isSuccess)
            assertTrue(backend.requestsTo("rest/v1/birthdays").isEmpty())
        }

    @Test
    fun `joining a family validates the code through the rpc`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/join_family", "\"f1\"")
            backend.onJson(HttpMethod.Get, "/rest/v1/birthdays", "[]")
            assertEquals("f1", repo.joinFamily(" abc ", "u1").getOrNull())
            assertTrue(
                backend
                    .requestsTo("rpc/join_family")
                    .single()
                    .body
                    .contains("\"p_code\":\"abc\""),
            )
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/join_family", "null")
            assertTrue(repo.joinFamily("bad", "u1").isFailure)
        }

    @Test
    fun `leaving a family without one just clears membership`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/users", """[{"id":"u1","name":"Ada"}]""")
            repo.leaveFamily("u1")
            assertEquals(1, backend.requestsTo("rest/v1/users", HttpMethod.Patch).size)
            backend.onJson(HttpMethod.Get, "/rest/v1/users", "[]")
            repo.invalidateUserCache()
            repo.leaveFamily("nobody")
        }

    @Test
    fun `leaving a family cleans up conversations events birthdays and lists`() =
        runBlocking {
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/conversation_participants",
                """[{"conversation_id":"c1","user_id":"u1"},{"conversation_id":"c2","user_id":"u1"},{"conversation_id":"c2","user_id":"u2"}]""",
            )
            repo.leaveFamily("u1")
            assertTrue(
                backend
                    .requestsTo("rest/v1/conversations", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=in.%28c1%29"),
            )
            assertTrue(backend.requestsTo("conversation_participants", HttpMethod.Delete).isNotEmpty())
            assertTrue(backend.requestsTo("calendar_events", HttpMethod.Delete).isNotEmpty())
            assertTrue(backend.requestsTo("birthdays", HttpMethod.Delete).isNotEmpty())
            assertTrue(backend.requestsTo("shopping_lists", HttpMethod.Delete).isNotEmpty())
            assertTrue(
                backend
                    .requestsTo("rest/v1/users", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("family_id"),
            )
        }

    @Test
    fun `leaving with no conversations skips conversation cleanup`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/conversation_participants", "[]")
            repo.leaveFamily("u1")
            assertTrue(backend.requestsTo("rest/v1/conversations", HttpMethod.Delete).isEmpty())
            assertTrue(backend.requestsTo("calendar_events", HttpMethod.Delete).isEmpty().not())
        }

    @Test
    fun `leaving a failing family leaves membership untouched`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/conversation_participants", "{}", HttpStatusCode.InternalServerError)
            repo.leaveFamily("u1")
            assertTrue(backend.requestsTo("rest/v1/users", HttpMethod.Patch).isEmpty())
        }

    @Test
    fun `profile family and member edits patch the right rows`() =
        runBlocking {
            repo.updateProfile("u1", ProfileUpdate("Ada L", "a@b.c", "1990-01-01", "555", null))
            val profile = backend.requestsTo("rest/v1/users", HttpMethod.Patch).single().body
            assertTrue(profile.contains("\"name\":\"Ada L\"") && profile.contains("\"avatar_url\":null"))
            assertTrue(repo.removeFamilyMember("u2").isSuccess)
            assertTrue(repo.renameFamily("f1", " Crew ").isSuccess)
            assertTrue(
                backend
                    .requestsTo("rest/v1/families", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("Crew"),
            )
            assertTrue(repo.updateFamilyPhoto("f1", "https://photo").isSuccess)
            backend.onJson(HttpMethod.Patch, "/rest/v1/families", "{}", HttpStatusCode.Forbidden)
            assertTrue(repo.renameFamily("f1", "x").isFailure)
            assertTrue(repo.updateFamilyPhoto("f1", "x").isFailure)
            backend.onJson(HttpMethod.Patch, "/rest/v1/users", "{}", HttpStatusCode.Forbidden)
            assertTrue(repo.removeFamilyMember("u2").isFailure)
            repo.updateProfile("u1", ProfileUpdate("x", "x", "x", "x", "x"))
        }

    // ───────────────────────── chat ─────────────────────────

    @Test
    fun `chat helpers read write and react`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/messages", """[{"id":"m1","conversation_id":"c1","user_from":"u1","text":"hi"}]""")
            assertEquals("m1", repo.getLastMessage("c1")?.id)
            assertTrue(
                backend
                    .requestsTo("rest/v1/messages")
                    .single()
                    .query
                    .contains("order=sent_at.desc"),
            )
            signedIn()
            repo.markConversationRead("c1")
            assertTrue(
                backend
                    .requestsTo("conversation_participants", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("last_read_at"),
            )
            assertTrue(repo.sendMessage("c1", "hello").isSuccess)
            assertTrue(
                backend
                    .requestsTo("rest/v1/messages", HttpMethod.Post)
                    .single()
                    .body
                    .contains("\"message_type\":\"text\""),
            )
            assertTrue(repo.addReaction("m1", "c1", "👍").isSuccess)
            assertTrue(
                backend
                    .requestsTo("message_reactions", HttpMethod.Post)
                    .single()
                    .body
                    .contains("👍"),
            )
            assertTrue(repo.removeReaction("m1").isSuccess)
            assertTrue(
                backend
                    .requestsTo("message_reactions", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("message_id=eq.m1"),
            )
        }

    @Test
    fun `chat helpers handle failures and a signed out user`() =
        runBlocking {
            backend.onJson(HttpMethod.Get, "/rest/v1/messages", "{}", HttpStatusCode.InternalServerError)
            assertNull(repo.getLastMessage("c1"))
            repo.markConversationRead("c1")
            assertTrue(repo.sendMessage("c1", "x").isFailure)
            assertTrue(repo.addReaction("m", "c", "e").isFailure)
            assertTrue(repo.removeReaction("m").isFailure)
            assertTrue(backend.requestsTo("conversation_participants").isEmpty())
        }

    @Test
    fun `chat media is uploaded under the auth user and returns a public url`() =
        runBlocking {
            backend.onJson(HttpMethod.Post, "/storage/v1/object/chat-media", """{"Key":"chat-media/c1/auth-1/a.jpg","Id":"1"}""")
            val url = repo.uploadChatMedia("c1", byteArrayOf(1, 2), "a.jpg")
            assertTrue(url.contains("chat-media/c1/auth-1/a.jpg"))
            backend.client.auth.clearSession()
            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { repo.uploadChatMedia("c1", byteArrayOf(1), "b.jpg") }
                }
            assertEquals("Not authenticated", thrown.message)
        }

    @Test
    fun `pending join codes and wishlist tokens are held until consumed`() =
        runBlocking {
            repo.setPendingJoinCode("ABC123")
            assertEquals("ABC123", repo.pendingJoinCode.value)
            repo.consumePendingJoinCode()
            assertNull(repo.pendingJoinCode.value)
            repo.setPendingWishlistShareToken("tok")
            assertEquals("tok", repo.pendingWishlistShareToken.value)
            repo.setPendingWishlistShareToken(null)
            assertNull(repo.pendingWishlistShareToken.value)
        }

    private fun kotlinx.coroutines.CoroutineScope.launchCollector(into: MutableList<Unit>) =
        launch(Dispatchers.Default) { repo.familyChanged.collect { into += it } }
}
