package com.example.mainactivity.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.BirthdayModel
import com.example.mainactivity.data.CalendarEventModel
import com.example.mainactivity.data.FamilyModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MealPlanDayModel
import com.example.mainactivity.data.MealPlanModel
import com.example.mainactivity.data.ShoppingItemModel
import com.example.mainactivity.data.ShoppingListModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import com.example.mainactivity.ui.birthday.nextBirthdayDate
import com.example.mainactivity.ui.birthday.turnsAge
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val user: UserModel? = null,
    val family: FamilyModel? = null,
    val memberCount: Int = 0,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    // Glanceable summary (D4) — null/0 when there's nothing to show.
    val tonightMeal: String? = null,
    val nextEventTitle: String? = null,
    val nextEventWhen: String? = null,
    val nextBirthdayName: String? = null,
    val nextBirthdayWhen: String? = null,
    val shoppingRemaining: Int = 0,
) {
    /** True when at least one summary card has content to render. */
    val hasSummary: Boolean
        get() = tonightMeal != null || nextEventTitle != null || nextBirthdayName != null || shoppingRemaining > 0
}

private data class HomeSummary(
    val tonightMeal: String? = null,
    val nextEventTitle: String? = null,
    val nextEventWhen: String? = null,
    val nextBirthdayName: String? = null,
    val nextBirthdayWhen: String? = null,
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
                var memberCount = 0
                var summary = HomeSummary()
                // Only touch the network when the user is actually in a family.
                if (familyId != null) {
                    val db = SupabaseManager.client.postgrest
                    memberCount =
                        db
                            .from("users")
                            .select { filter { eq("family_id", familyId) } }
                            .decodeList<UserModel>()
                            .size
                    // Summary is best-effort — a failure here must not blank the whole screen.
                    summary = runCatching { loadSummary(db, familyId) }.getOrDefault(HomeSummary())
                }

                _state.value =
                    HomeUiState(
                        user = user,
                        family = family,
                        memberCount = memberCount,
                        isLoading = false,
                        tonightMeal = summary.tonightMeal,
                        nextEventTitle = summary.nextEventTitle,
                        nextEventWhen = summary.nextEventWhen,
                        nextBirthdayName = summary.nextBirthdayName,
                        nextBirthdayWhen = summary.nextBirthdayWhen,
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
            val event = loadNextEvent(db, familyId, today)
            val birthday = loadNextBirthday(db, familyId, today)
            return HomeSummary(
                tonightMeal = loadTonightMeal(db, familyId, today),
                nextEventTitle = event?.activity,
                nextEventWhen = event?.let { eventWhen(it, today) },
                nextBirthdayName = birthday?.first?.name,
                nextBirthdayWhen = birthday?.let { birthdayWhen(it.first, it.second, today) },
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

        /** Next upcoming event (ends today or later). */
        private suspend fun loadNextEvent(
            db: Postgrest,
            familyId: String,
            today: LocalDate,
        ): CalendarEventModel? =
            runCatching {
                db
                    .from("calendar_events")
                    .select { filter { eq("family_id", familyId) } }
                    .decodeList<CalendarEventModel>()
                    .filter {
                        val end = runCatching { LocalDate.parse(it.dateTo.ifBlank { it.dateFrom }) }.getOrNull()
                        end != null && !end.isBefore(today)
                    }.minByOrNull { runCatching { LocalDate.parse(it.dateFrom) }.getOrNull() ?: LocalDate.MAX }
            }.getOrNull()

        /** Soonest upcoming birthday paired with its next occurrence date. */
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

private val EVENT_DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun eventWhen(
    e: CalendarEventModel,
    today: LocalDate,
): String {
    val from = runCatching { LocalDate.parse(e.dateFrom) }.getOrNull() ?: return e.dateFrom
    val datePart =
        when (from) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> from.format(EVENT_DATE_FMT)
        }
    return if (e.allDay || e.timeFrom.isBlank()) datePart else "$datePart · ${e.timeFrom}"
}

private fun birthdayWhen(
    b: BirthdayModel,
    next: LocalDate,
    today: LocalDate,
): String {
    val days = (next.toEpochDay() - today.toEpochDay()).toInt()
    val age = turnsAge(b.date, today)
    val whenStr =
        when (days) {
            0 -> "Today!"
            1 -> "Tomorrow"
            else -> "in $days days"
        }
    return if (age != null) "Turns $age · $whenStr" else whenStr
}
