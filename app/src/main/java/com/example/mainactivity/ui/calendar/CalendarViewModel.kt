package com.example.mainactivity.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.CalendarEventModel
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The fields for a new calendar event, grouped into a single parameter. */
data class EventDraft(
    val activity: String,
    val allDay: Boolean,
    val dateFrom: String,
    val dateTo: String,
    val timeFrom: String,
    val timeTo: String,
    val icon: String = "schedule",
)

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        companion object {
            private var cache: List<CalendarEventModel> = emptyList()
        }

        private val db get() = SupabaseManager.client.postgrest

        private val _selectedDate = MutableStateFlow(LocalDate.now())
        val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

        private val _displayedMonth = MutableStateFlow(YearMonth.now())
        val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

        private val _events = MutableStateFlow(cache)
        val events: StateFlow<List<CalendarEventModel>> = _events.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private var realtimeChannel: RealtimeChannel? = null
        private var currentUserId: String? = null

        val eventsForSelectedDate: StateFlow<List<CalendarEventModel>> =
            combine(
                _selectedDate,
                _events,
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

        /** Re-fetch from the server. Called on screen resume so data stays fresh
         *  even though the ViewModel is Activity-scoped and init{} only runs once. */
        fun refresh() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                loadEvents(userId)
            }

        private suspend fun loadEvents(userId: String) {
            if (_events.value.isEmpty()) _isLoading.value = true
            runCatching {
                val user = repo.getUser(userId)
                if (user != null) {
                    val result =
                        if (user.familyId != null) {
                            db
                                .from("calendar_events")
                                .select {
                                    filter {
                                        or {
                                            eq("user_id", userId)
                                            eq("family_id", user.familyId)
                                        }
                                    }
                                }.decodeList<CalendarEventModel>()
                                .filter { it.familyId == null || it.familyId == user.familyId }
                        } else {
                            db
                                .from("calendar_events")
                                .select {
                                    filter { eq("user_id", userId) }
                                }.decodeList<CalendarEventModel>()
                                .filter { it.familyId == null }
                        }
                    cache = result
                    _events.value = result
                    if (user.familyId != null) subscribeToEvents(user.familyId, userId)
                }
            }
            _isLoading.value = false
        }

        private suspend fun subscribeToEvents(
            familyId: String,
            userId: String,
        ) {
            realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            val channel = runCatching { SupabaseManager.client.channel("calendar-$familyId") }.getOrNull() ?: return
            realtimeChannel = channel
            val flow =
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "calendar_events"
                    filter("family_id", FilterOperator.EQ, familyId)
                }
            channel.subscribe()
            viewModelScope.launch { flow.collect { loadEvents(userId) } }
        }

        override fun onCleared() {
            viewModelScope.launch {
                realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            }
        }

        fun selectDate(date: LocalDate) {
            _selectedDate.value = date
            _displayedMonth.value = YearMonth.of(date.year, date.month)
        }

        fun nextMonth() {
            _displayedMonth.value = _displayedMonth.value.plusMonths(1)
        }

        fun prevMonth() {
            _displayedMonth.value = _displayedMonth.value.minusMonths(1)
        }

        fun addEvent(draft: EventDraft) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val user = repo.getUser(userId) ?: return@launch
                val resolvedDateTo = if (draft.dateTo.isBlank()) draft.dateFrom else draft.dateTo
                val tempId = "temp-${java.util.UUID.randomUUID()}"
                _events.value = _events.value +
                    CalendarEventModel(
                        id = tempId,
                        userId = userId,
                        familyId = user.familyId,
                        activity = draft.activity,
                        allDay = draft.allDay,
                        dateFrom = draft.dateFrom,
                        dateTo = resolvedDateTo,
                        timeFrom = if (draft.allDay) "" else draft.timeFrom,
                        timeTo = if (draft.allDay) "" else draft.timeTo,
                        icon = draft.icon,
                    )
                runCatching {
                    db.from("calendar_events").insert(
                        buildJsonObject {
                            put("user_id", userId)
                            if (user.familyId != null) put("family_id", user.familyId)
                            put("activity", draft.activity)
                            put("all_day", draft.allDay)
                            put("date_from", draft.dateFrom)
                            put("date_to", resolvedDateTo)
                            put("time_from", if (draft.allDay) "" else draft.timeFrom)
                            put("time_to", if (draft.allDay) "" else draft.timeTo)
                            put("icon", draft.icon)
                        },
                    )
                }
                loadEvents(userId)
            }

        fun updateEvent(event: CalendarEventModel) =
            viewModelScope.launch {
                _events.value = _events.value.map { if (it.id == event.id) event else it }
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

        fun delete(event: CalendarEventModel) =
            viewModelScope.launch {
                _events.value = _events.value.filter { it.id != event.id }
                runCatching { db.from("calendar_events").delete { filter { eq("id", event.id) } } }
                val userId = repo.currentUserId.first() ?: return@launch
                loadEvents(userId)
            }
    }
