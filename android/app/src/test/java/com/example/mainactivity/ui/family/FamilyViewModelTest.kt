package com.example.mainactivity.ui.family

import com.example.mainactivity.data.FamilyModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [FamilyViewModel].
 *
 * Strategy: FamilyViewModel's [load] function calls [SupabaseManager.client] directly
 * for fetching family members when [UserModel.familyId] is non-null. To avoid that
 * path, most tests use a mock [FamilyRepository.getUser] that returns a user with a
 * null familyId. Tests that need [family] populated (e.g. renameFamily) let load()
 * run with a non-null familyId; the subsequent direct Supabase call fails silently
 * inside runCatching, but [_family] is already set before that line executes.
 *
 * Note: [FamilyViewModel] observes only [FamilyRepository.currentUserId] in its init
 * block — it does NOT observe [FamilyRepository.familyChanged]. The familyChanged stub
 * in setUp is kept for parity with sibling test files.
 */
@RunWith(JUnit4::class)
class FamilyViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: FamilyViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        // Return a user with no familyId to avoid the SupabaseManager.client direct
        // path for members in load(), which cannot be reached in a plain unit-test env.
        coEvery { repo.getUser(any()) } returns UserModel(id = "user1", familyId = null)
        vm = FamilyViewModel(repo)
    }

    // ──────────────────────────────────────────────────────────────
    // 1. Initial state
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `members are empty before userId emits`() =
        runTest(dispatcherRule.dispatcher) {
            assertTrue("members should be empty initially", vm.members.value.isEmpty())
        }

    @Test
    fun `family is null before userId emits`() =
        runTest(dispatcherRule.dispatcher) {
            assertNull("family should be null initially", vm.family.value)
        }

    @Test
    fun `error is null initially`() =
        runTest(dispatcherRule.dispatcher) {
            assertNull("error should be null initially", vm.error.value)
        }

    @Test
    fun `currentUser is null before userId emits`() =
        runTest(dispatcherRule.dispatcher) {
            assertNull("currentUser should be null initially", vm.currentUser.value)
        }

    // ──────────────────────────────────────────────────────────────
    // 2. userId emit triggers load
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `emitting userId triggers getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coVerify(atLeast = 1) { repo.getUser("user1") }
        }

    @Test
    fun `emitting userId with null familyId sets currentUser and clears family and members`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            assertNotNull("currentUser should be set after userId emits", vm.currentUser.value)
            assertNull("family should remain null when user has no familyId", vm.family.value)
            assertTrue("members should be empty when user has no familyId", vm.members.value.isEmpty())
        }

    @Test
    fun `null userId clears family members and currentUser`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            userId.value = null
            advanceUntilIdle()

            assertNull("family should be null after sign-out", vm.family.value)
            assertTrue("members should be empty after sign-out", vm.members.value.isEmpty())
            assertNull("currentUser should be null after sign-out", vm.currentUser.value)
        }

    // ──────────────────────────────────────────────────────────────
    // 3. createFamily
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `createFamily calls repo with correct arguments`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.createFamily("MyFamily", "CODE1234", "user1") } returns Result.success("fam-id")

            vm.createFamily("MyFamily", "CODE1234")
            advanceUntilIdle()

            coVerify { repo.createFamily("MyFamily", "CODE1234", "user1") }
        }

    @Test
    fun `createFamily success triggers reload — getUser called at least twice`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.createFamily(any(), any(), any()) } returns Result.success("fam-id")

            vm.createFamily("MyFamily", "CODE1234")
            advanceUntilIdle()

            // Once from the init collector's load(), once from onSuccess { load(userId) }
            coVerify(atLeast = 2) { repo.getUser("user1") }
        }

    @Test
    fun `createFamily failure sets error state`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.createFamily(any(), any(), any()) } returns
                Result.failure(RuntimeException("Family already exists"))

            vm.createFamily("MyFamily", "CODE1234")
            advanceUntilIdle()

            assertEquals("Family already exists", vm.error.value)
        }

    @Test
    fun `createFamily with null userId is a no-op`() =
        runTest(dispatcherRule.dispatcher) {
            // userId is null — createFamily should early-return via currentUserId.first()
            vm.createFamily("MyFamily", "CODE1234")
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.createFamily(any(), any(), any()) }
        }

    // ──────────────────────────────────────────────────────────────
    // 4. joinFamily
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `joinFamily calls repo with correct arguments`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.joinFamily("CODE1234", "user1") } returns Result.success("fam-id")

            vm.joinFamily("CODE1234")
            advanceUntilIdle()

            coVerify { repo.joinFamily("CODE1234", "user1") }
        }

    @Test
    fun `joinFamily success triggers reload — getUser called at least twice`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.joinFamily(any(), any()) } returns Result.success("fam-id")

            vm.joinFamily("CODE1234")
            advanceUntilIdle()

            // Once from the init collector's load(), once from onSuccess { load(userId) }
            coVerify(atLeast = 2) { repo.getUser("user1") }
        }

    @Test
    fun `joinFamily failure sets error state`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.joinFamily(any(), any()) } returns
                Result.failure(RuntimeException("Invalid code"))

            vm.joinFamily("WRONGCODE")
            advanceUntilIdle()

            assertEquals("Invalid code", vm.error.value)
        }

    @Test
    fun `joinFamily with null userId is a no-op`() =
        runTest(dispatcherRule.dispatcher) {
            vm.joinFamily("CODE1234")
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.joinFamily(any(), any()) }
        }

    // ──────────────────────────────────────────────────────────────
    // 5. leaveFamily
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `leaveFamily calls repo leaveFamily with the current userId`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            vm.leaveFamily()
            advanceUntilIdle()

            coVerify { repo.leaveFamily("user1") }
        }

    @Test
    fun `leaveFamily with null userId is a no-op`() =
        runTest(dispatcherRule.dispatcher) {
            vm.leaveFamily()
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.leaveFamily(any()) }
        }

    // ──────────────────────────────────────────────────────────────
    // 6. renameFamily
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `renameFamily when family is null is a no-op`() =
        runTest(dispatcherRule.dispatcher) {
            // _family.value is null (userId not emitted, no family loaded)
            vm.renameFamily("NewName")
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.renameFamily(any(), any()) }
        }

    @Test
    fun `renameFamily calls repo with family id and new name`() =
        runTest(dispatcherRule.dispatcher) {
            // Use a user with familyId to populate _family via load().
            // _family.value = repo.getFamily(...) is set BEFORE the direct Supabase members
            // call, so _family is populated even when that Supabase call fails silently.
            coEvery { repo.getUser("user1") } returns UserModel(id = "user1", familyId = "fam-id")
            coEvery { repo.getFamily("fam-id") } returns FamilyModel(id = "fam-id", name = "OldName")
            coEvery { repo.renameFamily("fam-id", "NewName") } returns Result.success(Unit)

            userId.value = "user1"
            advanceUntilIdle()

            vm.renameFamily("NewName")
            advanceUntilIdle()

            coVerify { repo.renameFamily("fam-id", "NewName") }
        }

    @Test
    fun `renameFamily failure sets error state`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("user1") } returns UserModel(id = "user1", familyId = "fam-id")
            coEvery { repo.getFamily("fam-id") } returns FamilyModel(id = "fam-id", name = "OldName")
            coEvery { repo.renameFamily(any(), any()) } returns
                Result.failure(RuntimeException("Rename failed"))

            userId.value = "user1"
            advanceUntilIdle()

            vm.renameFamily("NewName")
            advanceUntilIdle()

            assertEquals("Rename failed", vm.error.value)
        }

    // ──────────────────────────────────────────────────────────────
    // 7. generateJoinCode
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `generateJoinCode returns an 8-character string`() {
        val code = vm.generateJoinCode()
        assertEquals("join code should be 8 characters", 8, code.length)
    }

    @Test
    fun `generateJoinCode returns an uppercase string`() {
        val code = vm.generateJoinCode()
        assertEquals("join code should be uppercase", code, code.uppercase())
    }

    @Test
    fun `generateJoinCode returns different values on repeated calls`() {
        val codes = (1..5).map { vm.generateJoinCode() }.toSet()
        assertTrue("join codes should be unique across calls", codes.size > 1)
    }

    // ──────────────────────────────────────────────────────────────
    // 8. clearError
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `clearError resets error to null after a failure`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            coEvery { repo.createFamily(any(), any(), any()) } returns
                Result.failure(RuntimeException("Something went wrong"))

            vm.createFamily("Name", "CODE")
            advanceUntilIdle()
            assertNotNull("error should be set after failure", vm.error.value)

            vm.clearError()
            assertNull("error should be null after clearError", vm.error.value)
        }

    // ──────────────────────────────────────────────────────────────
    // 9. refresh
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `refresh triggers a second getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user1"
            advanceUntilIdle()

            vm.refresh()
            advanceUntilIdle()

            coVerify(exactly = 2) { repo.getUser("user1") }
        }
}
