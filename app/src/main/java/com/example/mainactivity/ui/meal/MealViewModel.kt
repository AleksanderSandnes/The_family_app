package com.example.mainactivity.ui.meal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MealPlanDayEntity
import com.example.mainactivity.data.MealPlanEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MealViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.mealPlanDao

    val plans: Flow<List<MealPlanEntity>> = repo.currentUser.flatMapLatest { user ->
        val fid = user?.familyId ?: 0L
        dao.plansForFamily(fid)
    }

    fun plan(id: Long) = dao.observePlan(id)
    fun days(planId: Long) = dao.daysForPlan(planId)

    fun createWeekPlan() = viewModelScope.launch {
        val user = repo.currentUser.first()
        val familyId = user?.familyId ?: 0L
        val cal = Calendar.getInstance()
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val from = fmt.format(cal.time)
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dates = ArrayList<String>()
        for (i in 0 until 7) {
            dates.add(fmt.format(cal.time)); cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val to = fmt.format(cal.time)
        val planId = dao.insertPlan(MealPlanEntity(fromDate = from, toDate = to, familyId = familyId, week = week))
        dayNames.forEachIndexed { index, name ->
            dao.insertDay(MealPlanDayEntity(mealPlanId = planId, day = name, date = dates[index]))
        }
    }

    fun deletePlan(plan: MealPlanEntity) = viewModelScope.launch { dao.deletePlan(plan) }
    fun setFood(day: MealPlanDayEntity, food: String) = viewModelScope.launch { dao.updateDay(day.copy(food = food)) }
}
