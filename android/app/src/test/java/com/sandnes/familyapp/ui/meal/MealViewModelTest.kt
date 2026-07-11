package com.sandnes.familyapp.ui.meal

import app.cash.turbine.test
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.MealPlanDayModel
import com.sandnes.familyapp.data.MealPlanModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.data.remote.SupabaseManager
import com.sandnes.familyapp.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [MealViewModel].
 *
 * All DB and Realtime operations go through [SupabaseManager.client] directly and are
 * wrapped in [runCatching]. In the test environment the Supabase client is not
 * initialised, so network/DB calls fail silently. Tests therefore focus on:
 *   - Initial StateFlow values
 *   - Optimistic state mutations (plans / days StateFlows) applied before network calls
 *   - Lifecycle triggers (userId flow, familyChanged flow, refresh())
 *
 * [UserModel.familyId] is kept null in the default mock so that
 * [MealViewModel.subscribeToPlansOnce] is never reached (it calls
 * [SupabaseManager.client.channel] outside of [runCatching]).  Tests that
 * exercise [createPlan]'s optimistic path override [repo.getUser] to return a
 * non-null familyId only after [init] has already collected the userId emission
 * with the null-familyId mock, ensuring the subscription path is never triggered.
 */
@RunWith(JUnit4::class)
class MealViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: MealViewModel

    @Before
    fun setUp() {
        mockkObject(SupabaseManager)
        every { SupabaseManager.client } throws RuntimeException("Supabase client not available in unit tests")
        resetCompanionCache()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        // Default: return a user with no familyId so subscribeToPlansOnce is never called
        // (avoids the Supabase Realtime WebSocket path in tests).
        coEvery { repo.getUser(any()) } returns UserModel(id = "user-1", familyId = null)
        vm = MealViewModel(repo)
    }

    /**
     * Clears the companion-object [MealViewModel.cache] between tests so that
     * optimistic mutations from [renamePlan] / [setPlanIcon] (which write back to
     * [cache]) cannot bleed into the next test's ViewModel construction.
     */
    private fun resetCompanionCache() {
        // `cache` is a companion-object var, compiled to a private STATIC field on the
        // MealViewModel class itself (not on the Companion class).
        val cacheField = MealViewModel::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        cacheField.set(null, emptyList<MealPlanModel>())
    }

    /** Seeds [MealViewModel._plans] via reflection, bypassing the Supabase load path. */
    private fun seedPlans(plans: List<MealPlanModel>) {
        val field = MealViewModel::class.java.getDeclaredField("_plans")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<List<MealPlanModel>>).value = plans
    }

    /** Seeds [MealViewModel._days] via reflection, bypassing the Supabase load path. */
    private fun seedDays(days: List<MealPlanDayModel>) {
        val field = MealViewModel::class.java.getDeclaredField("_days")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<List<MealPlanDayModel>>).value = days
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial plans list is empty before coroutines run`() {
        assertTrue("plans must start empty", vm.plans.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false before coroutines run`() {
        assertFalse("isLoading must start false", vm.isLoading.value)
    }

    @Test
    fun `initial selectedPlan is null before coroutines run`() {
        assertNull("selectedPlan must start null", vm.selectedPlan.value)
    }

    @Test
    fun `initial days list is empty before coroutines run`() {
        assertTrue("days must start empty", vm.days.value.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Null userId — no load, no getUser call
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `null userId results in empty plans after coroutines idle`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            assertTrue("plans should be empty when userId is null", vm.plans.value.isEmpty())
        }

    @Test
    fun `null userId does not trigger getUser`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Non-null userId — load triggered; null familyId → plans stay empty
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `non-null userId triggers at least one getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()
            coVerify(atLeast = 1) { repo.getUser("user-1") }
        }

    @Test
    fun `userId with null familyId leaves plans empty`() =
        runTest(dispatcherRule.dispatcher) {
            // Default mock already returns UserModel(familyId = null)
            userId.value = "user-1"
            advanceUntilIdle()
            assertTrue("plans should be empty when familyId is null", vm.plans.value.isEmpty())
        }

    @Test
    fun `userId becoming null after non-null clears plans`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            userId.value = null
            advanceUntilIdle()

            assertTrue("plans should be cleared when userId becomes null", vm.plans.value.isEmpty())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. createPlan — optimistic add + rollback
    //
    // The optimistic field mapping is covered via [buildOptimisticPlan] (pure). The
    // ViewModel path is covered by the rollback test: in this suite the Supabase client
    // always throws, so a createPlan that passes the family gate must roll the temp row
    // back out of [MealViewModel.plans] and surface an error.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `buildOptimisticPlan maps name icon and familyId`() {
        val plan = buildOptimisticPlan("Week 1", "2024-01-01", "2024-01-07", "restaurant", null, "fam-1")
        assertEquals("Plan name should match", "Week 1", plan.name)
        assertEquals("Plan icon should match", "restaurant", plan.icon)
        assertEquals("familyId should match", "fam-1", plan.familyId)
        assertTrue("temp id expected", plan.id.startsWith("temp-"))
    }

    @Test
    fun `buildOptimisticPlan stores the date range and week`() {
        val plan = buildOptimisticPlan("Date Range Plan", "2024-03-01", "2024-03-07", "restaurant", null, "fam-1")
        assertEquals("fromDate should match", "2024-03-01", plan.fromDate)
        assertEquals("toDate should match", "2024-03-07", plan.toDate)
        assertEquals("week should be derived from fromDate", 9, plan.week)
    }

    @Test
    fun `buildOptimisticPlan stores the specified color`() {
        val plan = buildOptimisticPlan("Colored Plan", "2024-04-01", "2024-04-07", "restaurant", 0x14B8A6, "fam-1")
        assertEquals("color should be stored", 0x14B8A6, plan.color)
    }

    @Test
    fun `buildOptimisticPlan without color defaults to null color`() {
        val plan = buildOptimisticPlan("Plain Plan", "2024-05-01", "2024-05-07", "restaurant", null, "fam-1")
        assertNull("color should default to null", plan.color)
    }

    @Test
    fun `createPlan rolls back the optimistic item and surfaces an error when insert fails`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle() // init collector runs; null familyId → no subscription

            coEvery { repo.getUser("user-1") } returns UserModel(id = "user-1", familyId = "fam-1")
            vm.createPlan("Week 1", "2024-01-01", "2024-01-07", "restaurant")
            advanceUntilIdle()

            assertTrue("failed insert should roll the temp plan back out", vm.plans.value.isEmpty())
            assertEquals(
                "a failed create must surface an error",
                com.sandnes.familyapp.R.string.couldnt_save,
                vm.errorRes.value,
            )
        }

    @Test
    fun `createPlan when user has null familyId does not add to plans`() =
        runTest(dispatcherRule.dispatcher) {
            // Default mock returns null familyId — early return inside createPlan
            userId.value = "user-1"
            advanceUntilIdle()

            vm.createPlan("Should Not Appear", "2024-01-01", "2024-01-07", "restaurant")
            advanceUntilIdle()

            assertTrue("plans should stay empty when familyId is null", vm.plans.value.isEmpty())
        }

    @Test
    fun `createPlan when userId is null does not add to plans`() =
        runTest(dispatcherRule.dispatcher) {
            // userId stays null — createPlan's first() returns null → early return
            vm.createPlan("Ghost Plan", "2024-01-01", "2024-01-07", "restaurant")
            advanceUntilIdle()

            assertTrue("plans should stay empty when userId is null", vm.plans.value.isEmpty())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4b. hasFamily gate + createPlan error surfacing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `hasFamily is false initially`() {
        assertFalse("hasFamily must start false", vm.hasFamily.value)
    }

    @Test
    fun `errorRes is null initially`() {
        assertNull("errorRes must start null", vm.errorRes.value)
    }

    @Test
    fun `hasFamily becomes true when the user has a family`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("user-1") } returns UserModel(id = "user-1", familyId = "fam-1")
            userId.value = "user-1"
            advanceUntilIdle()
            assertTrue("hasFamily should be true once the user belongs to a family", vm.hasFamily.value)
        }

    @Test
    fun `hasFamily stays false when the user has no family`() =
        runTest(dispatcherRule.dispatcher) {
            // Default mock returns null familyId
            userId.value = "user-1"
            advanceUntilIdle()
            assertFalse("hasFamily should stay false without a family", vm.hasFamily.value)
        }

    @Test
    fun `createPlan without a family surfaces an error and adds nothing`() =
        runTest(dispatcherRule.dispatcher) {
            // Default mock returns null familyId
            userId.value = "user-1"
            advanceUntilIdle()

            vm.createPlan("No Family Plan", "2024-01-01", "2024-01-07", "restaurant")
            advanceUntilIdle()

            assertNotNull("createPlan without a family should set an error", vm.errorRes.value)
            assertTrue("plans should stay empty without a family", vm.plans.value.isEmpty())
        }

    @Test
    fun `clearError resets the error state`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()
            vm.createPlan("No Family Plan", "2024-01-01", "2024-01-07", "restaurant")
            advanceUntilIdle()

            vm.clearError()
            assertNull("errorRes should be cleared", vm.errorRes.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. deletePlan — optimistic removal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deletePlan removes the targeted plan from the list`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "To Delete")
            seedPlans(listOf(plan))

            vm.deletePlan(plan)
            advanceUntilIdle()

            assertTrue("plans should be empty after deleting the only plan", vm.plans.value.isEmpty())
        }

    @Test
    fun `deletePlan only removes the targeted plan leaving others intact`() =
        runTest(dispatcherRule.dispatcher) {
            val plan1 = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan 1")
            val plan2 = MealPlanModel(id = "plan-2", familyId = "fam-1", name = "Plan 2")
            seedPlans(listOf(plan1, plan2))

            vm.deletePlan(plan1)
            advanceUntilIdle()

            assertEquals("One plan should remain", 1, vm.plans.value.size)
            assertEquals("Remaining plan should be Plan 2", "Plan 2", vm.plans.value[0].name)
        }

    @Test
    fun `deletePlan with non-existent id leaves the list unchanged`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Existing")
            seedPlans(listOf(plan))

            vm.deletePlan(MealPlanModel(id = "nonexistent-id", familyId = "fam-1", name = "Ghost"))
            advanceUntilIdle()

            assertEquals("Plan list should be unchanged", 1, vm.plans.value.size)
            assertEquals("Existing plan should still be there", "Existing", vm.plans.value[0].name)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. renamePlan — optimistic rename in list and selectedPlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `renamePlan updates the name in the plans list`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Old Name")
            seedPlans(listOf(plan))

            vm.renamePlan(plan, "New Name")
            advanceUntilIdle()

            assertEquals("Plan name should be updated in list", "New Name", vm.plans.value[0].name)
        }

    @Test
    fun `renamePlan also updates selectedPlan`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Old Name")
            seedPlans(listOf(plan))

            vm.renamePlan(plan, "Renamed Plan")
            advanceUntilIdle()

            assertEquals("selectedPlan name should be updated", "Renamed Plan", vm.selectedPlan.value?.name)
        }

    @Test
    fun `renamePlan does not affect other plans in the list`() =
        runTest(dispatcherRule.dispatcher) {
            val plan1 = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan 1")
            val plan2 = MealPlanModel(id = "plan-2", familyId = "fam-1", name = "Plan 2")
            seedPlans(listOf(plan1, plan2))

            vm.renamePlan(plan1, "Plan 1 Renamed")
            advanceUntilIdle()

            val plan2After = vm.plans.value.first { it.id == "plan-2" }
            assertEquals("Plan 2 name should be unchanged", "Plan 2", plan2After.name)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. setPlanIcon — optimistic icon update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setPlanIcon updates the icon in the plans list`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan", icon = "restaurant")
            seedPlans(listOf(plan))

            vm.setPlanIcon(plan, "fastfood")
            advanceUntilIdle()

            assertEquals("Icon should be updated in list", "fastfood", vm.plans.value[0].icon)
        }

    @Test
    fun `setPlanIcon also updates selectedPlan`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan", icon = "restaurant")
            seedPlans(listOf(plan))

            vm.setPlanIcon(plan, "local_pizza")
            advanceUntilIdle()

            assertEquals("selectedPlan icon should be updated", "local_pizza", vm.selectedPlan.value?.icon)
        }

    @Test
    fun `setPlanIcon does not affect other plans`() =
        runTest(dispatcherRule.dispatcher) {
            val plan1 = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan 1", icon = "restaurant")
            val plan2 = MealPlanModel(id = "plan-2", familyId = "fam-1", name = "Plan 2", icon = "restaurant")
            seedPlans(listOf(plan1, plan2))

            vm.setPlanIcon(plan1, "fastfood")
            advanceUntilIdle()

            val plan2After = vm.plans.value.first { it.id == "plan-2" }
            assertEquals("Plan 2 icon should be unchanged", "restaurant", plan2After.icon)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 7b. setPlanColor — optimistic colour update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setPlanColor updates the color in the plans list`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan", color = null)
            seedPlans(listOf(plan))

            vm.setPlanColor(plan, 0x8B5CF6)
            advanceUntilIdle()

            assertEquals("Colour should be updated in list", 0x8B5CF6, vm.plans.value[0].color)
        }

    @Test
    fun `setPlanColor also updates selectedPlan`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan", color = null)
            seedPlans(listOf(plan))

            vm.setPlanColor(plan, 0xF59E0B)
            advanceUntilIdle()

            assertEquals("selectedPlan colour should be updated", 0xF59E0B, vm.selectedPlan.value?.color)
        }

    @Test
    fun `setPlanColor does not affect other plans`() =
        runTest(dispatcherRule.dispatcher) {
            val plan1 = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan 1", color = null)
            val plan2 = MealPlanModel(id = "plan-2", familyId = "fam-1", name = "Plan 2", color = 0x6366F1)
            seedPlans(listOf(plan1, plan2))

            vm.setPlanColor(plan1, 0xEF4444)
            advanceUntilIdle()

            val plan2After = vm.plans.value.first { it.id == "plan-2" }
            assertEquals("Plan 2 colour should be unchanged", 0x6366F1, plan2After.color)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. setFood — optimistic food update in days
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setFood updates the food field for the matching day`() =
        runTest(dispatcherRule.dispatcher) {
            val day =
                MealPlanDayModel(
                    id = "day-1",
                    mealPlanId = "plan-1",
                    day = "Monday",
                    date = "2024-01-01",
                    food = "",
                )
            seedDays(listOf(day))

            vm.setFood(day, "Pizza")
            advanceUntilIdle()

            assertEquals("Food should be updated for the targeted day", "Pizza", vm.days.value[0].food)
        }

    @Test
    fun `setFood trims surrounding whitespace`() =
        runTest(dispatcherRule.dispatcher) {
            val day =
                MealPlanDayModel(
                    id = "day-1",
                    mealPlanId = "plan-1",
                    day = "Monday",
                    date = "2024-01-01",
                    food = "",
                )
            seedDays(listOf(day))

            vm.setFood(day, "  Tacograteng med mais ")
            advanceUntilIdle()

            assertEquals("Food should be trimmed", "Tacograteng med mais", vm.days.value[0].food)
        }

    @Test
    fun `setFood does not modify other days in the list`() =
        runTest(dispatcherRule.dispatcher) {
            val day1 = MealPlanDayModel(id = "day-1", mealPlanId = "plan-1", day = "Monday", date = "2024-01-01", food = "")
            val day2 = MealPlanDayModel(id = "day-2", mealPlanId = "plan-1", day = "Tuesday", date = "2024-01-02", food = "Salad")
            seedDays(listOf(day1, day2))

            vm.setFood(day1, "Burger")
            advanceUntilIdle()

            val day2After = vm.days.value.first { it.id == "day-2" }
            assertEquals("Day 2 food should be unchanged", "Salad", day2After.food)
        }

    @Test
    fun `setFood with empty string clears the day's food`() =
        runTest(dispatcherRule.dispatcher) {
            val day = MealPlanDayModel(id = "day-1", mealPlanId = "plan-1", day = "Monday", date = "2024-01-01", food = "Old Food")
            seedDays(listOf(day))

            vm.setFood(day, "")
            advanceUntilIdle()

            assertEquals("Food should be cleared for the targeted day", "", vm.days.value[0].food)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. familyChanged — triggers reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `familyChanged emission triggers an additional getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            familyChanged.emit(Unit)
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }

    @Test
    fun `familyChanged when familyId is null clears plans`() =
        runTest(dispatcherRule.dispatcher) {
            val plan = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Existing")
            seedPlans(listOf(plan))

            // userId emits → getUser returns null familyId → plans cleared by init collector
            userId.value = "user-1"
            advanceUntilIdle()

            familyChanged.emit(Unit)
            advanceUntilIdle()

            assertTrue(
                "plans should be empty when familyChanged resolves to null familyId",
                vm.plans.value.isEmpty(),
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. refresh() — triggers reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `refresh triggers another getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            vm.refresh()
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }

    @Test
    fun `refresh with null userId is a no-op and does not call getUser`() =
        runTest(dispatcherRule.dispatcher) {
            // userId remains null — refresh should early-return via currentUserId.first()
            vm.refresh()
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Error path — getUser returning null is handled gracefully
    //
    // The ViewModel's init block does not wrap getUser in runCatching. A null
    // return (e.g. user not yet created, transient RLS failure) must not leave
    // the VM in a broken state — plans should remain empty and isLoading false.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getUser returning null leaves plans empty and isLoading false`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser(any()) } returns null

            userId.value = "user-1"
            advanceUntilIdle()

            assertTrue("plans should be empty when getUser returns null", vm.plans.value.isEmpty())
            assertFalse("isLoading should be false when getUser returns null", vm.isLoading.value)
        }

    @Test
    fun `repeated userId toggling with null getUser leaves ViewModel functional`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser(any()) } returns null

            userId.value = "user-1"
            advanceUntilIdle()
            userId.value = null
            advanceUntilIdle()
            userId.value = "user-1"
            advanceUntilIdle()

            // ViewModel is still alive and StateFlows are accessible
            assertTrue("plans remain accessible after repeated userId toggles", vm.plans.value.isEmpty())
            assertNull("selectedPlan remains null after repeated userId toggles", vm.selectedPlan.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. Turbine — plans flow emits expected values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `plans flow emits the optimistic add then rolls it back when insert fails`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle() // init runs with null familyId → no subscription

            coEvery { repo.getUser("user-1") } returns UserModel(id = "user-1", familyId = "fam-1")

            vm.plans.test {
                val initial = awaitItem()
                assertTrue("Initial emission should be empty", initial.isEmpty())

                vm.createPlan("Turbine Plan", "2024-06-01", "2024-06-07", "restaurant")

                // Optimistic add lands first…
                val afterCreate = awaitItem()
                assertTrue(
                    "plans flow should emit the optimistic plan",
                    afterCreate.any { it.name == "Turbine Plan" },
                )

                // …then the failed insert (Supabase client throws in tests) rolls it back.
                advanceUntilIdle()
                val afterRollback = expectMostRecentItem()
                assertTrue(
                    "failed insert should roll the optimistic plan back out",
                    afterRollback.none { it.name == "Turbine Plan" },
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `plans flow emits updated list after deletePlan`() =
        runTest(dispatcherRule.dispatcher) {
            val plan1 = MealPlanModel(id = "plan-1", familyId = "fam-1", name = "Plan 1")
            val plan2 = MealPlanModel(id = "plan-2", familyId = "fam-1", name = "Plan 2")

            vm.plans.test {
                awaitItem() // initial empty emission

                seedPlans(listOf(plan1, plan2))
                val seeded = awaitItem()
                assertEquals("Two plans should be seeded", 2, seeded.size)

                vm.deletePlan(plan1)
                advanceUntilIdle()

                val afterDelete = expectMostRecentItem()
                assertEquals("One plan should remain after delete", 1, afterDelete.size)
                assertEquals("Remaining plan should be Plan 2", "Plan 2", afterDelete[0].name)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
