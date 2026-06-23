package com.example.mainactivity.data

import android.content.Context
import com.example.mainactivity.data.remote.DEEP_LINK_HOST
import com.example.mainactivity.data.remote.DEEP_LINK_SCHEME
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FamilyRepository(val session: SessionManager) {

    val currentUserId: Flow<String?> = session.currentUserId
    val themeMode: Flow<ThemeMode> = session.themeMode
    val notificationsEnabled: Flow<Boolean> = session.notificationsEnabled
    val notifyDaysBefore: Flow<Int> = session.notifyDaysBefore
    val sessionStatusFlow: StateFlow<SessionStatus> get() = SupabaseManager.client.auth.sessionStatus

    suspend fun setThemeMode(mode: ThemeMode) = session.setThemeMode(mode)
    suspend fun setNotificationsEnabled(enabled: Boolean) = session.setNotificationsEnabled(enabled)
    suspend fun setNotifyDaysBefore(days: Int) = session.setNotifyDaysBefore(days)

    suspend fun getUser(userId: String): UserModel? = runCatching {
        SupabaseManager.client.postgrest.from("users")
            .select { filter { eq("id", userId) } }
            .decodeList<UserModel>()
            .firstOrNull()
    }.getOrNull()

    suspend fun getFamily(familyId: String): FamilyModel? = runCatching {
        SupabaseManager.client.postgrest.from("families")
            .select { filter { eq("id", familyId) } }
            .decodeList<FamilyModel>()
            .firstOrNull()
    }.getOrNull()

    // ---- Auth ----

    suspend fun register(
        name: String,
        email: String,
        password: String,
        birthday: String,
        mobile: String
    ): Result<Unit> = runCatching {
        val client = SupabaseManager.client
        val emailNorm = email.trim().lowercase()
        // Clear any stale session before signing up to avoid FK constraint from wrong auth_id
        runCatching { client.auth.signOut() }
        client.auth.signUpWith(
            provider = Email,
            redirectUrl = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST"
        ) {
            this.email = emailNorm
            this.password = password
            this.data = buildJsonObject {
                put("full_name", name.trim())
                put("phone", mobile)
                put("birthday", birthday)
                put("avatar_color", palette(name))
            }
        }
        // Profile is created automatically by the on_auth_user_created trigger.
    }

    suspend fun completeSignInAfterConfirmation(): Result<String> = runCatching {
        val client = SupabaseManager.client
        val authId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("No authenticated session")
        val user = client.postgrest.from("users")
            .select { filter { eq("auth_id", authId) } }
            .decodeList<UserModel>()
            .firstOrNull() ?: error("User profile not found")
        session.signIn(user.id)
        user.id
    }

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val client = SupabaseManager.client
        val emailNorm = email.trim().lowercase()
        client.auth.signInWith(Email) {
            this.email = emailNorm
            this.password = password
        }
        val authId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("No auth session after login")
        val user = client.postgrest.from("users")
            .select { filter { eq("auth_id", authId) } }
            .decodeList<UserModel>()
            .firstOrNull() ?: error("User profile not found")
        session.signIn(user.id)
        user.id
    }

    suspend fun signOut() {
        runCatching { SupabaseManager.client.auth.signOut() }
        session.signOut()
    }

    // ---- Family ----

    suspend fun createFamily(name: String, code: String, userId: String): Result<String> =
        runCatching {
            val client = SupabaseManager.client
            val family = client.postgrest.from("families").insert(buildJsonObject {
                put("name", name.trim())
                put("join_code", code)
                put("admin_id", userId)
            }) { select() }.decodeList<FamilyModel>().first()
            client.postgrest.from("users").update({
                set("family_id", family.id)
            }) { filter { eq("id", userId) } }
            syncUserBirthday(userId, family.id)
            family.id
        }

    suspend fun joinFamily(name: String, code: String, userId: String): Result<String> =
        runCatching {
            val client = SupabaseManager.client
            val family = client.postgrest.from("families")
                .select { filter { eq("name", name.trim()) } }
                .decodeList<FamilyModel>()
                .firstOrNull { it.joinCode == code }
                ?: error("Family name or join code is incorrect.")
            client.postgrest.from("users").update({
                set("family_id", family.id)
            }) { filter { eq("id", userId) } }
            syncUserBirthday(userId, family.id)
            family.id
        }

    private suspend fun syncUserBirthday(userId: String, familyId: String) {
        runCatching {
            val user = getUser(userId) ?: return@runCatching
            if (user.birthday.isBlank()) return@runCatching
            val client = SupabaseManager.client
            val exists = client.postgrest.from("birthdays")
                .select { filter { eq("user_id", userId); eq("family_id", familyId) } }
                .decodeList<BirthdayModel>()
                .isNotEmpty()
            if (!exists) {
                client.postgrest.from("birthdays").insert(buildJsonObject {
                    put("name", "${user.name} birthday")
                    put("date", user.birthday)
                    put("family_id", familyId)
                    put("user_id", userId)
                    put("made_by_user_id", userId)
                })
            }
        }
    }

    suspend fun leaveFamily(userId: String) {
        runCatching {
            SupabaseManager.client.postgrest.from("users").update({
                set("family_id", null as String?)
            }) { filter { eq("id", userId) } }
        }
    }

    suspend fun updateProfile(
        userId: String,
        name: String,
        email: String,
        birthday: String,
        mobile: String,
        avatarUrl: String?
    ) {
        runCatching {
            SupabaseManager.client.postgrest.from("users").update({
                set("name", name)
                set("email", email)
                set("birthday", birthday)
                set("mobile", mobile)
                set("avatar_url", avatarUrl)
            }) { filter { eq("id", userId) } }
        }
    }

    companion object {
        @Volatile private var INSTANCE: FamilyRepository? = null

        fun get(context: Context): FamilyRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FamilyRepository(
                    SessionManager(context.applicationContext)
                ).also { INSTANCE = it }
            }

        private val avatarColors = intArrayOf(
            0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(),
            0xFFF59E0B.toInt(), 0xFF8B5CF6.toInt(), 0xFF06B6D4.toInt(),
            0xFFEF4444.toInt(), 0xFF10B981.toInt()
        )

        fun palette(seed: String): Int =
            avatarColors[(seed.hashCode() and 0x7FFFFFFF) % avatarColors.size]
    }
}
