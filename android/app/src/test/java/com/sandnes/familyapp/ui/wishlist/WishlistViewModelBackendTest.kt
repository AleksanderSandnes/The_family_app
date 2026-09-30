package com.sandnes.familyapp.ui.wishlist

import android.content.Context
import android.net.Uri
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.data.WishModel
import com.sandnes.familyapp.data.WishlistModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** WishlistViewModel against a fake Supabase backend, so the real query/decoding paths run. */
class WishlistViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val pendingToken = MutableStateFlow<String?>(null)

    private val mine = """{"id":"w1","owner_user_id":"u1","family_id":"f1","name":"Birthday"}"""
    private val theirs = """{"id":"w2","owner_user_id":"u2","family_id":"f1","name":"Christmas"}"""
    private val foreign = """{"id":"w3","owner_user_id":"u9","family_id":"zzz","name":"Elsewhere"}"""
    private val bike = """{"id":"x1","wishlist_id":"w1","user_id":"u1","text":"Bike","checked":false}"""
    private val book = """{"id":"x2","wishlist_id":"w1","user_id":"u1","text":"Book","checked":true,"link":"https://b.example","price":"99"}"""

    private fun resetCompanionCache() {
        val cacheField = WishlistViewModel::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        cacheField.set(null, emptyList<Any?>())
    }

    @Before
    fun setUp() {
        backend.install()
        resetCompanionCache()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.pendingWishlistShareToken } returns pendingToken
        coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = "f1")
        coEvery { repo.getUser("u2") } returns UserModel(id = "u2", name = "Bob", familyId = "f1")
        backend.onJson(HttpMethod.Get, "/rest/v1/wishlists", "[$mine,$theirs,$foreign]")
        backend.onJson(HttpMethod.Get, "/rest/v1/wishes", "[$bike,$book]")
        backend.onJson(HttpMethod.Get, "/rest/v1/wishlist_shares", "[]")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/wish_reservations",
            """[{"id":"r1","wish_id":"x1","reserved_by":"u2"}]""",
        )
    }

    @After
    fun tearDown() {
        backend.uninstall()
        // The companion cache is process-wide; don't leak loaded lists into other test classes.
        resetCompanionCache()
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): WishlistViewModel {
        val vm = WishlistViewModel(repo)
        userId.value = "u1"
        settle { vm.wishlists.value.isNotEmpty() }
        return vm
    }

    @Test
    fun `loads family wishlists with owner names and drops other families`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals(listOf("w1", "w2"), vm.wishlists.value.map { it.id })
            assertEquals(listOf("Ada", "Bob"), vm.wishlists.value.map { it.ownerName })
            assertEquals("u1", vm.currentUserId.value)
        }

    @Test
    fun `a family-less user only sees personal wishlists`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = null)
            backend.onJson(HttpMethod.Get, "/rest/v1/wishlists", """[{"id":"p1","owner_user_id":"u1","name":"Solo"},$mine]""")
            val vm = loaded()
            assertEquals(listOf("p1"), vm.wishlists.value.map { it.id })
        }

    @Test
    fun `signing out clears wishlists`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            userId.value = null
            settle { vm.wishlists.value.isEmpty() }
            assertNull(vm.currentUserId.value)
        }

    @Test
    fun `shared wishlists are flagged and refresh reloads`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/wishlist_shares", """[{"wishlist_id":"w3"}]""")
            val vm = loaded()
            settle { vm.sharedWishlists.value.isNotEmpty() }
            assertTrue(vm.sharedWishlists.value.all { it.sharedWithMe })
            val before = backend.requestsTo("wishlists", HttpMethod.Get).size
            finish(vm.refresh())
            assertTrue(backend.requestsTo("wishlists", HttpMethod.Get).size > before)
        }

    @Test
    fun `loadWishlistDetail loads wishes, owner and visible reservations`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w1"))
            assertEquals("w1", vm.selectedWishlist.value?.id)
            assertEquals("Ada", vm.selectedWishlist.value?.ownerName)
            assertEquals(listOf("x1", "x2"), vm.wishes.value.map { it.id })
            assertEquals("u2", vm.reservations.value["x1"]?.reservedBy)
            finish(vm.loadWishlistDetail("w1"))
            assertEquals(2, vm.wishes.value.size)
        }

    @Test
    fun `detail for a missing wishlist selects nothing`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Get, "/rest/v1/wishlists", "[]")
            backend.onJson(HttpMethod.Get, "/rest/v1/wishes", "[]")
            finish(vm.loadWishlistDetail("nope"))
            assertNull(vm.selectedWishlist.value)
            assertTrue(vm.reservations.value.isEmpty())
        }

    @Test
    fun `reserve and unreserve write reservations`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w2"))
            finish(vm.reserve(WishModel(id = "x2", wishlistId = "w2")))
            val post = backend.requestsTo("wish_reservations", HttpMethod.Post).single()
            assertTrue(post.body.contains("\"wish_id\":\"x2\""))
            assertTrue(post.body.contains("\"reserved_by\":\"u1\""))
            finish(vm.unreserve(WishModel(id = "x2", wishlistId = "w2")))
            val delete = backend.requestsTo("wish_reservations", HttpMethod.Delete).single()
            assertTrue(delete.query.contains("wish_id=eq.x2"))
            assertTrue(delete.query.contains("reserved_by=eq.u1"))
        }

    @Test
    fun `reservation failures are reported`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/wish_reservations", "{}", HttpStatusCode.Conflict)
            finish(vm.reserve(WishModel(id = "x2", wishlistId = "w2")))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            backend.onJson(HttpMethod.Delete, "/rest/v1/wish_reservations", "{}", HttpStatusCode.InternalServerError)
            finish(vm.unreserve(WishModel(id = "x2", wishlistId = "w2")))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `share link is minted from the backend token`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/ensure_wishlist_share_token", "\"tok123\"")
            assertEquals("familyapp://wishlist?token=tok123", vm.shareLink("w1"))
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/ensure_wishlist_share_token", "{}", HttpStatusCode.Forbidden)
            assertNull(vm.shareLink("w1"))
        }

    @Test
    fun `redeeming a share token accepts it and reloads`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/accept_wishlist_share", "\"w3\"")
            finish(vm.redeemShareToken("tok"))
            assertTrue(
                backend
                    .requestsTo("rpc/accept_wishlist_share")
                    .single()
                    .body
                    .contains("\"p_token\":\"tok\""),
            )
            pendingToken.value = "tok"
            assertEquals("tok", vm.pendingShareToken.value)
            vm.consumePendingShareToken()
        }

    @Test
    fun `addWishlist posts family owner name icon and colour`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.addWishlist("Holiday", icon = "flight", color = 7))
            val body = backend.requestsTo("wishlists", HttpMethod.Post).single().body
            assertTrue(body.contains("\"name\":\"Holiday\""))
            assertTrue(body.contains("\"family_id\":\"f1\""))
            assertTrue(body.contains("\"icon\":\"flight\""))
            assertTrue(body.contains("\"color\":7"))
        }

    @Test
    fun `addWishlist without a family omits it and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = null)
            backend.onJson(HttpMethod.Post, "/rest/v1/wishlists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.addWishlist("Solo"))
            assertFalse(
                backend
                    .requestsTo("wishlists", HttpMethod.Post)
                    .single()
                    .body
                    .contains("family_id"),
            )
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `addWishlist does nothing while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = WishlistViewModel(repo)
            finish(vm.addWishlist("Nope"))
            assertTrue(backend.requestsTo("wishlists", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `deleteWishlist removes it and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.deleteWishlist(WishlistModel(id = "w1")))
            assertTrue(
                backend
                    .requestsTo("wishlists", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=eq.w1"),
            )
            backend.onJson(HttpMethod.Delete, "/rest/v1/wishlists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.deleteWishlist(WishlistModel(id = "w1")))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
        }

    @Test
    fun `rename icon and colour patch the wishlist`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w1"))
            finish(vm.renameWishlist("w1", "Party"))
            finish(vm.changeWishlistIcon("w1", "cake"))
            finish(vm.changeWishlistColor("w1", 5))
            val patches = backend.requestsTo("wishlists", HttpMethod.Patch)
            assertEquals(3, patches.size)
            assertTrue(patches[0].body.contains("\"name\":\"Party\""))
            assertTrue(patches[1].body.contains("\"icon\":\"cake\""))
            assertTrue(patches[2].body.contains("\"color\":5"))
        }

    @Test
    fun `wishlist edit failures are reported`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Patch, "/rest/v1/wishlists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.renameWishlist("w1", "x"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            finish(vm.changeWishlistIcon("w1", "x"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            finish(vm.changeWishlistColor("w1", null))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `addWish trims optional fields and posts the wish`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val context = mockk<Context>(relaxed = true)
            finish(
                vm.addWish(
                    context,
                    "w1",
                    WishDraft(text = "Lego", link = " https://l.example ", price = " 49 ", description = "  "),
                ),
            )
            val body = backend.requestsTo("rest/v1/wishes", HttpMethod.Post).single().body
            assertTrue(body.contains("\"text\":\"Lego\""))
            assertTrue(body.contains("\"link\":\"https://l.example\""))
            assertTrue(body.contains("\"price\":\"49\""))
            assertFalse(body.contains("description"))
            assertFalse(body.contains("image_url"))
        }

    @Test
    fun `addWish tolerates an unreadable image and reports insert failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val context = mockk<Context>(relaxed = true)
            every { context.contentResolver.openInputStream(any()) } returns null
            backend.onJson(HttpMethod.Post, "/rest/v1/wishes", "{}", HttpStatusCode.InternalServerError)
            finish(vm.addWish(context, "w1", WishDraft(text = "Pic", imageUri = mockk<Uri>(relaxed = true))))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            assertFalse(
                backend
                    .requestsTo("rest/v1/wishes", HttpMethod.Post)
                    .single()
                    .body
                    .contains("image_url"),
            )
        }

    @Test
    fun `addWish does nothing while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = WishlistViewModel(repo)
            finish(vm.addWish(mockk(relaxed = true), "w1", WishDraft(text = "x")))
            assertTrue(backend.requestsTo("rest/v1/wishes", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `updateWish patches the edited fields`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w1"))
            finish(vm.updateWish(mockk(relaxed = true), "x2", WishDraft(text = "Novel", link = "", price = "10", description = "Good")))
            val patch = backend.requestsTo("rest/v1/wishes", HttpMethod.Patch).single()
            assertTrue(patch.body.contains("\"text\":\"Novel\""))
            assertTrue(patch.body.contains("\"price\":\"10\""))
            assertTrue(patch.body.contains("\"description\":\"Good\""))
            assertTrue(patch.query.contains("id=eq.x2"))
        }

    @Test
    fun `updateWish keeps the old image when a new one cannot be read and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w1"))
            val context = mockk<Context>(relaxed = true)
            every { context.contentResolver.openInputStream(any()) } returns null
            backend.onJson(HttpMethod.Patch, "/rest/v1/wishes", "{}", HttpStatusCode.InternalServerError)
            finish(vm.updateWish(context, "x1", WishDraft(text = "Bike", imageUri = mockk<Uri>(relaxed = true))))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            finish(vm.updateWish(context, "unknown", WishDraft(text = "Ghost")))
        }

    @Test
    fun `toggle flips a wish and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w1"))
            finish(vm.toggle(WishModel(id = "x1", wishlistId = "w1", checked = false)))
            assertTrue(
                backend
                    .requestsTo("rest/v1/wishes", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("\"checked\":true"),
            )
            backend.onJson(HttpMethod.Patch, "/rest/v1/wishes", "{}", HttpStatusCode.InternalServerError)
            finish(vm.toggle(WishModel(id = "x1", wishlistId = "w1")))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `deleteWish offers undo and restoreWish re-inserts with all fields`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadWishlistDetail("w1"))
            val wish = WishModel(id = "x2", wishlistId = "w1", userId = "u1", text = "Book", checked = true, link = "l", price = "9", imageUrl = "i")
            finish(vm.deleteWish(wish))
            assertEquals(wish, vm.undoWish.value)
            vm.clearUndo()
            assertNull(vm.undoWish.value)
            finish(vm.restoreWish(wish))
            val body = backend.requestsTo("rest/v1/wishes", HttpMethod.Post).single().body
            assertTrue(body.contains("\"link\":\"l\"") && body.contains("\"price\":\"9\"") && body.contains("\"image_url\":\"i\""))
        }

    @Test
    fun `wish deletion and restore failures are reported`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val wish = WishModel(id = "x2", wishlistId = "w1", userId = "u1", text = "Book")
            backend.onJson(HttpMethod.Delete, "/rest/v1/wishes", "{}", HttpStatusCode.InternalServerError)
            finish(vm.deleteWish(wish))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
            assertNull(vm.undoWish.value)
            vm.clearError()
            backend.onJson(HttpMethod.Post, "/rest/v1/wishes", "{}", HttpStatusCode.InternalServerError)
            finish(vm.restoreWish(wish))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }
}
