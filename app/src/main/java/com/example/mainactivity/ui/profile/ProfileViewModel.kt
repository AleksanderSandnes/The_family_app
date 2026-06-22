package com.example.mainactivity.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    val user: StateFlow<UserEntity?> =
        repo.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(name: String, email: String, birthday: String, mobile: String) = viewModelScope.launch {
        val current = user.value ?: return@launch
        repo.updateProfile(current.copy(name = name.trim(), email = email.trim(), birthday = birthday.trim(), mobile = mobile.trim()))
    }

    fun signOut(onDone: () -> Unit) = viewModelScope.launch {
        repo.signOut()
        onDone()
    }
}
