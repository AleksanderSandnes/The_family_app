package com.example.mainactivity.ui.birthday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.BirthdayModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@HiltViewModel
class BirthdayViewModel @Inject constructor(
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

    private var realtimeChannel: RealtimeChannel? = null
    private var currentUserId: String? = null

    /** Tracks which familyId is currently subscribed so subscription is set up only once per family. */
    private var subscribedFamilyId: String? = null

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                currentUserId = userId
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
        val channel = SupabaseManager.client.channel("birthdays-$familyId")
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
    ) =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId) ?: return@launch
            val tempId = "temp-${System.currentTimeMillis()}"
            _birthdays.value = _birthdays.value +
                BirthdayModel(
                    id = tempId,
                    name = name,
                    date = date,
                    familyId = user.familyId,
                    madeByUserId = userId,
                )
            runCatching {
                db.from("birthdays").insert(
                    buildJsonObject {
                        put("name", name)
                        put("date", date)
                        if (user.familyId != null) put("family_id", user.familyId)
                        put("made_by_user_id", userId)
                    },
                )
            }
            reload(userId)
        }

    fun update(
        id: String,
        name: String,
        date: String,
    ) = viewModelScope.launch {
        // Optimistic update
        _birthdays.value = _birthdays.value.map { if (it.id == id) it.copy(name = name, date = date) else it }
        runCatching {
            db.from("birthdays").update({
                set("name", name)
                set("date", date)
            }) {
                filter { eq("id", id) }
            }
        }
        val userId = currentUserId ?: repo.currentUserId.first() ?: return@launch
        reload(userId)
    }

    fun delete(birthday: BirthdayModel) =
        viewModelScope.launch {
            _birthdays.value = _birthdays.value.filter { it.id != birthday.id }
            runCatching { db.from("birthdays").delete { filter { eq("id", birthday.id) } } }
            val userId = repo.currentUserId.first() ?: return@launch
            reload(userId)
        }
}
