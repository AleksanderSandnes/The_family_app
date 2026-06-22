package com.example.mainactivity.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.CalendarEventEntity
import com.example.mainactivity.data.FamilyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.calendarDao

    val events: Flow<List<CalendarEventEntity>> = repo.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else {
            val fid = user.familyId
            if (fid == null) dao.eventsForUsers(listOf(user.id))
            else repo.userDao.membersOfFamily(fid).flatMapLatest { members ->
                val ids = (members.map { it.id } + user.id).distinct()
                dao.eventsForUsers(ids)
            }
        }
    }

    fun addEvent(activity: String, date: String, time: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        dao.insert(
            CalendarEventEntity(
                dateFrom = date, dateTo = date,
                timeFrom = time, timeTo = time,
                userId = userId, activity = activity
            )
        )
    }

    fun delete(event: CalendarEventEntity) = viewModelScope.launch { dao.delete(event) }
}
