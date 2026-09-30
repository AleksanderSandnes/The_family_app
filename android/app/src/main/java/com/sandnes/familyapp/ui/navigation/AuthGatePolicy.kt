package com.sandnes.familyapp.ui.navigation

import io.github.jan.supabase.auth.status.SessionStatus

/** Cached app identity alone cannot unlock navigation; offline access requires prior verification. */
internal class AuthGatePolicy {
    private var verifiedUserId: String? = null

    fun resolve(
        userId: String?,
        permissionsDone: Boolean,
        status: SessionStatus,
    ): AuthGate =
        when (status) {
            SessionStatus.Initializing -> {
                verifiedUserId = null
                AuthGate.Loading
            }
            is SessionStatus.NotAuthenticated -> {
                verifiedUserId = null
                AuthGate.SignedOut
            }
            is SessionStatus.Authenticated -> {
                verifiedUserId = userId
                signedInGate(userId, permissionsDone)
            }
            is SessionStatus.RefreshFailure -> {
                if (userId != null && userId == verifiedUserId) {
                    signedInGate(userId, permissionsDone)
                } else {
                    AuthGate.Loading
                }
            }
        }

    private fun signedInGate(
        userId: String?,
        permissionsDone: Boolean,
    ): AuthGate =
        when {
            // Keep the auth flow available so its profile-resolution observer can finish sign-in.
            userId == null -> AuthGate.SignedOut
            !permissionsDone -> AuthGate.NeedsPermissions
            else -> AuthGate.SignedIn
        }
}
