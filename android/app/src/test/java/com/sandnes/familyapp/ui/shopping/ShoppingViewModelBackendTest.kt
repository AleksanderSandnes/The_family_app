package com.sandnes.familyapp.ui.shopping

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.ShoppingItemModel
import com.sandnes.familyapp.data.ShoppingListModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** ShoppingViewModel against a fake Supabase backend, so the real query/decoding paths run. */
class ShoppingViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>

    private val weekly = """{"id":"l1","title":"Weekly","owner_user_id":"u1","family_id":"f1"}"""
    private val milk = """{"id":"i1","list_id":"l1","item":"Milk","checked":true}"""
    private val eggs = """{"id":"i2","list_id":"l1","item":"Eggs","checked":false}"""

    private fun resetCompanionCache() {
        val cacheField = ShoppingViewModel::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        cacheField.set(null, emptyList<Any?>())
    }

    @Before
    fun setUp() {
        backend.install()
        resetCompanionCache()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        coEvery { repo.getUser(any()) } returns UserModel(id = "u1", familyId = "f1")
        coEvery { repo.isFamilyAdmin(any()) } returns true
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_lists", "[$weekly]")
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_items", "[$milk,$eggs]")
    }

    @After
    fun tearDown() {
        backend.uninstall()
        // The companion cache is process-wide; don't leak loaded lists into other test classes.
        resetCompanionCache()
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    /** A ViewModel that has finished its initial load for user u1. */
    private fun loaded(): ShoppingViewModel {
        val vm = ShoppingViewModel(repo)
        userId.value = "u1"
        settle { vm.listProgress.value.isNotEmpty() }
        return vm
    }

    @Test
    fun `loads lists and per-list progress from the backend`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/shopping_lists",
                "[$weekly,{\"id\":\"l2\",\"title\":\"Other\",\"owner_user_id\":\"u9\",\"family_id\":\"zzz\"}]",
            )
            val vm = loaded()
            assertEquals(listOf("l1"), vm.lists.value.map { it.id })
            assertEquals(ListProgress(bought = 1, total = 2), vm.listProgress.value["l1"])
        }

    @Test
    fun `a user without a family only sees personal lists`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser(any()) } returns UserModel(id = "u1", familyId = null)
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/shopping_lists",
                "[{\"id\":\"p1\",\"title\":\"Mine\",\"owner_user_id\":\"u1\"},$weekly]",
            )
            val vm = ShoppingViewModel(repo)
            userId.value = "u1"
            settle { vm.lists.value.isNotEmpty() }
            assertEquals(listOf("p1"), vm.lists.value.map { it.id })
            assertTrue(
                backend
                    .requestsTo("shopping_lists")
                    .single()
                    .query
                    .contains("owner_user_id=eq.u1"),
            )
        }

    @Test
    fun `signing out clears lists and progress`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            userId.value = null
            settle { vm.lists.value.isEmpty() }
            assertTrue(vm.listProgress.value.isEmpty())
        }

    @Test
    fun `refresh and family changes reload the lists`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("shopping_lists", HttpMethod.Get).size
            finish(vm.refresh())
            assertTrue(backend.requestsTo("shopping_lists", HttpMethod.Get).size > before)
            val afterRefresh = backend.requestsTo("shopping_lists", HttpMethod.Get).size
            familyChanged.emit(Unit)
            settle { backend.requestsTo("shopping_lists", HttpMethod.Get).size > afterRefresh }
            assertTrue(vm.isAdmin.value)
        }

    @Test
    fun `addList posts the new list with family owner and colour`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.addList("Party", icon = "cake", color = 0xFF0000))
            val post = backend.requestsTo("shopping_lists", HttpMethod.Post).single()
            assertTrue(post.body.contains("\"title\":\"Party\""))
            assertTrue(post.body.contains("\"family_id\":\"f1\""))
            assertTrue(post.body.contains("\"owner_user_id\":\"u1\""))
            assertTrue(post.body.contains("\"icon\":\"cake\""))
            assertTrue(post.body.contains("\"color\":16711680"))
            assertNull(vm.errorRes.value)
        }

    @Test
    fun `addList for a family-less user omits family and colour`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser(any()) } returns UserModel(id = "u1", familyId = null)
            val vm = ShoppingViewModel(repo)
            userId.value = "u1"
            settle { vm.lists.value.isNotEmpty() || !vm.isLoading.value }
            finish(vm.addList("Solo"))
            val body = backend.requestsTo("shopping_lists", HttpMethod.Post).single().body
            assertTrue(!body.contains("family_id"))
            assertTrue(!body.contains("color"))
        }

    @Test
    fun `addList failure surfaces a save error that can be cleared`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/shopping_lists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.addList("Broken"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            assertNull(vm.errorRes.value)
        }

    @Test
    fun `addList does nothing while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ShoppingViewModel(repo)
            finish(vm.addList("Nope"))
            assertTrue(backend.requestsTo("shopping_lists", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `changing list colour and icon patches the row`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.changeListColor("l1", 255))
            finish(vm.changeListIcon("l1", "star"))
            val patches = backend.requestsTo("shopping_lists", HttpMethod.Patch)
            assertEquals(2, patches.size)
            assertTrue(patches[0].body.contains("\"color\":255"))
            assertTrue(patches[0].query.contains("id=eq.l1"))
            assertTrue(patches[1].body.contains("\"icon\":\"star\""))
        }

    @Test
    fun `changing list colour and icon reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Patch, "/rest/v1/shopping_lists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.changeListColor("l1", null))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            finish(vm.changeListIcon("l1", "star"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `deleteList removes the list and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.deleteList(ShoppingListModel(id = "l1", title = "Weekly")))
            assertTrue(
                backend
                    .requestsTo("shopping_lists", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=eq.l1"),
            )
            backend.onJson(HttpMethod.Delete, "/rest/v1/shopping_lists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.deleteList(ShoppingListModel(id = "l1", title = "Weekly")))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
        }

    @Test
    fun `loadListDetail fetches the list and its items`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadListDetail("l1"))
            assertEquals("l1", vm.selectedList.value?.id)
            assertEquals(listOf("i1", "i2"), vm.items.value.map { it.id })
            // A second call for the same list must not resubscribe or break state.
            finish(vm.loadListDetail("l1"))
            assertEquals(2, vm.items.value.size)
        }

    @Test
    fun `addItem inserts into the list`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.addItem("l1", "Bread"))
            val post = backend.requestsTo("shopping_items", HttpMethod.Post).single()
            assertTrue(post.body.contains("\"item\":\"Bread\""))
            assertTrue(post.body.contains("\"list_id\":\"l1\""))
        }

    @Test
    fun `addItem failure reports a save error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/shopping_items", "{}", HttpStatusCode.InternalServerError)
            finish(vm.addItem("l1", "Bread"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `toggle flips the checked flag`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.toggle(ShoppingItemModel(id = "i2", listId = "l1", item = "Eggs", checked = false)))
            val patch = backend.requestsTo("shopping_items", HttpMethod.Patch).single()
            assertTrue(patch.body.contains("\"checked\":true"))
            backend.onJson(HttpMethod.Patch, "/rest/v1/shopping_items", "{}", HttpStatusCode.InternalServerError)
            finish(vm.toggle(ShoppingItemModel(id = "i2", listId = "l1", item = "Eggs", checked = false)))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `deleteItem offers undo and restoreItem re-inserts it`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val eggs = ShoppingItemModel(id = "i2", listId = "l1", item = "Eggs", checked = false)
            finish(vm.deleteItem(eggs))
            assertEquals(eggs, vm.undoItem.value)
            vm.clearUndo()
            assertNull(vm.undoItem.value)
            finish(vm.restoreItem(eggs))
            val post = backend.requestsTo("shopping_items", HttpMethod.Post).single()
            assertTrue(post.body.contains("\"item\":\"Eggs\""))
            assertTrue(post.body.contains("\"checked\":false"))
        }

    @Test
    fun `item deletion and restore failures are reported`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val eggs = ShoppingItemModel(id = "i2", listId = "l1", item = "Eggs")
            backend.onJson(HttpMethod.Delete, "/rest/v1/shopping_items", "{}", HttpStatusCode.InternalServerError)
            finish(vm.deleteItem(eggs))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
            assertNull(vm.undoItem.value)
            vm.clearError()
            backend.onJson(HttpMethod.Post, "/rest/v1/shopping_items", "{}", HttpStatusCode.InternalServerError)
            finish(vm.restoreItem(eggs))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `renaming items and lists patches the backend`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadListDetail("l1"))
            finish(vm.renameItem(ShoppingItemModel(id = "i2", listId = "l1", item = "Eggs"), "Free-range eggs"))
            finish(vm.renameList("l1", "Groceries"))
            assertTrue(
                backend
                    .requestsTo("shopping_items", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("Free-range eggs"),
            )
            assertTrue(
                backend
                    .requestsTo("shopping_lists", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("Groceries"),
            )
            backend.onJson(HttpMethod.Patch, "/rest/v1/shopping_items", "{}", HttpStatusCode.InternalServerError)
            finish(vm.renameItem(ShoppingItemModel(id = "i2", listId = "l1", item = "Eggs"), "x"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            backend.onJson(HttpMethod.Patch, "/rest/v1/shopping_lists", "{}", HttpStatusCode.InternalServerError)
            finish(vm.renameList("l1", "x"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `clearCompleted deletes only checked items and skips when none are checked`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.clearCompleted("l1"))
            assertTrue(backend.requestsTo("shopping_items", HttpMethod.Delete).isEmpty())
            finish(vm.loadListDetail("l1"))
            finish(vm.clearCompleted("l1"))
            val delete = backend.requestsTo("shopping_items", HttpMethod.Delete).single()
            assertTrue(delete.query.contains("checked=eq.true"))
            assertTrue(delete.query.contains("list_id=eq.l1"))
            backend.onJson(HttpMethod.Delete, "/rest/v1/shopping_items", "{}", HttpStatusCode.InternalServerError)
            finish(vm.loadListDetail("l1"))
            finish(vm.clearCompleted("l1"))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
        }
}
