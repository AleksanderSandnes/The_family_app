package com.example.mainactivity.ui.birthday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.BirthdayModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BirthdayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _birthdays = MutableStateFlow<List<BirthdayModel>>(emptyList())
    val birthdays: StateFlow<List<BirthdayModel>> = _birthdays.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    private suspend fun load(userId: String) {
        _isLoading.value = true
        runCatching {
            val user = repo.getUser(userId)
            if (user != null) {
                _birthdays.value = if (user.familyId != null) {
                    db.from("birthdays").select {
                        filter { or { eq("made_by_user_id", userId); eq("family_id", user.familyId) } }
                    }.decodeList<BirthdayModel>()
                        .filter { it.familyId == null || it.familyId == user.familyId }
                } else {
                    db.from("birthdays").select {
                        filter { eq("made_by_user_id", userId) }
                    }.decodeList<BirthdayModel>()
                }
            }
        }
        _isLoading.value = false
    }

    fun add(name: String, date: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val user = repo.getUser(userId) ?: return@launch
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
        runCatching { db.from("birthdays").delete { filter { eq("id", birthday.id) } } }
        val userId = repo.currentUserId.first() ?: return@launch
        load(userId)
    }
}
