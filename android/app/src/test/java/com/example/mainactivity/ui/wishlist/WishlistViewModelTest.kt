package com.example.mainactivity.ui.wishlist

import android.content.Context
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import com.example.mainactivity.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [WishlistViewModel].
 *
 * The ViewModel calls SupabaseManager.client directly for all DB and Realtime operations.
 * DB/PostgREST calls are wrapped in runCatching and fail silently in the unit-test
 * environment (no real Supabase connection), so tests focus on:
 *
 *   - Initial StateFlow values
 *   - Optimistic mutations (add / rename / icon / delete) applied to wishlists StateFlow
 *   - Optimistic mutations (add / toggle / delete) applied to wishes StateFlow
 *   - Lifecycle triggers (userId flow, refresh()) that initiate reload
 *
 * Tests use UserModel(familyId = null) so that subscribeToWishlists() is never reached;
 * that function calls SupabaseManager.client.channel() and channel.subscribe() which are
 * NOT wrapped in runCatching.  subscribeToWishes() is invoked by loadWishlistDetail(), but
 * its subscription guard (subscribedWishesListId) prevents redundant calls after the first.
 * For wish-level operations, the first subscribe attempt targets the real Supabase URL but
 * OkHttp WebSocket initiation is non-blocking, so any connection failure propagates
 * asynchronously and does not throw inside the test dispatcher.
 */
@RunWith(JUnit4::class)
class WishlistViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: WishlistViewModel

    // addWish only dereferences context when an image is attached (never in these tests).
    private val ctx: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(SupabaseManager)
        every { SupabaseManager.client } throws RuntimeException("Supabase client not available in unit tests")
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        // Default: getUser returns null so loadWishlists exits before any DB calls
        coEvery { repo.getUser(any()) } returns null
        vm = WishlistViewModel(repo)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial state — before any coroutine is advanced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial wishlists is empty before coroutines run`() {
        assertTrue("wishlists must start empty", vm.wishlists.value.isEmpty())
    }

    @Test
    fun `initial selectedWishlist is null before coroutines run`() {
        assertNull("selectedWishlist must start null", vm.selectedWishlist.value)
    }

    @Test
    fun `initial wishes is empty before coroutines run`() {
        assertTrue("wishes must start empty", vm.wishes.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false before coroutines run`() {
        assertFalse("isLoading must start false", vm.isLoading.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Null userId — idle state, no load attempted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `null userId does not trigger getUser`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    @Test
    fun `null userId leaves wishlists empty and isLoading false`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            assertTrue(vm.wishlists.value.isEmpty())
            assertFalse(vm.isLoading.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Non-null userId — load is triggered
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `non-null userId triggers at least one getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            coVerify(atLeast = 1) { repo.getUser("u1") }
        }

    @Test
    fun `userId becoming null after non-null clears wishlists`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            userId.value = null
            advanceUntilIdle()

            assertTrue("wishlists should be cleared on null userId", vm.wishlists.value.isEmpty())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. addWishlist — optimistic entry visible immediately
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addWishlist creates optimistic entry with correct name and icon`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("Birthday Gifts", "cake")
            advanceUntilIdle()

            val lists = vm.wishlists.value
            assertEquals("Should have exactly one wishlist", 1, lists.size)
            assertEquals("Name should match", "Birthday Gifts", lists[0].name)
            assertEquals("Icon should match", "cake", lists[0].icon)
        }

    @Test
    fun `addWishlist uses default icon when none provided`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("No Icon List")
            advanceUntilIdle()

            assertEquals("card_giftcard", vm.wishlists.value[0].icon)
        }

    @Test
    fun `addWishlist sets ownerUserId to current user`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("My List")
            advanceUntilIdle()

            assertEquals("u1", vm.wishlists.value[0].ownerUserId)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. deleteWishlist — optimistic removal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleteWishlist removes the targeted wishlist`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("To Delete")
            advanceUntilIdle()

            val target = vm.wishlists.value.first()
            vm.deleteWishlist(target)
            advanceUntilIdle()

            assertTrue("Wishlists should be empty after delete", vm.wishlists.value.isEmpty())
        }

    @Test
    fun `deleteWishlist does not remove other lists`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("Keep Me")
            advanceUntilIdle()
            vm.addWishlist("Remove Me")
            advanceUntilIdle()

            val toRemove = vm.wishlists.value.first { it.name == "Remove Me" }
            vm.deleteWishlist(toRemove)
            advanceUntilIdle()

            assertEquals("One wishlist should remain", 1, vm.wishlists.value.size)
            assertEquals("Keep Me", vm.wishlists.value[0].name)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. changeWishlistIcon — optimistic icon update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `changeWishlistIcon updates icon optimistically in wishlists`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("Icons", "star")
            advanceUntilIdle()

            val wishlist = vm.wishlists.value.first()
            vm.changeWishlistIcon(wishlist.id, "favorite")
            advanceUntilIdle()

            val updated = vm.wishlists.value.firstOrNull { it.id == wishlist.id }
            assertEquals("Icon should be updated to favorite", "favorite", updated?.icon)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. renameWishlist — optimistic name update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `renameWishlist updates name optimistically in wishlists`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("Old Name")
            advanceUntilIdle()

            val wishlist = vm.wishlists.value.first()
            vm.renameWishlist(wishlist.id, "New Name")
            advanceUntilIdle()

            val updated = vm.wishlists.value.firstOrNull { it.id == wishlist.id }
            assertEquals("Name should be updated", "New Name", updated?.name)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. addWish — optimistic entry in wishes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addWish creates optimistic entry with correct text and userId`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWish(ctx, "wl-1", WishDraft("New bicycle"))
            advanceUntilIdle()

            val items = vm.wishes.value
            assertEquals("Should have exactly one wish", 1, items.size)
            assertEquals("Text should match", "New bicycle", items[0].text)
            assertEquals("userId should match", "u1", items[0].userId)
            assertFalse("Wish should start unchecked", items[0].checked)
        }

    @Test
    fun `addWish sets wishlistId on optimistic entry`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWish(ctx, "wishlist-99", WishDraft("Lego set"))
            advanceUntilIdle()

            assertEquals("wishlist-99", vm.wishes.value[0].wishlistId)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. deleteWish — optimistic removal from wishes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleteWish removes the targeted wish`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWish(ctx, "wl-1", WishDraft("Delete me"))
            advanceUntilIdle()

            val wish = vm.wishes.value.first()
            vm.deleteWish(wish)
            advanceUntilIdle()

            assertTrue("Wishes should be empty after delete", vm.wishes.value.isEmpty())
        }

    @Test
    fun `deleteWish only removes target leaving others intact`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWish(ctx, "wl-1", WishDraft("Keep me"))
            advanceUntilIdle()
            vm.addWish(ctx, "wl-1", WishDraft("Delete me"))
            advanceUntilIdle()

            val toDelete = vm.wishes.value.first { it.text == "Delete me" }
            vm.deleteWish(toDelete)
            advanceUntilIdle()

            assertEquals("One wish should remain", 1, vm.wishes.value.size)
            assertEquals("Keep me", vm.wishes.value[0].text)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. toggle — flips wish checked state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggle flips wish checked state from false to true`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWish(ctx, "wl-1", WishDraft("Gift"))
            advanceUntilIdle()

            val wish = vm.wishes.value.first()
            assertFalse("Wish should start unchecked", wish.checked)

            vm.toggle(wish)
            advanceUntilIdle()

            val toggled = vm.wishes.value.firstOrNull { it.id == wish.id }
            assertTrue("Wish should be checked after toggle", toggled?.checked == true)
        }

    @Test
    fun `toggle twice returns wish to original unchecked state`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWish(ctx, "wl-1", WishDraft("Gift"))
            advanceUntilIdle()

            val wish = vm.wishes.value.first()
            vm.toggle(wish)
            advanceUntilIdle()

            // subscribedWishesListId guard prevents double-subscribe on the second call
            val toggled = vm.wishes.value.first { it.id == wish.id }
            vm.toggle(toggled)
            advanceUntilIdle()

            assertFalse(
                "Wish should be unchecked after two toggles",
                vm.wishes.value
                    .first { it.id == wish.id }
                    .checked,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. refresh — forces a reload outside of the Flow collectors
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `refresh triggers another getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "u1"
            advanceUntilIdle()

            vm.refresh()
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("u1") }
        }

    @Test
    fun `refresh when userId is null does nothing`() =
        runTest(dispatcherRule.dispatcher) {
            // userId stays null
            vm.refresh()
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. StateFlow de-duplication — same userId value emitted twice
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `emitting same userId value twice does not duplicate wishlist entries`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", familyId = null)
            coEvery { repo.getUser("u1") } returns user
            userId.value = "u1"
            advanceUntilIdle()

            vm.addWishlist("Only Entry")
            advanceUntilIdle()

            // StateFlow de-duplicates equal values; a second emit with the same
            // userId does NOT re-trigger the collect lambda — state stays unchanged.
            userId.value = "u1"
            advanceUntilIdle()

            assertEquals(
                "There should still be exactly one wishlist",
                1,
                vm.wishlists.value.size,
            )
        }
}
