package com.example.mainactivity.ui.birthday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.BirthdayModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.remote.SupabaseManager
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

class BirthdayViewModel(app: Application) : AndroidViewModel(app) {
    companion object { private var cache: List<BirthdayModel> = emptyList() }

    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _birthdays = MutableStateFlow(cache)
    val birthdays: StateFlow<List<BirthdayModel>> = _birthdays.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
    private var currentUserId: String? = null

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
    fun refresh() = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        load(userId)
    }

    private suspend fun load(userId: String) {
        if (_birthdays.value.isEmpty()) _isLoading.value = true
        runCatching {
            val user = repo.getUser(userId)
            if (user != null) {
                val result = if (user.familyId != null) {
                    db.from("birthdays").select {
                        filter { or { eq("made_by_user_id", userId); eq("family_id", user.familyId) } }
                    }.decodeList<BirthdayModel>()
                        .filter { it.familyId == null || it.familyId == user.familyId }
                } else {
                    db.from("birthdays").select {
                        filter { eq("made_by_user_id", userId) }
                    }.decodeList<BirthdayModel>()
                }
                cache = result
                _birthdays.value = result
                if (user.familyId != null) subscribeToBirthdays(user.familyId, userId)
            }
        }
        _isLoading.value = false
    }

    private suspend fun subscribeToBirthdays(familyId: String, userId: String) {
        realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("birthdays-$familyId")
        realtimeChannel = channel
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "birthdays"
            filter("family_id", FilterOperator.EQ, familyId)
        }
        channel.subscribe()
        viewModelScope.launch { flow.collect { load(userId) } }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        }
    }

    fun add(name: String, date: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val user = repo.getUser(userId) ?: return@launch
        val tempId = "temp-${System.currentTimeMillis()}"
        _birthdays.value = _birthdays.value + BirthdayModel(
            id = tempId, name = name, date = date,
            familyId = user.familyId, madeByUserId = userId
        )
        runCatching {
            db.from("birthdays").insert(buildJsonObject {
                put("name", name)
                put("date", date)
                if (user.familyId != null) put("family_id", user.familyId)
                put("made_by_user_id", userId)
            })
        }
        load(userId)
    }

    fun delete(birthday: BirthdayModel) = viewModelScope.launch {
        _birthdays.value = _birthdays.value.filter { it.id != birthday.id }
        runCatching { db.from("birthdays").delete { filter { eq("id", birthday.id) } } }
        val userId = repo.currentUserId.first() ?: return@launch
        load(userId)
    }
}
