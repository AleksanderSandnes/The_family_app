package com.example.mainactivity.ui.birthday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.BirthdayEntity
import com.example.mainactivity.data.FamilyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BirthdayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.birthdayDao

    val birthdays: Flow<List<BirthdayEntity>> = repo.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else if (user.familyId == null) dao.birthdaysForUser(user.id)
        else dao.birthdaysFor(user.familyId, user.id)
    }

    fun add(name: String, date: String) = viewModelScope.launch {
        val user = repo.currentUser.first() ?: return@launch
        dao.insert(BirthdayEntity(name = name, date = date, familyId = user.familyId, userId = null, madeByUserId = user.id))
    }

    fun delete(birthday: BirthdayEntity) = viewModelScope.launch { dao.delete(birthday) }
}
