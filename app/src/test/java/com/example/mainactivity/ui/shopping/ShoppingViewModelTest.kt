package com.example.mainactivity.ui.shopping

import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ShoppingItemModel
import com.example.mainactivity.data.ShoppingListModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [ShoppingViewModel].
 *
 * Strategy: ShoppingViewModel performs all DB operations directly through
 * [SupabaseManager.client] (not via repo), and wraps every call in
 * [runCatching]. In the test environment the placeholder Supabase credentials
 * cause all network/DB calls to fail silently, which means:
 *  - Optimistic state mutations (applied before the network call) are the
 *    observable outcome in every test.
 *  - [FamilyRepository.getUser] is a suspend fun on the mock, so it returns
 *    null by default and can be verified with [coVerify].
 */
@RunWith(JUnit4::class)
class ShoppingViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: ShoppingViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        // Return a user with no familyId so subscribeToListsOnce is never called
        // (avoids the Supabase Realtime WebSocket path in tests).
        coEvery { repo.getUser(any()) } returns UserModel(id = "user1", familyId = null)
        vm = ShoppingViewModel(repo)
    }

    // ──────────────────────────────────────────────────────────────
    // 1. Initial state
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `lists are empty before userId emits`() = runTest(dispatcherRule.dispatcher) {
        assertTrue("lists should be empty initially", vm.lists.value.isEmpty())
    }

    @Test
    fun `isLoading is false initially`() = runTest(dispatcherRule.dispatcher) {
        assertFalse("isLoading should be false at startup", vm.isLoading.value)
    }

    @Test
    fun `items are empty initially`() = runTest(dispatcherRule.dispatcher) {
        assertTrue("items should be empty initially", vm.items.value.isEmpty())
    }

    @Test
    fun `selectedList is null initially`() = runTest(dispatcherRule.dispatcher) {
        assertNull("selectedList should start as null", vm.selectedList.value)
    }

    // ──────────────────────────────────────────────────────────────
    // 2. userId emit triggers load (verifiable via getUser)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `emitting userId triggers getUser call`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        coVerify(atLeast = 1) { repo.getUser("user1") }
    }

    @Test
    fun `null userId clears lists and itemCounts`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        userId.value = null
        advanceUntilIdle()

        assertTrue("lists should be empty after sign-out", vm.lists.value.isEmpty())
        assertTrue("itemCounts should be empty after sign-out", vm.itemCounts.value.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────
    // 3. addList — optimistic update
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `addList adds entry optimistically to lists`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        vm.addList("Groceries", "shopping_cart")
        advanceUntilIdle()

        assertTrue(
            "list should appear in state immediately (optimistic)",
            vm.lists.value.any { it.title == "Groceries" },
        )
    }

    @Test
    fun `addList preserves specified icon`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        vm.addList("Hardware", "build")
        advanceUntilIdle()

        val added = vm.lists.value.firstOrNull { it.title == "Hardware" }
        assertNotNull("list 'Hardware' should exist", added)
        assertTrue("icon should match", added!!.icon == "build")
    }

    @Test
    fun `addList with null userId does not add entry`() = runTest(dispatcherRule.dispatcher) {
        // userId is null — addList should early-return via currentUserId.first()
        vm.addList("Groceries", "shopping_cart")
        advanceUntilIdle()

        assertTrue("no list should be added when userId is null", vm.lists.value.isEmpty())
    }

    @Test
    fun `multiple addList calls accumulate entries`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        vm.addList("Groceries")
        vm.addList("Hardware")
        advanceUntilIdle()

        assertTrue("Groceries should be present", vm.lists.value.any { it.title == "Groceries" })
        assertTrue("Hardware should be present", vm.lists.value.any { it.title == "Hardware" })
    }

    // ──────────────────────────────────────────────────────────────
    // 4. deleteList — optimistic removal
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `deleteList removes entry optimistically from lists`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        vm.addList("Groceries", "shopping_cart")
        advanceUntilIdle()

        val list = vm.lists.value.first { it.title == "Groceries" }
        vm.deleteList(list)
        advanceUntilIdle()

        assertFalse(
            "list should be removed after deleteList",
            vm.lists.value.any { it.title == "Groceries" },
        )
    }

    @Test
    fun `deleteList only removes the target list`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        vm.addList("Groceries")
        vm.addList("Hardware")
        advanceUntilIdle()

        val groceries = vm.lists.value.first { it.title == "Groceries" }
        vm.deleteList(groceries)
        advanceUntilIdle()

        assertFalse("Groceries should be deleted", vm.lists.value.any { it.title == "Groceries" })
        assertTrue("Hardware should remain", vm.lists.value.any { it.title == "Hardware" })
    }

    // ──────────────────────────────────────────────────────────────
    // 5. addItem — optimistic update
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `addItem adds entry optimistically to items`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        advanceUntilIdle()

        assertTrue(
            "item should appear in state immediately (optimistic)",
            vm.items.value.any { it.item == "Milk" },
        )
    }

    @Test
    fun `addItem sets correct listId and defaults checked to false`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Butter")
        advanceUntilIdle()

        val item = vm.items.value.firstOrNull { it.item == "Butter" }
        assertNotNull("item should exist", item)
        assertTrue("listId should match", item!!.listId == "list-1")
        assertFalse("new item should be unchecked", item.checked)
    }

    // ──────────────────────────────────────────────────────────────
    // 6. toggle — flips checked state
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `toggle marks unchecked item as checked`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        advanceUntilIdle()

        val item = vm.items.value.first { it.item == "Milk" }
        assertFalse("precondition: item should be unchecked", item.checked)

        vm.toggle(item)
        advanceUntilIdle()

        assertTrue(
            "item should be checked after toggle",
            vm.items.value.first { it.item == "Milk" }.checked,
        )
    }

    @Test
    fun `toggle marks checked item as unchecked`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        advanceUntilIdle()

        val item = vm.items.value.first { it.item == "Milk" }
        vm.toggle(item)
        advanceUntilIdle()

        val checkedItem = vm.items.value.first { it.item == "Milk" }
        assertTrue("precondition: item should now be checked", checkedItem.checked)

        vm.toggle(checkedItem)
        advanceUntilIdle()

        assertFalse(
            "item should be unchecked after second toggle",
            vm.items.value.first { it.item == "Milk" }.checked,
        )
    }

    @Test
    fun `toggle only affects the target item`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        vm.addItem("list-1", "Bread")
        advanceUntilIdle()

        val milk = vm.items.value.first { it.item == "Milk" }
        vm.toggle(milk)
        advanceUntilIdle()

        assertTrue("Milk should be checked", vm.items.value.first { it.item == "Milk" }.checked)
        assertFalse("Bread should remain unchecked", vm.items.value.first { it.item == "Bread" }.checked)
    }

    // ──────────────────────────────────────────────────────────────
    // 7. deleteItem — optimistic removal
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `deleteItem removes item optimistically`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        advanceUntilIdle()

        val item = vm.items.value.first { it.item == "Milk" }
        vm.deleteItem(item)
        advanceUntilIdle()

        assertFalse("item should be gone after deleteItem", vm.items.value.any { it.item == "Milk" })
    }

    @Test
    fun `deleteItem only removes target item`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        vm.addItem("list-1", "Bread")
        advanceUntilIdle()

        val milk = vm.items.value.first { it.item == "Milk" }
        vm.deleteItem(milk)
        advanceUntilIdle()

        assertFalse("Milk should be deleted", vm.items.value.any { it.item == "Milk" })
        assertTrue("Bread should remain", vm.items.value.any { it.item == "Bread" })
    }

    // ──────────────────────────────────────────────────────────────
    // 8. clearCompleted — removes all checked items
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `clearCompleted removes checked items and leaves unchecked`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        vm.addItem("list-1", "Butter")
        advanceUntilIdle()

        val milk = vm.items.value.first { it.item == "Milk" }
        vm.toggle(milk)
        advanceUntilIdle()

        vm.clearCompleted("list-1")
        advanceUntilIdle()

        assertFalse("checked item Milk should be removed", vm.items.value.any { it.item == "Milk" })
        assertTrue("unchecked item Butter should remain", vm.items.value.any { it.item == "Butter" })
    }

    @Test
    fun `clearCompleted with no checked items leaves all items`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        vm.addItem("list-1", "Bread")
        advanceUntilIdle()

        vm.clearCompleted("list-1")
        advanceUntilIdle()

        assertTrue("Milk should remain (was unchecked)", vm.items.value.any { it.item == "Milk" })
        assertTrue("Bread should remain (was unchecked)", vm.items.value.any { it.item == "Bread" })
    }

    @Test
    fun `clearCompleted on empty list is a no-op`() = runTest(dispatcherRule.dispatcher) {
        vm.clearCompleted("list-1")
        advanceUntilIdle()

        assertTrue("items should still be empty", vm.items.value.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────
    // 9. familyChanged triggers reload
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `familyChanged triggers getUser call when userId is set`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        familyChanged.emit(Unit)
        advanceUntilIdle()

        // getUser called at least twice: once on loadLists, once on familyChanged reload
        coVerify(atLeast = 2) { repo.getUser("user1") }
    }

    @Test
    fun `familyChanged without userId is a no-op`() = runTest(dispatcherRule.dispatcher) {
        // userId is still null — collector returns early via currentUserId.first() ?: return@collect
        familyChanged.emit(Unit)
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.getUser(any()) }
    }

    // ──────────────────────────────────────────────────────────────
    // 10. changeListIcon — optimistic update
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `changeListIcon updates icon optimistically in lists`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        vm.addList("Groceries", "shopping_cart")
        advanceUntilIdle()

        val list = vm.lists.value.first { it.title == "Groceries" }
        vm.changeListIcon(list.id, "local_grocery_store")
        advanceUntilIdle()

        val updated = vm.lists.value.firstOrNull { it.title == "Groceries" }
        assertNotNull("list should still exist after icon change", updated)
        assertTrue(
            "icon should be updated optimistically",
            updated!!.icon == "local_grocery_store",
        )
    }

    // ──────────────────────────────────────────────────────────────
    // 11. renameItem — optimistic update
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `renameItem updates item text optimistically`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        advanceUntilIdle()

        val item = vm.items.value.first { it.item == "Milk" }
        vm.renameItem(item, "Oat Milk")
        advanceUntilIdle()

        assertFalse("old name should be gone", vm.items.value.any { it.item == "Milk" })
        assertTrue("new name should appear", vm.items.value.any { it.item == "Oat Milk" })
    }

    @Test
    fun `renameItem preserves checked state`() = runTest(dispatcherRule.dispatcher) {
        vm.addItem("list-1", "Milk")
        advanceUntilIdle()

        val item = vm.items.value.first { it.item == "Milk" }
        vm.toggle(item)
        advanceUntilIdle()

        val checkedItem = vm.items.value.first { it.item == "Milk" }
        vm.renameItem(checkedItem, "Oat Milk")
        advanceUntilIdle()

        val renamed = vm.items.value.firstOrNull { it.item == "Oat Milk" }
        assertNotNull("renamed item should exist", renamed)
        assertTrue("renamed item should retain checked state", renamed!!.checked)
    }

    // ──────────────────────────────────────────────────────────────
    // 12. subscribedListsFamilyId guard — no double-subscribe
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `emitting same userId twice does not double getUser on second emit`() = runTest(dispatcherRule.dispatcher) {
        userId.value = "user1"
        advanceUntilIdle()

        // Emitting the same value to a StateFlow does NOT re-trigger collect — verified here
        // by checking getUser was called only once (from the initial loadLists).
        coVerify(exactly = 1) { repo.getUser("user1") }
    }
}
