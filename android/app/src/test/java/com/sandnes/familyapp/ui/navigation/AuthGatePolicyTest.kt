package com.sandnes.familyapp.ui.navigation

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthGatePolicyTest {
    private val policy = AuthGatePolicy()
    private val authenticated =
        SessionStatus.Authenticated(
            UserSession("fictional-access", "fictional-refresh", expiresIn = 3600, tokenType = "bearer"),
        )
    private val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("Offline fixture")))

    @Test
    fun `cached identity cannot bypass auth initialization`() {
        assertEquals(AuthGate.Loading, policy.resolve("cached-user", true, SessionStatus.Initializing))
    }

    @Test
    fun `missing or invalid session signs out despite cached identity`() {
        assertEquals(AuthGate.SignedOut, policy.resolve("cached-user", true, SessionStatus.NotAuthenticated()))
    }

    @Test
    fun `valid session waits for app profile before opening navigation`() {
        assertEquals(AuthGate.SignedOut, policy.resolve(null, true, authenticated))
        assertEquals(AuthGate.SignedIn, policy.resolve("verified-user", true, authenticated))
    }

    @Test
    fun `permissions onboarding requires an authenticated session`() {
        assertEquals(AuthGate.NeedsPermissions, policy.resolve("verified-user", false, authenticated))
        assertEquals(AuthGate.SignedOut, policy.resolve("verified-user", false, SessionStatus.NotAuthenticated()))
    }

    @Test
    fun `refresh failure cannot unlock an unverified cached identity`() {
        assertEquals(AuthGate.Loading, policy.resolve("cached-user", true, offline))
    }

    @Test
    fun `temporary refresh failure preserves verified offline access`() {
        assertEquals(AuthGate.SignedIn, policy.resolve("verified-user", true, authenticated))
        assertEquals(AuthGate.SignedIn, policy.resolve("verified-user", true, offline))
        assertEquals(AuthGate.NeedsPermissions, policy.resolve("verified-user", false, offline))
        assertEquals(AuthGate.Loading, policy.resolve("different-user", true, offline))
    }

    @Test
    fun `signout or revocation clears offline eligibility`() {
        policy.resolve("verified-user", true, authenticated)
        assertEquals(AuthGate.SignedOut, policy.resolve("verified-user", true, SessionStatus.NotAuthenticated(isSignOut = true)))
        assertEquals(AuthGate.Loading, policy.resolve("verified-user", true, offline))
    }

    @Test
    fun `reinitialization cannot reuse prior offline eligibility`() {
        policy.resolve("verified-user", true, authenticated)
        policy.resolve("verified-user", true, SessionStatus.Initializing)
        assertEquals(AuthGate.Loading, policy.resolve("verified-user", true, offline))
    }
}
