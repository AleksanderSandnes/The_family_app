package com.sandnes.familyapp.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.MealPlanDayModel
import com.sandnes.familyapp.data.MealPlanModel
import com.sandnes.familyapp.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Per-plan meal progress: days with a dinner planned out of total days. */
data class MealProgress(
    val planned: Int,
    val total: Int,
)

@HiltViewModel
class MealViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        companion object {
            private var cache: List<MealPlanModel> = emptyList()
        }

        private val db get() = SupabaseManager.client.postgrest

        private val _plans = MutableStateFlow(cache)
        val plans: StateFlow<List<MealPlanModel>> = _plans.asStateFlow()

        private val _planProgress = MutableStateFlow<Map<String, MealProgress>>(emptyMap())
        val planProgress: StateFlow<Map<String, MealProgress>> = _planProgress.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        /** Whether the current user belongs to a family. Gates plan creation — with no
         *  family, an insert would silently no-op (RLS/family scoping), so the UI hides
         *  the create action and points the user at joining/creating a family instead. */
        private val _hasFamily = MutableStateFlow(false)
        val hasFamily: StateFlow<Boolean> = _hasFamily.asStateFlow()

        /** One-shot, user-visible error as a string resource id. Cleared via [clearError]
         *  once shown so a failed create surfaces instead of presenting as "nothing happened". */
        private val _errorRes = MutableStateFlow<Int?>(null)
        val errorRes: StateFlow<Int?> = _errorRes.asStateFlow()

        fun clearError() {
            _errorRes.value = null
        }

        private val _selectedPlan = MutableStateFlow<MealPlanModel?>(null)
        val selectedPlan: StateFlow<MealPlanModel?> = _selectedPlan.asStateFlow()

        private val _days = MutableStateFlow<List<MealPlanDayModel>>(emptyList())
        val days: StateFlow<List<MealPlanDayModel>> = _days.asStateFlow()

        private var realtimeChannel: RealtimeChannel? = null

        /** Tracks which familyId the current channel is subscribed to, so we don't
         *  re-subscribe on every userId emission when the family hasn't changed. */
        private var subscribedFamilyId: String? = null

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    if (userId != null) {
                        val user = repo.getUser(userId)
                        _hasFamily.value = user?.familyId != null
                        if (user?.familyId != null) {
                            loadPlansOnly(user.familyId)
                            subscribeToPlansOnce(user.familyId)
                        } else {
                            _plans.value = emptyList()
                        }
                    } else {
                        _hasFamily.value = false
                        _plans.value = emptyList()
                    }
                }
            }
            viewModelScope.launch {
                repo.familyChanged.collect {
                    val userId = repo.currentUserId.first() ?: return@collect
                    val user = repo.getUser(userId)
                    _hasFamily.value = user?.familyId != null
                    if (user?.familyId != null) {
                        loadPlansOnly(user.familyId)
                        subscribeToPlansOnce(user.familyId)
                    } else {
                        _plans.value = emptyList()
                    }
                }
            }
        }

        /** Re-fetch from the server. Called on screen resume so data stays fresh
         *  even though the ViewModel is Activity-scoped and init{} only runs once. */
        fun refresh() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val user = repo.getUser(userId)
                _hasFamily.value = user?.familyId != null
                if (user?.familyId != null) loadPlansOnly(user.familyId)
            }

        /** Fetches meal plans and updates the StateFlow. Does NOT touch the realtime
         *  channel — safe to call repeatedly without causing subscription churn. */
        private suspend fun loadPlansOnly(familyId: String) {
            if (_plans.value.isEmpty()) _isLoading.value = true
            runCatching {
                val result =
                    db
                        .from("meal_plans")
                        .select { filter { eq("family_id", familyId) } }
                        .decodeList<MealPlanModel>()
                cache = result
                _plans.value = result
                loadPlanProgress(result.map { it.id })
            }
            _isLoading.value = false
        }

        /** Loads planned/total day counts per plan in one query. */
        private suspend fun loadPlanProgress(planIds: List<String>) {
            if (planIds.isEmpty()) {
                _planProgress.value = emptyMap()
                return
            }
            runCatching {
                val allDays =
                    db
                        .from("meal_plan_days")
                        .select { filter { isIn("meal_plan_id", planIds) } }
                        .decodeList<MealPlanDayModel>()
                _planProgress.value =
                    allDays.groupBy { it.mealPlanId }.mapValues { (_, days) ->
                        MealProgress(planned = days.count { it.food.isNotBlank() }, total = days.size)
                    }
            }
        }

        /** Creates a realtime channel for the given familyId, but only if we are not
         *  already subscribed to that family. Idempotent across userId emissions. */
        private fun subscribeToPlansOnce(familyId: String) {
            if (subscribedFamilyId == familyId) return
            subscribedFamilyId = familyId

            // Tear down any existing channel for a different family.
            realtimeChannel?.let { old ->
                viewModelScope.launch { runCatching { SupabaseManager.client.realtime.removeChannel(old) } }
            }

            val channel = runCatching { SupabaseManager.client.channel("meal-plans-$familyId") }.getOrNull() ?: return
            realtimeChannel = channel
            val flow =
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "meal_plans"
                    filter("family_id", FilterOperator.EQ, familyId)
                }
            viewModelScope.launch {
                channel.subscribe()
                // On any realtime event just reload data — do NOT re-subscribe.
                flow.collect { loadPlansOnly(familyId) }
            }
        }

        override fun onCleared() {
            viewModelScope.launch {
                realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            }
        }

        fun loadPlanDetail(planId: String) =
            viewModelScope.launch {
                runCatching {
                    coroutineScope {
                        val planDeferred =
                            async {
                                db
                                    .from("meal_plans")
                                    .select { filter { eq("id", planId) } }
                                    .decodeList<MealPlanModel>()
                                    .firstOrNull()
                            }
                        val daysDeferred =
                            async {
                                db
                                    .from("meal_plan_days")
                                    .select { filter { eq("meal_plan_id", planId) } }
                                    .decodeList<MealPlanDayModel>()
                            }
                        val planResult = planDeferred.await()
                        val daysResult = daysDeferred.await()
                        _selectedPlan.value = planResult
                        _days.value = daysResult.sortedBy { it.date }
                    }
                }
            }

        fun createPlan(
            name: String,
            fromIso: String,
            toIso: String,
            icon: String,
            color: Int? = null,
        ) = viewModelScope.launch {
            val userId = repo.currentUserId.first()
            val user = userId?.let { repo.getUser(it) }
            val familyId = user?.familyId
            if (familyId == null) {
                // No family → creation cannot be scoped and would silently no-op. Surface it
                // instead of pretending the plan was created.
                _hasFamily.value = false
                _errorRes.value = R.string.join_or_create_a_family_to_get_started
                return@launch
            }

            val from = LocalDate.parse(fromIso)
            val to = LocalDate.parse(toIso)
            val cal =
                Calendar.getInstance().also {
                    it.time = Date.from(from.atStartOfDay(ZoneOffset.UTC).toInstant())
                }
            val week = cal.get(Calendar.WEEK_OF_YEAR)

            val optimistic =
                MealPlanModel(
                    id = "temp-${java.util.UUID.randomUUID()}",
                    familyId = familyId,
                    name = name,
                    icon = icon,
                    fromDate = fromIso,
                    toDate = toIso,
                    week = week,
                    color = color,
                )
            _plans.value = _plans.value + optimistic

            val result = runCatching { insertPlanWithDays(optimistic, from, to) }
            // Don't let a failed insert present as "nothing happened": surface it so the
            // reload below (which drops the optimistic temp row) isn't silently confusing.
            result.onFailure {
                _plans.value = _plans.value.filterNot { it.id == optimistic.id }
                _errorRes.value = R.string.couldnt_save
            }
            loadPlansOnly(familyId)
        }

        /** Inserts the meal plan row (from [draft]) and one day row per calendar day in the range. */
        private suspend fun insertPlanWithDays(
            draft: MealPlanModel,
            from: LocalDate,
            to: LocalDate,
        ) {
            val plan =
                db
                    .from("meal_plans")
                    .insert(
                        buildJsonObject {
                            put("family_id", draft.familyId)
                            put("name", draft.name)
                            put("icon", draft.icon)
                            put("from_date", draft.fromDate)
                            put("to_date", draft.toDate)
                            put("week", draft.week)
                            draft.color?.let { put("color", it) }
                        },
                    ) { select() }
                    .decodeList<MealPlanModel>()
                    .first()

            var current = from
            while (!current.isAfter(to)) {
                db.from("meal_plan_days").insert(
                    buildJsonObject {
                        put("meal_plan_id", plan.id)
                        put("day", current.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                        put("date", current.toString())
                    },
                )
                current = current.plusDays(1)
            }
        }

        fun renamePlan(
            plan: MealPlanModel,
            newName: String,
        ) = viewModelScope.launch {
            val updated = plan.copy(name = newName)
            _selectedPlan.value = updated
            _plans.value = _plans.value.map { if (it.id == plan.id) updated else it }
            cache = _plans.value
            runCatching {
                db.from("meal_plans").update({ set("name", newName) }) { filter { eq("id", plan.id) } }
            }
        }

        fun setPlanIcon(
            plan: MealPlanModel,
            newIcon: String,
        ) = viewModelScope.launch {
            val updated = plan.copy(icon = newIcon)
            _selectedPlan.value = updated
            _plans.value = _plans.value.map { if (it.id == plan.id) updated else it }
            cache = _plans.value
            runCatching {
                db.from("meal_plans").update({ set("icon", newIcon) }) { filter { eq("id", plan.id) } }
            }
        }

        fun setPlanColor(
            plan: MealPlanModel,
            newColor: Int?,
        ) = viewModelScope.launch {
            val updated = plan.copy(color = newColor)
            _selectedPlan.value = updated
            _plans.value = _plans.value.map { if (it.id == plan.id) updated else it }
            cache = _plans.value
            runCatching {
                db.from("meal_plans").update({ set("color", newColor) }) { filter { eq("id", plan.id) } }
            }
        }

        fun deletePlan(plan: MealPlanModel) =
            viewModelScope.launch {
                _plans.value = _plans.value.filter { it.id != plan.id }
                runCatching { db.from("meal_plans").delete { filter { eq("id", plan.id) } } }
                val userId = repo.currentUserId.first() ?: return@launch
                val user = repo.getUser(userId) ?: return@launch
                user.familyId?.let { loadPlansOnly(it) }
            }

        fun setFood(
            day: MealPlanDayModel,
            food: String,
        ) =
            viewModelScope.launch {
                _days.value = _days.value.map { if (it.id == day.id) it.copy(food = food) else it }
                runCatching {
                    db.from("meal_plan_days").update({
                        set("food", food)
                    }) { filter { eq("id", day.id) } }
                }
                loadPlanDetail(day.mealPlanId).join()
            }
    }
