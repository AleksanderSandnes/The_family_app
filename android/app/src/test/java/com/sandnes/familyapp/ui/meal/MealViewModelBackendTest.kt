package com.sandnes.familyapp.ui.meal

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.MealPlanDayModel
import com.sandnes.familyapp.data.MealPlanModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** MealViewModel against a fake Supabase backend, so the real query/decoding paths run. */
class MealViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val familyChanged = MutableSharedFlow<Unit>()

    private val plan = """{"id":"p1","family_id":"f1","name":"Week 40","from_date":"2026-09-28","to_date":"2026-09-30","icon":"restaurant"}"""
    private val days =
        """[{"id":"d2","meal_plan_id":"p1","date":"2026-09-29","food":""},
            {"id":"d1","meal_plan_id":"p1","date":"2026-09-28","food":"Pasta"}]"""

    private fun resetCompanionCache() {
        val cacheField = MealViewModel::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        cacheField.set(null, emptyList<Any?>())
    }

    @Before
    fun setUp() {
        backend.install()
        resetCompanionCache()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = "f1")
        coEvery { repo.isFamilyAdmin(any()) } returns true
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plans", "[$plan]")
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plan_days", days)
    }

    @After
    fun tearDown() {
        backend.uninstall()
        resetCompanionCache()
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): MealViewModel {
        val vm = MealViewModel(repo)
        userId.value = "u1"
        settle { vm.plans.value.isNotEmpty() && vm.planProgress.value.isNotEmpty() }
        return vm
    }

    private val sample = MealPlanModel(id = "p1", familyId = "f1", name = "Week 40")

    @Test
    fun `loads plans with planned versus total day progress`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals(listOf("p1"), vm.plans.value.map { it.id })
            assertEquals(MealProgress(planned = 1, total = 2), vm.planProgress.value["p1"])
            assertTrue(vm.hasFamily.value)
            assertEquals("u1", vm.currentUserId.value)
        }

    @Test
    fun `no plans clears progress and a user without a family gets an empty list`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/meal_plans", "[]")
            val vm = MealViewModel(repo)
            userId.value = "u1"
            settle { !vm.isLoading.value && backend.requestsTo("meal_plans").isNotEmpty() }
            assertTrue(vm.planProgress.value.isEmpty())
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = null)
            familyChanged.emit(Unit)
            settle { !vm.hasFamily.value }
            assertTrue(vm.plans.value.isEmpty())
            userId.value = null
            settle { !vm.hasFamily.value }
        }

    @Test
    fun `a family change reloads and a failed load ends loading`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("meal_plans", HttpMethod.Get).size
            familyChanged.emit(Unit)
            settle { backend.requestsTo("meal_plans", HttpMethod.Get).size > before }
            backend.onJson(HttpMethod.Get, "/rest/v1/meal_plan_days", "{}", HttpStatusCode.InternalServerError)
            finish(vm.refresh())
            settle { !vm.isLoading.value }
            assertTrue(vm.isAdmin.value)
        }

    @Test
    fun `refresh does nothing while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = MealViewModel(repo)
            finish(vm.refresh())
            assertTrue(backend.requests.isEmpty())
        }

    @Test
    fun `plan detail sorts the days by date`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadPlanDetail("p1"))
            assertEquals("p1", vm.selectedPlan.value?.id)
            assertEquals(listOf("d1", "d2"), vm.days.value.map { it.id })
        }

    @Test
    fun `creating a plan inserts the plan and one row per day`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/meal_plans", """[{"id":"p9","family_id":"f1","name":"Trip"}]""")
            finish(vm.createPlan("Trip", "2026-10-01", "2026-10-03", "flight", color = 9))
            val planPost = backend.requestsTo("rest/v1/meal_plans", HttpMethod.Post).single().body
            assertTrue(planPost.contains("\"name\":\"Trip\"") && planPost.contains("\"color\":9") && planPost.contains("\"created_by\":\"u1\""))
            assertEquals(3, backend.requestsTo("meal_plan_days", HttpMethod.Post).size)
            assertNull(vm.errorRes.value)
        }

    @Test
    fun `a failed plan creation removes the optimistic row and reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/meal_plans", "{}", HttpStatusCode.InternalServerError)
            finish(vm.createPlan("Broken", "2026-10-01", "2026-10-02", "x"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            assertTrue(vm.plans.value.none { it.name == "Broken" })
            vm.clearError()
            assertNull(vm.errorRes.value)
        }

    @Test
    fun `creating a plan without a family explains why`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = null)
            val vm = loaded0()
            finish(vm.createPlan("Nope", "2026-10-01", "2026-10-02", "x"))
            assertEquals(R.string.join_or_create_a_family_to_get_started, vm.errorRes.value)
            assertFalse(vm.hasFamily.value)
        }

    private fun loaded0(): MealViewModel {
        val vm = MealViewModel(repo)
        userId.value = "u1"
        settle { backend.requests.isNotEmpty() || !vm.isLoading.value }
        return vm
    }

    @Test
    fun `renaming and restyling a plan patches it and updates the selection`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.renamePlan(sample, "Renamed"))
            finish(vm.setPlanIcon(sample, "cake"))
            finish(vm.setPlanColor(sample, 7))
            val patches = backend.requestsTo("rest/v1/meal_plans", HttpMethod.Patch).map { it.body }
            assertTrue(patches[0].contains("Renamed") && patches[1].contains("cake") && patches[2].contains("\"color\":7"))
            assertEquals(7, vm.selectedPlan.value?.color)
        }

    @Test
    fun `plan edit failures are reported`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Patch, "/rest/v1/meal_plans", "{}", HttpStatusCode.InternalServerError)
            for (action in listOf(
                { vm.renamePlan(sample, "x") },
                { vm.setPlanIcon(sample, "x") },
                { vm.setPlanColor(sample, null) },
            )) {
                finish(action())
                assertEquals(R.string.couldnt_save, vm.errorRes.value)
                vm.clearError()
            }
        }

    @Test
    fun `deleting a plan removes it and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.deletePlan(sample))
            assertTrue(
                backend
                    .requestsTo("rest/v1/meal_plans", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=eq.p1"),
            )
            backend.onJson(HttpMethod.Delete, "/rest/v1/meal_plans", "{}", HttpStatusCode.InternalServerError)
            finish(vm.deletePlan(sample))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
            userId.value = null
            finish(vm.deletePlan(sample))
        }

    @Test
    fun `setting food trims it and reloads the plan`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadPlanDetail("p1"))
            finish(vm.setFood(MealPlanDayModel(id = "d2", mealPlanId = "p1", date = "2026-09-29"), "  Soup  "))
            assertTrue(
                backend
                    .requestsTo("meal_plan_days", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("\"food\":\"Soup\""),
            )
            backend.onJson(HttpMethod.Patch, "/rest/v1/meal_plan_days", "{}", HttpStatusCode.InternalServerError)
            finish(vm.setFood(MealPlanDayModel(id = "d2", mealPlanId = "p1"), "x"))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }
}
