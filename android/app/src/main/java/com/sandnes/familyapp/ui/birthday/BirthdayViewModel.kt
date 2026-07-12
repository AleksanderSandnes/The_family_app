package com.sandnes.familyapp.ui.birthday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.BirthdayModel
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@HiltViewModel
class BirthdayViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        companion object {
            private var cache: List<BirthdayModel> = emptyList()
        }

        private val db get() = SupabaseManager.client.postgrest

        private val _birthdays = MutableStateFlow(cache)
        val birthdays: StateFlow<List<BirthdayModel>> = _birthdays.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        /** One-shot, user-visible error as a string resource id. Cleared via [clearError]. */
        private val _errorRes = MutableStateFlow<Int?>(null)
        val errorRes: StateFlow<Int?> = _errorRes.asStateFlow()

        fun clearError() {
            _errorRes.value = null
        }

        /** The current app user id (public.users.id) — gates creator-only edit affordances. */
        val currentUserId: StateFlow<String?> =
            repo.currentUserId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private var realtimeChannel: RealtimeChannel? = null

        /** Tracks which familyId is currently subscribed so subscription is set up only once per family. */
        private var subscribedFamilyId: String? = null

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    if (userId != null) load(userId) else _birthdays.value = emptyList()
                }
            }
            viewModelScope.launch {
                repo.familyChanged.collect {
                    val userId = repo.currentUserId.first() ?: return@collect
                    load(userId)
                }
            }
        }

        /** Re-fetch from the server. Called on screen resume so data stays fresh
         *  even though the ViewModel is Activity-scoped and init{} only runs once. */
        fun refresh() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                reload(userId)
            }

        /** Fetch birthdays without touching the subscription. Safe to call from anywhere. */
        private suspend fun reload(userId: String) {
            if (_birthdays.value.isEmpty()) _isLoading.value = true
            runCatching {
                val user = repo.getUser(userId)
                if (user != null) {
                    val result =
                        if (user.familyId != null) {
                            db
                                .from("birthdays")
                                .select {
                                    filter {
                                        or {
                                            eq("made_by_user_id", userId)
                                            eq("family_id", user.familyId)
                                        }
                                    }
                                }.decodeList<BirthdayModel>()
                                .filter { it.familyId == null || it.familyId == user.familyId }
                        } else {
                            db
                                .from("birthdays")
                                .select {
                                    filter { eq("made_by_user_id", userId) }
                                }.decodeList<BirthdayModel>()
                        }
                    cache = result
                    _birthdays.value = result
                }
            }
            _isLoading.value = false
        }

        /** Full load: reload data then set up realtime subscription (once per family). */
        private suspend fun load(userId: String) {
            reload(userId)
            val user = repo.getUser(userId) ?: return
            if (user.familyId != null) subscribeToBirthdays(user.familyId, userId)
        }

        private suspend fun subscribeToBirthdays(
            familyId: String,
            userId: String,
        ) {
            // Guard: skip if already subscribed to this family to prevent subscription churn.
            if (subscribedFamilyId == familyId && realtimeChannel != null) return
            realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            val channel = runCatching { SupabaseManager.client.channel("birthdays-$familyId") }.getOrNull() ?: return
            realtimeChannel = channel
            subscribedFamilyId = familyId
            val flow =
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "birthdays"
                    filter("family_id", FilterOperator.EQ, familyId)
                }
            channel.subscribe()
            // Collect events and reload data only — no re-subscription.
            viewModelScope.launch { flow.collect { reload(userId) } }
        }

        override fun onCleared() {
            viewModelScope.launch {
                realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            }
        }

        fun add(
            name: String,
            date: String,
            icon: String,
            color: Int?,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val user = repo.getUser(userId) ?: return@launch
                val tempId = "temp-${java.util.UUID.randomUUID()}"
                _birthdays.value = _birthdays.value +
                    BirthdayModel(
                        id = tempId,
                        name = name,
                        date = date,
                        familyId = user.familyId,
                        madeByUserId = userId,
                        icon = icon,
                        color = color,
                    )
                runCatching {
                    db.from("birthdays").insert(
                        buildJsonObject {
                            put("name", name)
                            put("date", date)
                            if (user.familyId != null) put("family_id", user.familyId)
                            put("made_by_user_id", userId)
                            put("icon", icon)
                            color?.let { put("color", it) }
                        },
                    )
                }.onFailure { _errorRes.value = R.string.couldnt_save }
                reload(userId)
            }

        fun update(
            id: String,
            name: String,
            date: String,
            icon: String,
            color: Int?,
        ) = viewModelScope.launch {
            // Optimistic update
            _birthdays.value =
                _birthdays.value.map {
                    if (it.id == id) it.copy(name = name, date = date, icon = icon, color = color) else it
                }
            runCatching {
                db.from("birthdays").update({
                    set("name", name)
                    set("date", date)
                    set("icon", icon)
                    color?.let { set("color", it) } ?: setToNull("color")
                }) {
                    filter { eq("id", id) }
                }
            }.onFailure { _errorRes.value = R.string.couldnt_save }
            val userId = currentUserId.value ?: repo.currentUserId.first() ?: return@launch
            reload(userId)
        }

        fun delete(birthday: BirthdayModel) =
            viewModelScope.launch {
                _birthdays.value = _birthdays.value.filter { it.id != birthday.id }
                runCatching { db.from("birthdays").delete { filter { eq("id", birthday.id) } } }
                    .onFailure { _errorRes.value = R.string.couldnt_delete }
                val userId = repo.currentUserId.first() ?: return@launch
                reload(userId)
            }
    }
