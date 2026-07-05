package com.example.mainactivity.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MealPlanDayModel
import com.example.mainactivity.data.MealPlanModel
import com.example.mainactivity.data.remote.SupabaseManager
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
                        if (user?.familyId != null) {
                            loadPlansOnly(user.familyId)
                            subscribeToPlansOnce(user.familyId)
                        } else {
                            _plans.value = emptyList()
                        }
                    } else {
                        _plans.value = emptyList()
                    }
                }
            }
            viewModelScope.launch {
                repo.familyChanged.collect {
                    val userId = repo.currentUserId.first() ?: return@collect
                    val user = repo.getUser(userId)
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
        ) = viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId) ?: return@launch
            val familyId = user.familyId ?: return@launch

            val from = LocalDate.parse(fromIso)
            val to = LocalDate.parse(toIso)
            val cal =
                Calendar.getInstance().also {
                    it.time = Date.from(from.atStartOfDay(ZoneOffset.UTC).toInstant())
                }
            val week = cal.get(Calendar.WEEK_OF_YEAR)

            val tempId = "temp-${java.util.UUID.randomUUID()}"
            _plans.value = _plans.value +
                MealPlanModel(
                    id = tempId,
                    familyId = familyId,
                    name = name,
                    icon = icon,
                    fromDate = fromIso,
                    toDate = toIso,
                    week = week,
                )

            runCatching {
                val plan =
                    db
                        .from("meal_plans")
                        .insert(
                            buildJsonObject {
                                put("family_id", familyId)
                                put("name", name)
                                put("icon", icon)
                                put("from_date", fromIso)
                                put("to_date", toIso)
                                put("week", week)
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
            loadPlansOnly(familyId)
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
