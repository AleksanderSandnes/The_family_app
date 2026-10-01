package com.sandnes.familyapp.ui.wishlist

import com.sandnes.familyapp.R
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.runSwipeDelete
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WishlistScreensTest : ComposeScreenTest() {
    private val mine = """{"id":"w1","owner_user_id":"u1","family_id":"f1","name":"Birthday"}"""
    private val theirs = """{"id":"w2","owner_user_id":"u2","family_id":"f1","name":"Christmas"}"""
    private val bike = """{"id":"x1","wishlist_id":"w1","user_id":"u1","text":"Bike","checked":false,"link":"https://b.example/bike","price":"1499","description":"Red one","image_url":"https://img.example/b.png"}"""
    private val book = """{"id":"x2","wishlist_id":"w1","user_id":"u1","text":"Book","checked":true}"""
    private val lamp = """{"id":"x3","wishlist_id":"w1","user_id":"u1","text":"Lamp","checked":false}"""

    @Before
    fun reset() = resetCache(WishlistViewModel::class.java)

    @After
    fun clear() = resetCache(WishlistViewModel::class.java)

    private fun viewModel(
        wishlists: String = "[$mine,$theirs]",
        wishes: String = "[$bike,$book,$lamp]",
        reservations: String = "[]",
    ): WishlistViewModel {
        backend.onJson(HttpMethod.Get, "/rest/v1/wishlists", wishlists)
        backend.onJson(HttpMethod.Get, "/rest/v1/wishes", wishes)
        backend.onJson(HttpMethod.Get, "/rest/v1/wish_reservations", reservations)
        backend.onJson(HttpMethod.Get, "/rest/v1/wishlist_shares", "[]")
        backend.onJson(HttpMethod.Post, "/rest/v1/rpc/ensure_wishlist_share_token", "\"tok\"")
        val repo =
            fakeRepo {
                coEvery { getUser("u2") } returns
                    com.sandnes.familyapp.data
                        .UserModel(id = "u2", name = "Bob", familyId = "f1")
                every { pendingWishlistShareToken } returns kotlinx.coroutines.flow.MutableStateFlow(null)
            }
        return WishlistViewModel(repo)
    }

    @Test
    fun `lists my wishlists and the ones shared with me`() {
        var opened: String? = null
        val vm = viewModel()
        compose.setContent { WishlistScreen(onBack = {}, onOpen = { opened = it }, viewModel = vm) }
        compose.waitForText("Birthday")
        assertTrue(compose.hasText("Christmas"))
        compose.clickText("Birthday")
        assertTrue(opened == "w1")
    }

    @Test
    fun `empty state creates a wishlist`() {
        val vm = viewModel(wishlists = "[]")
        compose.setContent { WishlistScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_wishlists_yet))
        compose.clickText(str(R.string.new_wishlist))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.typeInto("Gadgets")
        compose.clickText(str(R.string.create))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishlists", HttpMethod.Post).isNotEmpty() }
    }

    @Test
    fun `owners can swipe their wishlist away after confirming`() {
        val vm = viewModel(wishlists = "[$mine]")
        compose.setContent { WishlistScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText("Birthday")
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_wishlist_q))
        compose.clickText(str(R.string.cancel))
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_wishlist_q))
        compose.clickText(str(R.string.delete))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishlists", HttpMethod.Delete).isNotEmpty() }
    }

    @Test
    fun `owner view shows wishes toggles claims and deletes with undo`() {
        val vm = viewModel()
        compose.setContent { WishlistDetailScreen(wishlistId = "w1", onBack = {}, viewModel = vm) }
        compose.waitForText("Bike", substring = true)
        assertTrue(compose.hasText("Book"))
        compose.clickDescription(str(R.string.mark_as_claimed), index = 0)
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishes", HttpMethod.Patch).isNotEmpty() }
        compose.runSwipeDelete()
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishes", HttpMethod.Delete).isNotEmpty() }
        compose.waitForText(str(R.string.wish_deleted))
        compose.clickText(str(R.string.undo))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishes", HttpMethod.Post).isNotEmpty() }
    }

    @Test
    fun `owner adds a wish and edits an existing one`() {
        val vm = viewModel()
        compose.setContent { WishlistDetailScreen(wishlistId = "w1", onBack = {}, viewModel = vm) }
        compose.waitForText("Bike", substring = true)
        compose.clickText(str(R.string.add_a_wish), index = 0)
        compose.waitForText(str(R.string.add_photo_optional))
        compose.typeInto("Lego", 0)
        compose.typeInto("Big box", 1)
        compose.typeInto("https://lego.example", 2)
        compose.typeInto("649", 3)
        compose.clickText(str(R.string.add))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishes", HttpMethod.Post).isNotEmpty() }

        compose.clickText("Lamp")
        compose.waitForText(str(R.string.edit_wish))
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishes", HttpMethod.Patch).isNotEmpty() }
    }

    @Test
    fun `owner menu renames changes appearance shares and exports`() {
        val vm = viewModel()
        compose.setContent { WishlistDetailScreen(wishlistId = "w1", onBack = {}, viewModel = vm) }
        compose.waitForText("Bike", substring = true)
        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.rename_wishlist))
        compose.waitForText(str(R.string.save))
        compose.typeInto("2")
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wishlists", HttpMethod.Patch).isNotEmpty() }

        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.change_icon))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.clickText(str(R.string.save))

        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.share_link))
        compose.waitForIdle()

        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.export_pdf))
        compose.waitForIdle()
    }

    @Test
    fun `members reserve and unreserve wishes from the list and the detail dialog`() {
        val vm =
            viewModel(
                wishlists = "[$theirs]",
                wishes = "[$bike,$book,$lamp]",
                reservations = """[{"id":"r1","wish_id":"x2","reserved_by":"u3"},{"id":"r2","wish_id":"x3","reserved_by":"u1"}]""",
            )
        compose.setContent { WishlistDetailScreen(wishlistId = "w2", onBack = {}, viewModel = vm) }
        compose.waitForText("Bike", substring = true)
        assertTrue(compose.hasText(str(R.string.reservations_are_hidden_from, "Bob") + " 🤫"))
        assertFalse(compose.hasText(str(R.string.add_a_wish)))
        assertTrue(compose.hasText(str(R.string.reserved)))
        assertTrue(compose.hasText(str(R.string.reserved_by_you)))
        compose.clickText(str(R.string.reserve), index = 0)
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wish_reservations", HttpMethod.Post).isNotEmpty() }
        // Un-reserve my own claim from the card.
        compose.clickText(str(R.string.reserved_by_you), index = 0)
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wish_reservations", HttpMethod.Delete).isNotEmpty() }
        // Open a wish for the full detail dialog and reserve from there.
        compose.clickText("Bike", substring = true)
        compose.waitForText(str(R.string.close))
        assertTrue(compose.hasText("Red one"))
        compose.clickText(str(R.string.reserve), index = 1)
    }

    @Test
    fun `detail dialog offers unreserve for my claim and hides the action for others`() {
        val vm =
            viewModel(
                wishlists = "[$theirs]",
                wishes = "[$book,$lamp]",
                reservations = """[{"id":"r1","wish_id":"x2","reserved_by":"u3"},{"id":"r2","wish_id":"x3","reserved_by":"u1"}]""",
            )
        compose.setContent { WishlistDetailScreen(wishlistId = "w2", onBack = {}, viewModel = vm) }
        compose.waitForText("Book")
        compose.clickText("Book")
        compose.waitForText(str(R.string.close))
        compose.clickText(str(R.string.close))
        compose.clickText("Lamp")
        compose.waitForText(str(R.string.unreserve))
        compose.clickText(str(R.string.unreserve))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/wish_reservations", HttpMethod.Delete).isNotEmpty() }
    }

    @Test
    fun `empty wishlists show their empty state`() {
        val vm = viewModel(wishes = "[]")
        compose.setContent { WishlistDetailScreen(wishlistId = "w1", onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_wishes_yet))
    }
}
