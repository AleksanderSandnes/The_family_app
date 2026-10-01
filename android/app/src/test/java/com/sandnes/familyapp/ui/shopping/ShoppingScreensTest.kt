package com.sandnes.familyapp.ui.shopping

import com.sandnes.familyapp.R
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickButtonBeside
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.editField
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.imeDone
import com.sandnes.familyapp.testutil.runSwipeDelete
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShoppingScreensTest : ComposeScreenTest() {
    private val mine = """{"id":"l1","title":"Weekly","owner_user_id":"u1","family_id":"f1"}"""
    private val theirs = """{"id":"l2","title":"Party","owner_user_id":"u2","family_id":"f1"}"""
    private val done = """{"id":"l3","title":"Finished","owner_user_id":"u2","family_id":"f1"}"""
    private val milk = """{"id":"i1","list_id":"l1","item":"Milk","checked":true}"""
    private val eggs = """{"id":"i2","list_id":"l1","item":"Eggs","checked":false}"""
    private val cake = """{"id":"i3","list_id":"l3","item":"Cake","checked":true}"""

    @Before
    fun reset() = resetCache(ShoppingViewModel::class.java)

    @After
    fun clear() = resetCache(ShoppingViewModel::class.java)

    private fun viewModel(
        lists: String = "[$mine,$theirs,$done]",
        items: String = "[$milk,$eggs,$cake]",
        admin: Boolean = false,
    ): ShoppingViewModel {
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_lists", lists)
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_items", items)
        return ShoppingViewModel(fakeRepo(admin = admin))
    }

    @Test
    fun `shows lists with progress and opens one`() {
        var opened: String? = null
        val vm = viewModel()
        compose.setContent { ShoppingScreen(onOpenList = { opened = it }, onBack = {}, viewModel = vm) }
        compose.waitForText("Weekly")
        compose.waitForText(str(R.string.count_of_count_bought, 1, 2))
        assertTrue(compose.hasText(str(R.string.all_bought)))
        assertTrue(compose.hasText(str(R.string.no_items_yet)))
        compose.clickText("Weekly")
        assertEquals("l1", opened)
    }

    @Test
    fun `empty state offers a new list and creates one`() {
        val vm = viewModel(lists = "[]", items = "[]")
        compose.setContent { ShoppingScreen(onOpenList = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_lists_yet))
        compose.clickDescription(str(R.string.new_list))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.typeInto("Camping")
        compose.clickText(str(R.string.create))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_lists", HttpMethod.Post).isNotEmpty() }
        assertTrue(
            backend
                .requestsTo("/rest/v1/shopping_lists", HttpMethod.Post)
                .single()
                .body
                .contains("Camping"),
        )
    }

    @Test
    fun `only owned lists can be swiped away and deletion is confirmed`() {
        val vm = viewModel(lists = "[$mine,$theirs]", items = "[]")
        compose.setContent { ShoppingScreen(onOpenList = {}, viewModel = vm) }
        compose.waitForText("Weekly")
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_list_q))
        compose.clickText(str(R.string.cancel))
        assertTrue(backend.requestsTo("/rest/v1/shopping_lists", HttpMethod.Delete).isEmpty())
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_list_q))
        compose.clickText(str(R.string.delete))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_lists", HttpMethod.Delete).isNotEmpty() }
    }

    @Test
    fun `an admin may delete any list`() {
        val vm = viewModel(lists = "[$theirs]", items = "[]", admin = true)
        compose.setContent { ShoppingScreen(onOpenList = {}, viewModel = vm) }
        compose.waitForText("Party")
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_list_q))
    }

    @Test
    fun `detail screen lists active and completed items and toggles one`() {
        val vm = viewModel()
        compose.setContent { ShoppingDetailScreen(listId = "l1", onBack = {}, viewModel = vm) }
        compose.waitForText("Eggs")
        compose.waitForText("Milk")
        assertTrue(compose.hasText(str(R.string.count_left, 1)))
        compose.clickButtonBeside("Eggs")
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Patch).isNotEmpty() }
        // Collapse then expand the completed section.
        compose.clickDescription(str(R.string.hide_completed_items))
        assertFalse(compose.hasText("Milk"))
        compose.clickDescription(str(R.string.show_completed_items))
        assertTrue(compose.hasText("Milk"))
    }

    @Test
    fun `detail screen adds items through the bar and the keyboard`() {
        val vm = viewModel(items = "[]")
        compose.setContent { ShoppingDetailScreen(listId = "l1", onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.empty_list))
        compose.typeInto("Bread")
        compose.clickDescription(str(R.string.add_item))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Post).size == 1 }
        compose.typeInto("Jam")
        compose.imeDone()
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Post).size == 2 }
        // Blank input is ignored.
        compose.typeInto("   ")
        compose.imeDone()
        assertEquals(2, backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Post).size)
    }

    @Test
    fun `tapping an item row starts an edit that changes nothing until text is committed`() {
        val vm = viewModel()
        compose.setContent { ShoppingDetailScreen(listId = "l1", onBack = {}, viewModel = vm) }
        compose.waitForText("Eggs")
        compose.clickText("Eggs")
        compose.clickText("Milk")
        assertTrue(backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Patch).isEmpty())
    }

    @Test
    fun `deleting an item offers undo`() {
        val vm = viewModel()
        compose.setContent { ShoppingDetailScreen(listId = "l1", onBack = {}, viewModel = vm) }
        compose.waitForText("Eggs")
        compose.runSwipeDelete()
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Delete).isNotEmpty() }
        compose.waitForText(str(R.string.item_deleted))
        compose.clickText(str(R.string.undo))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Post).isNotEmpty() }
    }

    @Test
    fun `overflow menu renames the list changes its icon and clears completed items`() {
        val vm = viewModel(items = "[$milk,$eggs]")
        compose.setContent { ShoppingDetailScreen(listId = "l1", onBack = {}, viewModel = vm) }
        compose.waitForText("Eggs")
        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.rename_list))
        compose.waitForText(str(R.string.list_name))
        compose.editField("Weekly", "Groceries", done = false)
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_lists", HttpMethod.Patch).isNotEmpty() }

        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.change_icon))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.clickText(str(R.string.save))

        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.clear_completed_count, 1))
        compose.waitForText(str(R.string.clear_completed_q))
        compose.clickText(str(R.string.cancel))
        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.clear_completed_count, 1))
        compose.clickText(str(R.string.delete))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/shopping_items", HttpMethod.Delete).isNotEmpty() }
    }
}
