package com.sandnes.familyapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.data.BirthdayModel
import com.sandnes.familyapp.data.CalendarEventModel
import com.sandnes.familyapp.data.FamilyModel
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.MealPlanDayModel
import com.sandnes.familyapp.data.MealPlanModel
import com.sandnes.familyapp.data.ShoppingItemModel
import com.sandnes.familyapp.data.ShoppingListModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.data.remote.SupabaseManager
import com.sandnes.familyapp.ui.birthday.nextBirthdayDate
import com.sandnes.familyapp.ui.birthday.turnsAge
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val user: UserModel? = null,
    val family: FamilyModel? = null,
    val memberCount: Int = 0,
    val familyMembers: List<UserModel> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    // Glanceable summary (D4) — null/0 when there's nothing to show.
    // Date/label strings are composed in the UI layer so they follow the in-app locale.
    val tonightMeal: String? = null,
    val nextEvent: CalendarEventModel? = null,
    val nextBirthday: BirthdayModel? = null,
    val nextBirthdayDate: LocalDate? = null,
    val shoppingRemaining: Int = 0,
) {
    /** True when at least one summary card has content to render. */
    val hasSummary: Boolean
        get() = tonightMeal != null || nextEvent != null || nextBirthday != null || shoppingRemaining > 0
}

private data class HomeSummary(
    val tonightMeal: String? = null,
    val nextEvent: CalendarEventModel? = null,
    val nextBirthday: BirthdayModel? = null,
    val nextBirthdayDate: LocalDate? = null,
    val shoppingRemaining: Int = 0,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeUiState())
        val state: StateFlow<HomeUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    if (userId != null) load(userId) else _state.value = HomeUiState(isLoading = false)
                }
            }
            viewModelScope.launch {
                repo.familyChanged.collect {
                    val userId = repo.currentUserId.first() ?: return@collect
                    load(userId)
                }
            }
        }

        fun refresh() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                load(userId)
            }

        private suspend fun load(userId: String) {
            _state.value = _state.value.copy(isLoading = true, loadError = false)
            runCatching {
                val user = repo.getUser(userId)
                if (user == null) {
                    _state.value = HomeUiState(isLoading = false, loadError = true)
                    return
                }
                val family = user.familyId?.let { repo.getFamily(it) }
                val familyId = user.familyId
                var members: List<UserModel> = emptyList()
                var summary = HomeSummary()
                // Only touch the network when the user is actually in a family.
                if (familyId != null) {
                    members = repo.getFamilyMembers(familyId)
                    // Summary is best-effort — a failure here must not blank the whole screen.
                    summary =
                        runCatching {
                            loadSummary(SupabaseManager.client.postgrest, familyId)
                        }.getOrDefault(HomeSummary())
                }

                _state.value =
                    HomeUiState(
                        user = user,
                        family = family,
                        memberCount = members.size,
                        familyMembers = members,
                        isLoading = false,
                        tonightMeal = summary.tonightMeal,
                        nextEvent = summary.nextEvent,
                        nextBirthday = summary.nextBirthday,
                        nextBirthdayDate = summary.nextBirthdayDate,
                        shoppingRemaining = summary.shoppingRemaining,
                    )
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, loadError = true)
            }
        }

        private suspend fun loadSummary(
            db: Postgrest,
            familyId: String,
        ): HomeSummary {
            val today = LocalDate.now()
            val birthday = loadNextBirthday(db, familyId, today)
            return HomeSummary(
                tonightMeal = loadTonightMeal(db, familyId, today),
                nextEvent = loadNextEvent(db, familyId, today),
                nextBirthday = birthday?.first,
                nextBirthdayDate = birthday?.second,
                shoppingRemaining = loadShoppingRemaining(db, familyId),
            )
        }

        /** Tonight's meal: the plan whose range covers today, then today's day row. */
        private suspend fun loadTonightMeal(
            db: Postgrest,
            familyId: String,
            today: LocalDate,
        ): String? =
            runCatching {
                val plans =
                    db
                        .from("meal_plans")
                        .select { filter { eq("family_id", familyId) } }
                        .decodeList<MealPlanModel>()
                val active =
                    plans.firstOrNull {
                        val f = runCatching { LocalDate.parse(it.fromDate) }.getOrNull()
                        val t = runCatching { LocalDate.parse(it.toDate) }.getOrNull()
                        f != null && t != null && !f.isAfter(today) && !t.isBefore(today)
                    }
                active?.let { plan ->
                    db
                        .from("meal_plan_days")
                        .select {
                            filter {
                                eq("meal_plan_id", plan.id)
                                eq("date", today.toString())
                            }
                        }.decodeList<MealPlanDayModel>()
                        .firstOrNull()
                        ?.food
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()

        /**
         * Next upcoming or ongoing event. Drops events that have already ended — including a timed
         * event earlier today — so it falls off the dashboard once it's over.
         */
        private suspend fun loadNextEvent(
            db: Postgrest,
            familyId: String,
            today: LocalDate,
        ): CalendarEventModel? =
            runCatching {
                val nowMinutes = LocalTime.now().let { it.hour * MINUTES_PER_HOUR + it.minute }
                db
                    .from("calendar_events")
                    .select { filter { eq("family_id", familyId) } }
                    .decodeList<CalendarEventModel>()
                    .filterNot { eventHasEnded(it, today, nowMinutes) }
                    .minByOrNull { runCatching { LocalDate.parse(it.dateFrom) }.getOrNull() ?: LocalDate.MAX }
            }.getOrNull()

        /**
         * Soonest upcoming birthday paired with its next occurrence date, only when it's within a
         * week — matching the iOS dashboard.
         */
        private suspend fun loadNextBirthday(
            db: Postgrest,
            familyId: String,
            today: LocalDate,
        ): Pair<BirthdayModel, LocalDate>? =
            runCatching {
                db
                    .from("birthdays")
                    .select { filter { eq("family_id", familyId) } }
                    .decodeList<BirthdayModel>()
            }.getOrNull()
                .orEmpty()
                .mapNotNull { b -> nextBirthdayDate(b.date, today)?.let { b to it } }
                .filter { (it.second.toEpochDay() - today.toEpochDay()) <= BIRTHDAY_HORIZON_DAYS }
                .minByOrNull { it.second }

        /** Items left to buy across all family lists. */
        private suspend fun loadShoppingRemaining(
            db: Postgrest,
            familyId: String,
        ): Int =
            runCatching {
                val ids =
                    db
                        .from("shopping_lists")
                        .select { filter { eq("family_id", familyId) } }
                        .decodeList<ShoppingListModel>()
                        .map { it.id }
                if (ids.isEmpty()) {
                    0
                } else {
                    db
                        .from("shopping_items")
                        .select {
                            filter {
                                isIn("list_id", ids)
                                eq("checked", false)
                            }
                        }.decodeList<ShoppingItemModel>()
                        .size
                }
            }.getOrDefault(0)
    }

private const val MINUTES_PER_HOUR = 60
private const val BIRTHDAY_HORIZON_DAYS = 7L

private val EVENT_DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** Minutes since midnight for an "HH:mm" string, or null if it isn't a valid time. */
@Suppress("MagicNumber") // 23:59 upper bounds for hours/minutes — self-describing
internal fun minutesSinceMidnight(hhmm: String): Int? {
    val parts = hhmm.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2) return null
    val (h, m) = parts
    if (h !in 0..23 || m !in 0..59) return null
    return h * MINUTES_PER_HOUR + m
}

/**
 * True once an event has finished, so the dashboard drops it from "next event". Past days are
 * over and future days are not; an event ending today is over only once its end time has passed.
 * All-day events and events with no end time stay visible until the day rolls over.
 */
internal fun eventHasEnded(
    event: CalendarEventModel,
    today: LocalDate,
    nowMinutes: Int,
): Boolean {
    val endIso = event.dateTo.ifBlank { event.dateFrom }
    val endDate = runCatching { LocalDate.parse(endIso) }.getOrNull()
    return when {
        endDate == null -> false
        endDate.isBefore(today) -> true
        endDate.isAfter(today) -> false
        event.allDay -> false
        else -> {
            val endMinutes = minutesSinceMidnight(event.timeTo)
            endMinutes != null && endMinutes <= nowMinutes
        }
    }
}

/** Localized labels for the summary detail lines, resolved by the UI layer (in-app locale). */
data class SummaryLabels(
    val today: String,
    val tomorrow: String,
    val todayExclaim: String,
    /** Format string with one %1$d arg, e.g. "in %1$d days". */
    val inDaysFormat: String,
    /** Format string with one %1$d arg, e.g. "Turns %1$d". */
    val turnsFormat: String,
)

internal fun eventWhen(
    e: CalendarEventModel,
    today: LocalDate,
    labels: SummaryLabels,
): String {
    val from = runCatching { LocalDate.parse(e.dateFrom) }.getOrNull() ?: return e.dateFrom
    val datePart =
        when (from) {
            today -> labels.today
            today.plusDays(1) -> labels.tomorrow
            else -> from.format(EVENT_DATE_FMT)
        }
    return if (e.allDay || e.timeFrom.isBlank()) datePart else "$datePart · ${e.timeFrom}"
}

internal fun birthdayWhen(
    b: BirthdayModel,
    next: LocalDate,
    today: LocalDate,
    labels: SummaryLabels,
): String {
    val days = (next.toEpochDay() - today.toEpochDay()).toInt()
    val age = turnsAge(b.date, today)
    val whenStr =
        when (days) {
            0 -> labels.todayExclaim
            1 -> labels.tomorrow
            else -> labels.inDaysFormat.format(days)
        }
    return if (age != null) "${labels.turnsFormat.format(age)} · $whenStr" else whenStr
}
