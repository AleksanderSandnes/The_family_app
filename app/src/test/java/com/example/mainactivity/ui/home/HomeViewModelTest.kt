package com.example.mainactivity.ui.home

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [HomeViewModel].
 *
 * HomeViewModel observes [FamilyRepository.currentUserId] and reloads user data when the ID
 * changes. It does NOT currently observe [FamilyRepository.familyChanged]; the [refresh] function
 * is the mechanism for forcing a reload.
 *
 * Note: tests deliberately use users with null familyId to avoid triggering
 * the direct SupabaseManager.client call for member count, which cannot be
 * initialised in a plain unit-test environment.
 */
@RunWith(JUnit4::class)
class HomeViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        vm = HomeViewModel(repo)
    }

    // -------------------------------------------------------------------------
    // 1. Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has isLoading true before any coroutine runs`() {
        // The init coroutine is queued but has not run yet (StandardTestDispatcher
        // requires explicit advancement). The StateFlow default is HomeUiState().
        val state = vm.state.value
        assertTrue("Initial state should be loading", state.isLoading)
        assertFalse("Initial state should have no error", state.loadError)
        assertNull("Initial state should have no user", state.user)
        assertNull("Initial state should have no family", state.family)
        assertEquals("Initial memberCount should be 0", 0, state.memberCount)
    }

    // -------------------------------------------------------------------------
    // 2. Null userId -> idle, not loading
    // -------------------------------------------------------------------------

    @Test
    fun `null userId results in non-loading state with no error and no user`() =
        runTest(dispatcherRule.dispatcher) {
            // userId remains null (MutableStateFlow initial value)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse("Should not be loading after null userId", state.isLoading)
            assertFalse("Should have no load error", state.loadError)
            assertNull("User should be null", state.user)
        }

    // -------------------------------------------------------------------------
    // 3. getUser returns null -> loadError
    // -------------------------------------------------------------------------

    @Test
    fun `getUser returning null sets loadError and clears isLoading`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns null

            userId.value = "u1"
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse("Should not be loading", state.isLoading)
            assertTrue("loadError should be set", state.loadError)
            assertNull("User should be null", state.user)
        }

    // -------------------------------------------------------------------------
    // 4. getUser throws -> loadError (via runCatching.onFailure)
    // -------------------------------------------------------------------------

    @Test
    fun `getUser throwing exception sets loadError and clears isLoading`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } throws RuntimeException("network error")

            userId.value = "u1"
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse("Should not be loading", state.isLoading)
            assertTrue("loadError should be set on exception", state.loadError)
            assertNull("User should be null after exception", state.user)
        }

    // -------------------------------------------------------------------------
    // 5. User found with no familyId -> successful state
    // -------------------------------------------------------------------------

    @Test
    fun `user without familyId populates state successfully`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", name = "Alice", familyId = null)
            coEvery { repo.getUser("u1") } returns user

            userId.value = "u1"
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse("Should not be loading", state.isLoading)
            assertFalse("Should have no load error", state.loadError)
            assertEquals("User should be populated", user, state.user)
            assertNull("Family should be null when no familyId", state.family)
            assertEquals("Member count should be 0 when no family", 0, state.memberCount)
        }

    // -------------------------------------------------------------------------
    // 6. refresh() triggers another getUser call
    //    (replaces familyChanged test — HomeViewModel does not observe familyChanged;
    //     refresh() is the mechanism to force a reload)
    // -------------------------------------------------------------------------

    @Test
    fun `refresh triggers a second getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", name = "Alice", familyId = null)
            coEvery { repo.getUser("u1") } returns user

            userId.value = "u1"
            advanceUntilIdle() // first load from init collector

            vm.refresh()
            advanceUntilIdle() // second load from refresh()

            coVerify(exactly = 2) { repo.getUser("u1") }
        }

    // -------------------------------------------------------------------------
    // 7. Turbine: state transitions loading -> populated on successful load
    // -------------------------------------------------------------------------

    @Test
    fun `state transitions from loading to populated via turbine`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "u1", name = "Bob", familyId = null)
            coEvery { repo.getUser("u1") } returns user

            vm.state.test {
                // Drain initial emission(s) before a user is set; the init collector may
                // process the starting null userId before/after turbine subscribes.
                awaitItem()

                userId.value = "u1"
                advanceUntilIdle()

                val loaded = expectMostRecentItem()
                assertFalse("Loaded state should not be loading", loaded.isLoading)
                assertFalse("Loaded state should have no error", loaded.loadError)
                assertEquals("Loaded state should have user", user, loaded.user)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // 8. Sequential userId changes load different users
    // -------------------------------------------------------------------------

    @Test
    fun `changing userId loads the new user`() =
        runTest(dispatcherRule.dispatcher) {
            val user1 = UserModel(id = "u1", name = "Alice", familyId = null)
            val user2 = UserModel(id = "u2", name = "Bob", familyId = null)
            coEvery { repo.getUser("u1") } returns user1
            coEvery { repo.getUser("u2") } returns user2

            userId.value = "u1"
            advanceUntilIdle()
            assertEquals(
                "First user should be Alice",
                "u1",
                vm.state.value.user
                    ?.id,
            )

            userId.value = "u2"
            advanceUntilIdle()
            assertEquals(
                "Second user should be Bob",
                "u2",
                vm.state.value.user
                    ?.id,
            )
        }

    // -------------------------------------------------------------------------
    // 9. Turbine: loadError emitted when getUser returns null
    // -------------------------------------------------------------------------

    @Test
    fun `getUser returning null emits loadError state via turbine`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns null

            vm.state.test {
                awaitItem() // initial state

                userId.value = "u1"
                advanceUntilIdle()

                val errorState = expectMostRecentItem()
                assertTrue("loadError should be true", errorState.loadError)
                assertFalse("Should not be loading", errorState.isLoading)
                assertNull("User should be null", errorState.user)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
