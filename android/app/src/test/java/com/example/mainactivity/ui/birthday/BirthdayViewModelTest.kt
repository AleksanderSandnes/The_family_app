package com.example.mainactivity.ui.birthday

import app.cash.turbine.test
import com.example.mainactivity.data.FamilyRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [BirthdayViewModel].
 *
 * The ViewModel calls SupabaseManager.client directly for all DB and Realtime operations.
 * Those calls are wrapped in runCatching inside reload() so they fail silently in a
 * plain unit-test environment (no real Supabase connection). The tests therefore focus on:
 *   - Initial StateFlow values before any coroutine runs
 *   - Optimistic mutations (add / update / delete) applied to the birthdays StateFlow
 *   - Lifecycle triggers (userId flow, familyChanged flow, refresh()) that initiate reload
 *
 * Tests deliberately use UserModel(familyId = null) to prevent subscribeToBirthdays()
 * from being reached; that function contains SupabaseManager.client.channel() and
 * channel.subscribe() calls that are NOT wrapped in runCatching.
 */
@RunWith(JUnit4::class)
class BirthdayViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: BirthdayViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        // Default: getUser returns null so the reload() DB branch is skipped entirely
        coEvery { repo.getUser(any()) } returns null
        vm = BirthdayViewModel(repo)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial state — before any coroutine is advanced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial birthdays list is empty before coroutines run`() {
        assertTrue("birthdays must start empty", vm.birthdays.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false before coroutines run`() {
        assertFalse("isLoading must start false", vm.isLoading.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Null userId — idle state, no load attempted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `null userId results in empty birthdays and no loading`() =
        runTest(dispatcherRule.dispatcher) {
            // userId remains null (MutableStateFlow initial value)
            advanceUntilIdle()

            assertTrue("birthdays should be empty when userId is null", vm.birthdays.value.isEmpty())
            assertFalse("isLoading should be false after null userId", vm.isLoading.value)
        }

    @Test
    fun `null userId does not trigger getUser`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Non-null userId — load is triggered
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `non-null userId triggers at least one getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            // getUser returns null by default, so reload() exits after getUser without DB calls
            userId.value = "user-1"
            advanceUntilIdle()

            coVerify(atLeast = 1) { repo.getUser("user-1") }
        }

    @Test
    fun `userId becoming null after non-null clears birthdays`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            userId.value = null
            advanceUntilIdle()

            assertTrue("birthdays should be cleared when userId becomes null", vm.birthdays.value.isEmpty())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. add() — optimistic item appears in the list
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `add creates optimistic item with correct name and date`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("Alice", "1990-05-15")
            advanceUntilIdle()

            val items = vm.birthdays.value
            assertEquals("Should have exactly one birthday", 1, items.size)
            assertEquals("Name should match", "Alice", items[0].name)
            assertEquals("Date should match", "1990-05-15", items[0].date)
        }

    @Test
    fun `add sets madeByUserId to the current userId`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("Bob", "1985-12-01")
            advanceUntilIdle()

            assertEquals(
                "madeByUserId should be the current user id",
                "user-1",
                vm.birthdays.value[0].madeByUserId,
            )
        }

    @Test
    fun `add when getUser returns null does not modify list`() =
        runTest(dispatcherRule.dispatcher) {
            // Default setUp: getUser returns null → add() hits early return guard
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("Ghost", "2000-01-01")
            advanceUntilIdle()

            assertTrue("List must stay empty when getUser returns null", vm.birthdays.value.isEmpty())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. update() — optimistic change reflected in the list
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update applies optimistic name and date change`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("Original", "1990-01-01")
            advanceUntilIdle()

            val item = vm.birthdays.value.first()
            vm.update(item.id, "Updated", "2000-06-15")
            advanceUntilIdle()

            val updated = vm.birthdays.value.first()
            assertEquals("Name should be updated", "Updated", updated.name)
            assertEquals("Date should be updated", "2000-06-15", updated.date)
        }

    @Test
    fun `update does not modify non-matching items`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("Alice", "1990-01-01")
            advanceUntilIdle()
            vm.add("Bob", "1985-06-15")
            advanceUntilIdle()

            val aliceId =
                vm.birthdays.value
                    .first { it.name == "Alice" }
                    .id
            vm.update(aliceId, "Alice-Renamed", "1990-01-02")
            advanceUntilIdle()

            val bob = vm.birthdays.value.first { it.name == "Bob" }
            assertEquals("Bob's date should be unchanged", "1985-06-15", bob.date)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. delete() — item removed optimistically
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes the targeted birthday from list`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("ToDelete", "1980-03-10")
            advanceUntilIdle()

            val item = vm.birthdays.value.first()
            vm.delete(item)
            advanceUntilIdle()

            assertTrue("Birthday list should be empty after delete", vm.birthdays.value.isEmpty())
        }

    @Test
    fun `delete only removes the targeted item leaving others intact`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.add("Alice", "1990-01-01")
            advanceUntilIdle()
            vm.add("Bob", "1985-06-15")
            advanceUntilIdle()

            val alice = vm.birthdays.value.first { it.name == "Alice" }
            vm.delete(alice)
            advanceUntilIdle()

            assertEquals("Only one birthday should remain", 1, vm.birthdays.value.size)
            assertEquals("Remaining birthday should be Bob", "Bob", vm.birthdays.value[0].name)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. familyChanged — triggers a reload (getUser called again)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `familyChanged emission triggers an additional getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()
            // At least 1 getUser call happened during the initial load

            familyChanged.emit(Unit)
            advanceUntilIdle()

            // familyChanged handler calls load() → reload() → getUser() (at least once more)
            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. refresh() — forces a reload outside of the Flow collectors
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `refresh triggers another getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            vm.refresh()
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Turbine — birthdays Flow emits expected sequence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `birthdays flow emits empty then populated after add via turbine`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user

            vm.birthdays.test {
                val initial = awaitItem()
                assertTrue("Initial emission should be empty", initial.isEmpty())

                userId.value = "user-1"
                // Queue add alongside init so both run in one advanceUntilIdle()
                vm.add("Alice", "1990-05-15")
                advanceUntilIdle()

                // The optimistic add emits a new list; reload() fails silently so the
                // item persists as the most recent emitted value.
                val populated = expectMostRecentItem()
                assertTrue("Flow should contain Alice after add", populated.any { it.name == "Alice" })

                cancelAndIgnoreRemainingEvents()
            }
        }
}
