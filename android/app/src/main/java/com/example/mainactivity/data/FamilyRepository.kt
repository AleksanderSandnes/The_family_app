package com.example.mainactivity.data

import android.content.Context
import com.example.mainactivity.data.remote.DEEP_LINK_HOST
import com.example.mainactivity.data.remote.DEEP_LINK_SCHEME
import com.example.mainactivity.data.remote.SupabaseManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Editable profile fields for [FamilyRepository.updateProfile], grouped into one parameter. */
data class ProfileUpdate(
    val name: String,
    val email: String,
    val birthday: String,
    val mobile: String,
    val avatarUrl: String?,
)

@Singleton
class FamilyRepository
    @Inject
    constructor(
        val session: SessionManager,
    ) {
        private val _familyChanged = MutableSharedFlow<Unit>()
        val familyChanged: SharedFlow<Unit> = _familyChanged.asSharedFlow()

        // Family invite code captured from a deep link (familyapp://join?code=...).
        // Consumed by FamilyScreen once the user is signed in.
        private val _pendingJoinCode = MutableStateFlow<String?>(null)
        val pendingJoinCode: StateFlow<String?> = _pendingJoinCode.asStateFlow()

        fun setPendingJoinCode(code: String) {
            _pendingJoinCode.value = code
        }

        fun consumePendingJoinCode() {
            _pendingJoinCode.value = null
        }

        /** Bumps the current user's last_active_at — called when the app foregrounds (presence). */
        suspend fun touchLastActive() {
            runCatching {
                val userId = session.currentUserId.first() ?: return
                SupabaseManager.client.postgrest
                    .from("users")
                    .update({
                        set(
                            "last_active_at",
                            java.time.Instant
                                .now()
                                .toString(),
                        )
                    }) {
                        filter { eq("id", userId) }
                    }
            }
        }

        val currentUserId: Flow<String?> = session.currentUserId
        val themeMode: Flow<ThemeMode> = session.themeMode
        val notificationsEnabled: Flow<Boolean> = session.notificationsEnabled
        val notifyDaysBefore: Flow<Int> = session.notifyDaysBefore
        val locationVisible: Flow<Boolean> = session.locationVisible
        val permissionsRequested: Flow<Boolean> = session.permissionsRequested
        val sessionStatusFlow: StateFlow<SessionStatus> get() = SupabaseManager.client.auth.sessionStatus

        suspend fun setThemeMode(mode: ThemeMode) = session.setThemeMode(mode)

        suspend fun setNotificationsEnabled(enabled: Boolean) {
            session.setNotificationsEnabled(enabled)
            updateUserNotificationPrefs(enabled = enabled)
        }

        suspend fun setNotifyDaysBefore(days: Int) {
            session.setNotifyDaysBefore(days)
            updateUserNotificationPrefs(days = days)
        }

        /** Mirrors the client notification settings onto the user's row so the server-side
         *  daily-reminders function can honour them. The DataStore copy stays the UI source
         *  of truth; this row is the server's read-only mirror. */
        private suspend fun updateUserNotificationPrefs(
            enabled: Boolean? = null,
            days: Int? = null,
        ) {
            val userId = currentUserId.first() ?: return
            runCatching {
                SupabaseManager.client.postgrest.from("users").update({
                    enabled?.let { set("notifications_enabled", it) }
                    days?.let { set("notify_days_before", it) }
                }) { filter { eq("id", userId) } }
                invalidateUserCache()
            }
        }

        /** Pushes the current DataStore notification settings to the server once after sign-in. */
        suspend fun syncNotificationPrefsToServer() {
            updateUserNotificationPrefs(
                enabled = session.notificationsEnabled.first(),
                days = session.notifyDaysBefore.first(),
            )
        }

        // ---- Push notification tokens (FCM) ----

        @Volatile private var lastPushToken: String? = null

        /** Fetches the current FCM token and stores it for the signed-in user. Safe to call
         *  repeatedly (upsert) and a no-op when Firebase isn't configured or no user is signed in. */
        suspend fun syncPushToken() {
            val token = runCatching { fetchFcmToken() }.getOrNull() ?: return
            registerPushToken(token)
        }

        /** Upserts a device push token for the current user. Called from [syncPushToken] and
         *  from `FamilyMessagingService.onNewToken`. */
        suspend fun registerPushToken(token: String) {
            val userId = currentUserId.first() ?: return
            lastPushToken = token
            runCatching {
                SupabaseManager.client.postgrest
                    .from("device_push_tokens")
                    .upsert(
                        buildJsonObject {
                            put("user_id", userId)
                            put("token", token)
                            put("platform", "android")
                            put("updated_at", Instant.now().toString())
                        },
                    ) { onConflict = "token" }
            }
        }

        /** Removes this device's push token. Must run while still authenticated (RLS), so it
         *  is called from [signOut] before the auth session is torn down. */
        suspend fun unregisterPushToken() {
            val token = lastPushToken ?: runCatching { fetchFcmToken() }.getOrNull() ?: return
            runCatching {
                SupabaseManager.client.postgrest
                    .from("device_push_tokens")
                    .delete { filter { eq("token", token) } }
            }
            lastPushToken = null
        }

        private suspend fun fetchFcmToken(): String? =
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging
                    .getInstance()
                    .token
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }

        suspend fun setLocationVisible(enabled: Boolean) = session.setLocationVisible(enabled)

        suspend fun setPermissionsRequested() = session.setPermissionsRequested()

        @Volatile private var cachedUser: UserModel? = null

        @Volatile private var cachedUserId: String? = null

        fun invalidateUserCache() {
            cachedUser = null
            cachedUserId = null
        }

        suspend fun getUser(userId: String): UserModel? {
            if (userId == cachedUserId && cachedUser != null) return cachedUser
            val fetched =
                runCatching {
                    SupabaseManager.client.postgrest
                        .from("users")
                        .select { filter { eq("id", userId) } }
                        .decodeList<UserModel>()
                        .firstOrNull()
                }.getOrNull()
            // Only cache a successful, non-null fetch. Caching null (e.g. on a
            // transient network/RLS failure) would poison the cache for the whole
            // session — every feature would then read the user as "no family" and
            // both create rows with family_id=null and only read null-family rows.
            if (fetched != null) {
                cachedUser = fetched
                cachedUserId = userId
            }
            return fetched
        }

        suspend fun getFamilyMembers(familyId: String): List<UserModel> =
            runCatching {
                SupabaseManager.client.postgrest
                    .from("users")
                    .select { filter { eq("family_id", familyId) } }
                    .decodeList<UserModel>()
            }.getOrDefault(emptyList())

        suspend fun getFamily(familyId: String): FamilyModel? =
            runCatching {
                SupabaseManager.client.postgrest
                    .from("families")
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
            mobile: String,
        ): Result<Unit> =
            runCatching {
                val client = SupabaseManager.client
                val emailNorm = email.trim().lowercase()
                // Clear any stale session before signing up to avoid FK constraint from wrong auth_id
                runCatching { client.auth.signOut() }
                client.auth.signUpWith(
                    provider = Email,
                    redirectUrl = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST",
                ) {
                    this.email = emailNorm
                    this.password = password
                    this.data =
                        buildJsonObject {
                            put("full_name", name.trim())
                            put("phone", mobile)
                            put("birthday", birthday)
                            put("avatar_color", palette(name))
                        }
                }
                // Profile is created automatically by the on_auth_user_created trigger.
            }

        suspend fun completeSignInAfterConfirmation(): Result<String> =
            runCatching {
                val client = SupabaseManager.client
                val authId =
                    client.auth
                        .currentSessionOrNull()
                        ?.user
                        ?.id
                        ?: error("No authenticated session")
                val user =
                    client.postgrest
                        .from("users")
                        .select { filter { eq("auth_id", authId) } }
                        .decodeList<UserModel>()
                        .firstOrNull() ?: error("User profile not found")
                session.signIn(user.id)
                user.id
            }

        /** Starts the browser-based Google OAuth flow. Completion arrives via the
         *  familyapp://auth deep link; AuthViewModel finalizes the app session on Authenticated. */
        suspend fun signInWithGoogle(): Result<Unit> =
            runCatching {
                SupabaseManager.client.auth.signInWith(Google)
            }

        suspend fun login(
            email: String,
            password: String,
        ): Result<String> =
            runCatching {
                val client = SupabaseManager.client
                val emailNorm = email.trim().lowercase()
                client.auth.signInWith(Email) {
                    this.email = emailNorm
                    this.password = password
                }
                val authId =
                    client.auth
                        .currentSessionOrNull()
                        ?.user
                        ?.id
                        ?: error("No auth session after login")
                val user =
                    client.postgrest
                        .from("users")
                        .select { filter { eq("auth_id", authId) } }
                        .decodeList<UserModel>()
                        .firstOrNull() ?: error("User profile not found")
                session.signIn(user.id)
                user.id
            }

        suspend fun signOut() {
            // Remove this device's push token while the auth session is still valid (RLS).
            unregisterPushToken()
            runCatching { SupabaseManager.client.auth.signOut() }
            invalidateUserCache()
            session.signOut()
        }

        // ---- Family ----

        suspend fun createFamily(
            name: String,
            code: String,
            userId: String,
        ): Result<String> =
            runCatching {
                val client = SupabaseManager.client
                val family =
                    client.postgrest
                        .from("families")
                        .insert(
                            buildJsonObject {
                                put("name", name.trim())
                                put("join_code", code)
                                put("admin_id", userId)
                            },
                        ) { select() }
                        .decodeList<FamilyModel>()
                        .first()
                client.postgrest.from("users").update({
                    set("family_id", family.id)
                }) { filter { eq("id", userId) } }
                syncUserBirthday(userId, family.id)
                family.id
            }.also {
                if (it.isSuccess) {
                    invalidateUserCache()
                    _familyChanged.emit(Unit)
                }
            }

        suspend fun joinFamily(
            code: String,
            userId: String,
        ): Result<String> =
            runCatching {
                val client = SupabaseManager.client
                val family =
                    client.postgrest
                        .from("families")
                        .select { filter { eq("join_code", code.trim()) } }
                        .decodeList<FamilyModel>()
                        .firstOrNull()
                        ?: error("Join code is incorrect.")
                client.postgrest.from("users").update({
                    set("family_id", family.id)
                }) { filter { eq("id", userId) } }
                syncUserBirthday(userId, family.id)
                family.id
            }.also {
                if (it.isSuccess) {
                    invalidateUserCache()
                    _familyChanged.emit(Unit)
                }
            }

        private suspend fun syncUserBirthday(
            userId: String,
            familyId: String,
        ) {
            runCatching {
                val user = getUser(userId) ?: return@runCatching
                if (user.birthday.isBlank()) return@runCatching
                val client = SupabaseManager.client
                val exists =
                    client.postgrest
                        .from("birthdays")
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("family_id", familyId)
                            }
                        }.decodeList<BirthdayModel>()
                        .isNotEmpty()
                if (!exists) {
                    client.postgrest.from("birthdays").insert(
                        buildJsonObject {
                            put("name", user.name)
                            put("date", user.birthday)
                            put("family_id", familyId)
                            put("user_id", userId)
                            put("made_by_user_id", userId)
                        },
                    )
                }
            }
        }

        suspend fun leaveFamily(userId: String) {
            val user = getUser(userId) ?: return
            val familyId =
                user.familyId ?: run {
                    SupabaseManager.client.postgrest.from("users").update({
                        set("family_id", null as String?)
                    }) { filter { eq("id", userId) } }
                    invalidateUserCache()
                    _familyChanged.emit(Unit)
                    return
                }
            val client = SupabaseManager.client
            runCatching {
                cleanupConversationsForLeavingUser(userId)

                // Delete user's calendar events in this family
                client.postgrest.from("calendar_events").delete {
                    filter {
                        eq("user_id", userId)
                        eq("family_id", familyId)
                    }
                }

                // Delete birthdays created by this user in this family
                client.postgrest.from("birthdays").delete {
                    filter {
                        eq("made_by_user_id", userId)
                        eq("family_id", familyId)
                    }
                }

                // Delete user's shopping lists in this family (items cascade)
                client.postgrest.from("shopping_lists").delete {
                    filter {
                        eq("owner_user_id", userId)
                        eq("family_id", familyId)
                    }
                }

                // Clear family membership
                client.postgrest.from("users").update({
                    set("family_id", null as String?)
                }) { filter { eq("id", userId) } }
            }.onSuccess {
                invalidateUserCache()
                _familyChanged.emit(Unit)
            }
        }

        /** Deletes conversations where the leaving user was the sole participant and
         *  removes their participant rows from any group conversations. */
        private suspend fun cleanupConversationsForLeavingUser(userId: String) {
            val client = SupabaseManager.client
            val myParticipantRows =
                client.postgrest
                    .from("conversation_participants")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<ConversationParticipantModel>()
            val myConversationIds = myParticipantRows.map { it.conversationId }
            if (myConversationIds.isEmpty()) return

            val allRows =
                client.postgrest
                    .from("conversation_participants")
                    .select { filter { isIn("conversation_id", myConversationIds) } }
                    .decodeList<ConversationParticipantModel>()
            val soloIds =
                allRows
                    .groupBy { it.conversationId }
                    .filter { (_, rows) -> rows.size == 1 }
                    .keys
                    .toList()
            if (soloIds.isNotEmpty()) {
                client.postgrest.from("conversations").delete { filter { isIn("id", soloIds) } }
            }
            client.postgrest.from("conversation_participants").delete { filter { eq("user_id", userId) } }
        }

        suspend fun updateProfile(
            userId: String,
            update: ProfileUpdate,
        ) {
            runCatching {
                SupabaseManager.client.postgrest.from("users").update({
                    set("name", update.name)
                    set("email", update.email)
                    set("birthday", update.birthday)
                    set("mobile", update.mobile)
                    set("avatar_url", update.avatarUrl)
                }) { filter { eq("id", userId) } }
                invalidateUserCache()
            }
        }

        suspend fun removeFamilyMember(memberId: String): Result<Unit> =
            runCatching {
                SupabaseManager.client.postgrest.from("users").update({
                    set("family_id", null as String?)
                }) { filter { eq("id", memberId) } }
                invalidateUserCache()
                _familyChanged.emit(Unit)
            }

        suspend fun renameFamily(
            familyId: String,
            newName: String,
        ): Result<Unit> =
            runCatching {
                SupabaseManager.client.postgrest.from("families").update({
                    set("name", newName.trim())
                }) { filter { eq("id", familyId) } }
            }

        suspend fun updateFamilyPhoto(
            familyId: String,
            photoUrl: String,
        ): Result<Unit> =
            runCatching {
                SupabaseManager.client.postgrest.from("families").update({
                    set("photo_url", photoUrl)
                }) { filter { eq("id", familyId) } }
                Unit
            }.also {
                if (it.isSuccess) _familyChanged.emit(Unit)
            }

        // ---- Chat ----

        suspend fun getLastMessage(conversationId: String): MessageModel? =
            runCatching {
                SupabaseManager.client.postgrest
                    .from("messages")
                    .select {
                        filter { eq("conversation_id", conversationId) }
                        order("sent_at", Order.DESCENDING)
                    }.decodeList<MessageModel>()
                    .firstOrNull()
            }.getOrNull()

        suspend fun markConversationRead(conversationId: String) {
            val userId = currentUserId.first() ?: return
            runCatching {
                SupabaseManager.client.postgrest
                    .from("conversation_participants")
                    .update({ set("last_read_at", Instant.now().toString()) }) {
                        filter {
                            eq("conversation_id", conversationId)
                            eq("user_id", userId)
                        }
                    }
            }
        }

        suspend fun sendMessage(
            conversationId: String,
            text: String,
        ): Result<Unit> =
            runCatching {
                val userId = currentUserId.first() ?: error("Not signed in")
                SupabaseManager.client.postgrest
                    .from("messages")
                    .insert(
                        buildJsonObject {
                            put("conversation_id", conversationId)
                            put("user_from", userId)
                            put("text", text)
                            put("message_type", "text")
                        },
                    )
            }

        suspend fun addReaction(
            messageId: String,
            conversationId: String,
            emoji: String,
        ): Result<Unit> =
            runCatching {
                val userId = currentUserId.first() ?: error("Not signed in")
                SupabaseManager.client.postgrest
                    .from("message_reactions")
                    .upsert(
                        buildJsonObject {
                            put("message_id", messageId)
                            put("conversation_id", conversationId)
                            put("user_id", userId)
                            put("emoji", emoji)
                        },
                    )
            }

        suspend fun removeReaction(messageId: String): Result<Unit> =
            runCatching {
                val userId = currentUserId.first() ?: error("Not signed in")
                SupabaseManager.client.postgrest
                    .from("message_reactions")
                    .delete {
                        filter {
                            eq("message_id", messageId)
                            eq("user_id", userId)
                        }
                    }
            }

        suspend fun uploadChatMedia(
            conversationId: String,
            bytes: ByteArray,
            filename: String,
        ): String {
            val authUid =
                SupabaseManager.client.auth
                    .currentSessionOrNull()
                    ?.user
                    ?.id
                    ?: error("Not authenticated")
            val path = "$conversationId/$authUid/$filename"
            val bucket = SupabaseManager.client.storage.from("chat-media")
            bucket.upload(path, bytes) { upsert = true }
            return bucket.publicUrl(path)
        }

        companion object {
            @EntryPoint
            @InstallIn(SingletonComponent::class)
            interface FamilyRepositoryEntryPoint {
                fun familyRepository(): FamilyRepository
            }

            fun get(context: Context): FamilyRepository =
                EntryPointAccessors
                    .fromApplication(
                        context.applicationContext,
                        FamilyRepositoryEntryPoint::class.java,
                    ).familyRepository()

            private val avatarColors =
                intArrayOf(
                    0xFF6366F1.toInt(),
                    0xFFEC4899.toInt(),
                    0xFF14B8A6.toInt(),
                    0xFFF59E0B.toInt(),
                    0xFF8B5CF6.toInt(),
                    0xFF06B6D4.toInt(),
                    0xFFEF4444.toInt(),
                    0xFF10B981.toInt(),
                )

            fun palette(seed: String): Int = avatarColors[(seed.hashCode() and Int.MAX_VALUE) % avatarColors.size]
        }
    }
