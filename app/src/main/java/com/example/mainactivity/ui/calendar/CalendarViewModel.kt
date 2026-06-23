package com.example.mainactivity.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.CalendarEventEntity
import com.example.mainactivity.data.FamilyRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.calendarDao

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

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

    val eventsForSelectedDate: StateFlow<List<CalendarEventEntity>> = combine(
        _selectedDate,
        events
    ) { date, all ->
        all.filter { event ->
            val from = runCatching { LocalDate.parse(event.dateFrom) }.getOrNull()
                ?: return@filter false
            val to = runCatching { LocalDate.parse(event.dateTo) }.getOrElse { from }
            !date.isBefore(from) && !date.isAfter(to)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _displayedMonth.value = YearMonth.of(date.year, date.month)
    }

    fun nextMonth() { _displayedMonth.value = _displayedMonth.value.plusMonths(1) }
    fun prevMonth() { _displayedMonth.value = _displayedMonth.value.minusMonths(1) }

    fun addEvent(
        activity: String,
        allDay: Boolean,
        dateFrom: String,
        dateTo: String,
        timeFrom: String,
        timeTo: String
    ) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val resolvedDateTo = if (dateTo.isBlank()) dateFrom else dateTo
        dao.insert(
            CalendarEventEntity(
                activity = activity,
                allDay = allDay,
                dateFrom = dateFrom,
                dateTo = resolvedDateTo,
                timeFrom = if (allDay) "" else timeFrom,
                timeTo = if (allDay) "" else timeTo,
                userId = userId
            )
        )
    }

    fun delete(event: CalendarEventEntity) = viewModelScope.launch { dao.delete(event) }
}
