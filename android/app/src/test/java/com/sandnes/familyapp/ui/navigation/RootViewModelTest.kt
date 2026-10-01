package com.sandnes.familyapp.ui.navigation

import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.util.MainDispatcherRule
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()
    private val userId = MutableStateFlow<String?>("fixture-user")
    private val permissions = MutableStateFlow(true)
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
    private val repo =
        mockk<FamilyRepository>(relaxed = true).also {
            every { it.currentUserId } returns userId
            every { it.permissionsRequested } returns permissions
            every { it.sessionStatusFlow } returns status
        }
    private val authenticated =
        SessionStatus.Authenticated(
            UserSession("fictional-access", "fictional-refresh", expiresIn = 3600, tokenType = "bearer"),
        )

    @Test
    fun `navigation observes auth status and never syncs push for cached identity alone`() =
        runTest {
            val vm = RootViewModel(repo)
            runCurrent()
            assertEquals(AuthGate.Loading, vm.gate.value)
            status.value = SessionStatus.NotAuthenticated()
            runCurrent()
            assertEquals(AuthGate.SignedOut, vm.gate.value)
            coVerify(exactly = 0) { repo.syncPushToken() }
            status.value = authenticated
            runCurrent()
            assertEquals(AuthGate.SignedIn, vm.gate.value)
            coVerify(exactly = 1) { repo.syncPushToken() }
            status.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("Offline fixture")))
            runCurrent()
            assertEquals(AuthGate.SignedIn, vm.gate.value)
            status.value = SessionStatus.NotAuthenticated(isSignOut = true)
            runCurrent()
            assertEquals(AuthGate.SignedOut, vm.gate.value)
        }

    @Test
    fun `profile completion and permission changes retain the auth flow until ready`() =
        runTest {
            userId.value = null
            status.value = authenticated
            permissions.value = false
            val vm = RootViewModel(repo)
            runCurrent()
            assertEquals(AuthGate.SignedOut, vm.gate.value)
            userId.value = "fixture-user"
            runCurrent()
            assertEquals(AuthGate.NeedsPermissions, vm.gate.value)
            permissions.value = true
            runCurrent()
            assertEquals(AuthGate.SignedIn, vm.gate.value)
        }
}
