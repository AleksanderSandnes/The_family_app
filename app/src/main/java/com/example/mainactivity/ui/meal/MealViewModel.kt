package com.example.mainactivity.ui.meal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MealPlanDayModel
import com.example.mainactivity.data.MealPlanModel
import com.example.mainactivity.data.remote.SupabaseManager
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MealViewModel(
    app: Application,
) : AndroidViewModel(app) {
    companion object {
        private var cache: List<MealPlanModel> = emptyList()
    }

    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _plans = MutableStateFlow(cache)
    val plans: StateFlow<List<MealPlanModel>> = _plans.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedPlan = MutableStateFlow<MealPlanModel?>(null)
    val selectedPlan: StateFlow<MealPlanModel?> = _selectedPlan.asStateFlow()

    private val _days = MutableStateFlow<List<MealPlanDayModel>>(emptyList())
    val days: StateFlow<List<MealPlanDayModel>> = _days.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                if (userId != null) {
                    val user = repo.getUser(userId)
                    if (user?.familyId != null) {
                        loadPlans(user.familyId)
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
                if (user?.familyId != null) loadPlans(user.familyId) else _plans.value = emptyList()
            }
        }
    }

    /** Re-fetch from the server. Called on screen resume so data stays fresh
     *  even though the ViewModel is Activity-scoped and init{} only runs once. */
    fun refresh() =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId)
            if (user?.familyId != null) loadPlans(user.familyId)
        }

    private suspend fun loadPlans(familyId: String) {
        if (_plans.value.isEmpty()) _isLoading.value = true
        runCatching {
            val result =
                db
                    .from("meal_plans")
                    .select { filter { eq("family_id", familyId) } }
                    .decodeList<MealPlanModel>()
            cache = result
            _plans.value = result
            subscribeToPlans(familyId)
        }
        _isLoading.value = false
    }

    private suspend fun subscribeToPlans(familyId: String) {
        realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("meal-plans-$familyId")
        realtimeChannel = channel
        val flow =
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "meal_plans"
                filter("family_id", FilterOperator.EQ, familyId)
            }
        channel.subscribe()
        viewModelScope.launch { flow.collect { loadPlans(familyId) } }
    }

    override fun onCleared() {
        super.onCleared()
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
                    _selectedPlan.value = planDeferred.await()
                    _days.value = daysDeferred.await()
                }
            }
        }

    fun createWeekPlan() =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId) ?: return@launch
            val familyId = user.familyId ?: return@launch
            val cal = Calendar.getInstance()
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val from = fmt.format(cal.time)
            val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            val dates = ArrayList<String>()
            for (i in 0 until 7) {
                dates.add(fmt.format(cal.time))
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            cal.add(Calendar.DAY_OF_MONTH, -1)
            val to = fmt.format(cal.time)

            val tempId = "temp-${System.currentTimeMillis()}"
            _plans.value = _plans.value + MealPlanModel(id = tempId, familyId = familyId, fromDate = from, toDate = to, week = week)

            runCatching {
                val plan =
                    db
                        .from("meal_plans")
                        .insert(
                            buildJsonObject {
                                put("family_id", familyId)
                                put("from_date", from)
                                put("to_date", to)
                                put("week", week)
                            },
                        ) { select() }
                        .decodeList<MealPlanModel>()
                        .first()
                dayNames.forEachIndexed { index, name ->
                    db.from("meal_plan_days").insert(
                        buildJsonObject {
                            put("meal_plan_id", plan.id)
                            put("day", name)
                            put("date", dates[index])
                        },
                    )
                }
            }
            loadPlans(familyId)
        }

    fun deletePlan(plan: MealPlanModel) =
        viewModelScope.launch {
            _plans.value = _plans.value.filter { it.id != plan.id }
            runCatching { db.from("meal_plans").delete { filter { eq("id", plan.id) } } }
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId) ?: return@launch
            user.familyId?.let { loadPlans(it) }
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
