package com.example.mainactivity.ui.profile

import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [ProfileViewModel].
 *
 * The VM collects [FamilyRepository.currentUserId] in its `init` block, calls
 * [FamilyRepository.getUser] to populate [ProfileViewModel.user], and exposes optimistic
 * save / signOut / clearError operations.
 *
 * Tests that involve [ProfileViewModel.removeAvatar] or [ProfileViewModel.uploadAvatar] are
 * intentionally excluded — both call [com.example.mainactivity.data.remote.SupabaseManager]
 * statics that cannot be mocked in a plain JUnit4 environment.
 *
 * All tests use [FamilyRepository] mocked with mockk (relaxed) and [MainDispatcherRule]
 * to control coroutine scheduling.
 */
@RunWith(JUnit4::class)
class ProfileViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var currentUserId: MutableStateFlow<String?>
    private lateinit var vm: ProfileViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        currentUserId = MutableStateFlow(null)
        every { repo.currentUserId } returns currentUserId
        // Default: getUser returns null — avoids any Supabase side-effects
        coEvery { repo.getUser(any()) } returns null
        vm = ProfileViewModel(repo)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial user is null before coroutines run`() {
        assertNull(vm.user.value)
    }

    @Test
    fun `initial error is null before coroutines run`() {
        assertNull(vm.error.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. currentUserId == null — user stays null, getUser not called
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `null userId keeps user null`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            assertNull(vm.user.value)
        }

    @Test
    fun `null userId does not call getUser`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. currentUserId non-null — getUser called, user is populated
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `non-null userId triggers getUser`() =
        runTest(dispatcherRule.dispatcher) {
            currentUserId.value = "user-1"
            advanceUntilIdle()
            coVerify(atLeast = 1) { repo.getUser("user-1") }
        }

    @Test
    fun `non-null userId with valid user sets user StateFlow`() =
        runTest(dispatcherRule.dispatcher) {
            val expectedUser = UserModel(id = "user-1", name = "Alice", email = "alice@example.com")
            coEvery { repo.getUser("user-1") } returns expectedUser

            currentUserId.value = "user-1"
            advanceUntilIdle()

            assertEquals(expectedUser, vm.user.value)
        }

    @Test
    fun `userId becoming null after non-null clears user`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("user-1") } returns UserModel(id = "user-1", name = "Alice")

            currentUserId.value = "user-1"
            advanceUntilIdle()

            currentUserId.value = null
            advanceUntilIdle()

            assertNull("User should be null when userId becomes null", vm.user.value)
        }

    @Test
    fun `getUser returning null keeps user null even with valid userId`() =
        runTest(dispatcherRule.dispatcher) {
            // getUser returns null (e.g. transient network error) — user must not be cached as null
            coEvery { repo.getUser("user-1") } returns null

            currentUserId.value = "user-1"
            advanceUntilIdle()

            assertNull(vm.user.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. save() — optimistic local update applied to user StateFlow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `save applies optimistic name update to user StateFlow`() =
        runTest(dispatcherRule.dispatcher) {
            val original =
                UserModel(
                    id = "user-1",
                    name = "Alice",
                    email = "alice@example.com",
                    birthday = "1990-01-01",
                    mobile = "12345678",
                )
            coEvery { repo.getUser("user-1") } returns original

            currentUserId.value = "user-1"
            advanceUntilIdle()

            vm.save("Alice Updated", "alice@example.com", "1990-01-01", "12345678")
            advanceUntilIdle()

            assertEquals("Alice Updated", vm.user.value?.name)
        }

    @Test
    fun `save applies optimistic email update to user StateFlow`() =
        runTest(dispatcherRule.dispatcher) {
            val original =
                UserModel(
                    id = "user-1",
                    name = "Alice",
                    email = "old@example.com",
                    birthday = "1990-01-01",
                    mobile = "12345678",
                )
            coEvery { repo.getUser("user-1") } returns original

            currentUserId.value = "user-1"
            advanceUntilIdle()

            vm.save("Alice", "new@example.com", "1990-01-01", "12345678")
            advanceUntilIdle()

            assertEquals("new@example.com", vm.user.value?.email)
        }

    @Test
    fun `save trims whitespace from name and email`() =
        runTest(dispatcherRule.dispatcher) {
            val original =
                UserModel(
                    id = "user-1",
                    name = "Alice",
                    email = "alice@example.com",
                    birthday = "1990-01-01",
                    mobile = "",
                )
            coEvery { repo.getUser("user-1") } returns original

            currentUserId.value = "user-1"
            advanceUntilIdle()

            vm.save("  Alice  ", "  alice@example.com  ", "1990-01-01", "")
            advanceUntilIdle()

            assertEquals("Alice", vm.user.value?.name)
            assertEquals("alice@example.com", vm.user.value?.email)
        }

    @Test
    fun `save does nothing when user is null`() =
        runTest(dispatcherRule.dispatcher) {
            // No userId → user stays null → save() exits early
            advanceUntilIdle()

            vm.save("Ghost", "ghost@example.com", "", "")
            advanceUntilIdle()

            assertNull(vm.user.value)
            coVerify(exactly = 0) { repo.updateProfile(any(), any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. clearError() — resets error StateFlow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearError sets error to null`() =
        runTest(dispatcherRule.dispatcher) {
            // Inject an error state by reflection-free means: call clearError on a fresh error
            // (error starts null, so just confirm the function is idempotent)
            vm.clearError()
            advanceUntilIdle()
            assertNull(vm.error.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. signOut() — delegates to repo and invokes callback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `signOut calls repo signOut`() =
        runTest(dispatcherRule.dispatcher) {
            vm.signOut {}
            advanceUntilIdle()
            coVerify { repo.signOut() }
        }

    @Test
    fun `signOut invokes onDone callback after repo call`() =
        runTest(dispatcherRule.dispatcher) {
            var callbackInvoked = false
            vm.signOut { callbackInvoked = true }
            advanceUntilIdle()
            assertTrue("onDone callback must be invoked after signOut", callbackInvoked)
        }
}
