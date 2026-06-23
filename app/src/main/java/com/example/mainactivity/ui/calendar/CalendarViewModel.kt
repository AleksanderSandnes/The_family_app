package com.example.mainactivity.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.CalendarEventModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEventModel>>(emptyList())
    val events: StateFlow<List<CalendarEventModel>> = _events.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
    private var currentUserId: String? = null

    val eventsForSelectedDate: StateFlow<List<CalendarEventModel>> = combine(
        _selectedDate, _events
    ) { date, all ->
        all.filter { event ->
            val from = runCatching { LocalDate.parse(event.dateFrom) }.getOrNull() ?: return@filter false
            val to = runCatching { LocalDate.parse(event.dateTo) }.getOrElse { from }
            !date.isBefore(from) && !date.isAfter(to)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                currentUserId = userId
                if (userId != null) loadEvents(userId) else _events.value = emptyList()
            }
        }
        viewModelScope.launch {
            repo.familyChanged.collect {
                val userId = repo.currentUserId.first() ?: return@collect
                loadEvents(userId)
            }
        }
    }

    private suspend fun loadEvents(userId: String) {
        _isLoading.value = true
        runCatching {
            val user = repo.getUser(userId)
            if (user != null) {
                _events.value = if (user.familyId != null) {
                    db.from("calendar_events").select {
                        filter { or { eq("user_id", userId); eq("family_id", user.familyId) } }
                    }.decodeList<CalendarEventModel>()
                        .filter { it.familyId == null || it.familyId == user.familyId }
                } else {
                    db.from("calendar_events").select {
                        filter { eq("user_id", userId) }
                    }.decodeList<CalendarEventModel>()
                        .filter { it.familyId == null }
                }
                if (user.familyId != null) subscribeToEvents(user.familyId, userId)
            }
        }
        _isLoading.value = false
    }

    private suspend fun subscribeToEvents(familyId: String, userId: String) {
        realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("calendar-$familyId")
        realtimeChannel = channel
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "calendar_events"
            filter("family_id", FilterOperator.EQ, familyId)
        }
        channel.subscribe()
        viewModelScope.launch { flow.collect { loadEvents(userId) } }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        }
    }

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
        timeTo: String,
        icon: String = "schedule"
    ) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val user = repo.getUser(userId) ?: return@launch
        val resolvedDateTo = if (dateTo.isBlank()) dateFrom else dateTo
        runCatching {
            db.from("calendar_events").insert(buildJsonObject {
                put("user_id", userId)
                if (user.familyId != null) put("family_id", user.familyId)
                put("activity", activity)
                put("all_day", allDay)
                put("date_from", dateFrom)
                put("date_to", resolvedDateTo)
                put("time_from", if (allDay) "" else timeFrom)
                put("time_to", if (allDay) "" else timeTo)
                put("icon", icon)
            })
        }
        loadEvents(userId)
    }

    fun updateEvent(event: CalendarEventModel) = viewModelScope.launch {
        runCatching {
            db.from("calendar_events").update({
                set("activity", event.activity)
                set("all_day", event.allDay)
                set("date_from", event.dateFrom)
                set("date_to", event.dateTo)
                set("time_from", event.timeFrom)
                set("time_to", event.timeTo)
                set("icon", event.icon)
            }) { filter { eq("id", event.id) } }
        }
        val userId = repo.currentUserId.first() ?: return@launch
        loadEvents(userId)
    }

    fun delete(event: CalendarEventModel) = viewModelScope.launch {
        runCatching { db.from("calendar_events").delete { filter { eq("id", event.id) } } }
        val userId = repo.currentUserId.first() ?: return@launch
        loadEvents(userId)
    }
}
