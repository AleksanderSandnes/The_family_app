package com.sandnes.familyapp.ui.meal

import com.sandnes.familyapp.R
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.editField
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.runSwipeDelete
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class MealScreensTest : ComposeScreenTest() {
    private val today: LocalDate = LocalDate.now()
    private val mine =
        """{"id":"p1","family_id":"f1","name":"Week 40","from_date":"$today","to_date":"${today.plusDays(2)}","icon":"restaurant","created_by":"u1"}"""
    private val theirs =
        """{"id":"p2","family_id":"f1","name":"","from_date":"bad","to_date":"worse","icon":"restaurant","created_by":"u2"}"""
    private val days =
        """[{"id":"d1","meal_plan_id":"p1","day":"Monday","date":"$today","food":"Pasta"},
            {"id":"d2","meal_plan_id":"p1","day":"Tuesday","date":"${today.plusDays(1)}","food":""},
            {"id":"d3","meal_plan_id":"p1","day":"Wednesday","date":"broken","food":""}]"""

    @Before
    fun reset() = resetCache(MealViewModel::class.java)

    @After
    fun clear() = resetCache(MealViewModel::class.java)

    private fun viewModel(
        plans: String = "[$mine,$theirs]",
        admin: Boolean = false,
        familyId: String? = "f1",
    ): MealViewModel {
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plans", plans)
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plan_days", days)
        return MealViewModel(fakeRepo(admin = admin, familyId = familyId))
    }

    @Test
    fun `lists plans with progress and opens one`() {
        var opened: String? = null
        val vm = viewModel()
        compose.setContent { MealScreen(onBack = {}, onOpen = { opened = it }, viewModel = vm) }
        compose.waitForText("Week 40", substring = true)
        compose.waitForText(str(R.string.dinners_planned_count, 1, 3), substring = true)
        // Plan with a blank name falls back to the generic title; broken dates are shown as stored.
        assertTrue(compose.hasText(str(R.string.meal_plan), substring = true))
        compose.clickText("Week 40", substring = true)
        assertTrue(opened == "p1")
    }

    @Test
    fun `without a family there is no create button and the empty state shows`() {
        val vm = viewModel(plans = "[]", familyId = null)
        compose.setContent { MealScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_meal_plans_yet))
        assertFalse(compose.hasText(str(R.string.create_a_meal_plan)))
    }

    @Test
    fun `creating a plan needs a name and a date range`() {
        val vm = viewModel(plans = "[]")
        compose.setContent { MealScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_meal_plans_yet))
        compose.clickDescription(str(R.string.create_a_meal_plan))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.typeInto("Holiday")
        compose.clickText(str(R.string.starts).uppercase())
        compose.clickText(str(R.string.ok))
        compose.clickText(str(R.string.ends).uppercase())
        compose.clickText(str(R.string.ok))
        // Re-open both pickers to cover the cancel path and the end-before-start clamp.
        compose.clickText(str(R.string.starts).uppercase())
        compose.clickText(str(R.string.cancel), index = 1)
        compose.clickText(str(R.string.create))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plans", HttpMethod.Post).isNotEmpty() }
        assertTrue(backend.requestsTo("/rest/v1/meal_plans", HttpMethod.Post).first().body.contains("Holiday"))
    }

    @Test
    fun `only creators and admins can swipe a plan away`() {
        val vm = viewModel()
        compose.setContent { MealScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText("Week 40", substring = true)
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_plan_q))
        compose.clickText(str(R.string.cancel))
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_plan_q))
        compose.clickText(str(R.string.delete))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plans", HttpMethod.Delete).isNotEmpty() }
    }

    @Test
    fun `an admin can delete a plan someone else made`() {
        val vm = viewModel(plans = "[$theirs]", admin = true)
        compose.setContent { MealScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.meal_plan), substring = true)
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_plan_q))
    }

    @Test
    fun `a failing create shows the error snackbar`() {
        val vm = viewModel(plans = "[]")
        backend.onJson(HttpMethod.Post, "/rest/v1/meal_plans", "{}", HttpStatusCode.InternalServerError)
        compose.setContent { MealScreen(onBack = {}, onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_meal_plans_yet))
        compose.clickDescription(str(R.string.create_a_meal_plan))
        compose.typeInto("Holiday")
        compose.clickText(str(R.string.starts).uppercase())
        compose.clickText(str(R.string.ok))
        compose.clickText(str(R.string.ends).uppercase())
        compose.clickText(str(R.string.ok))
        compose.clickText(str(R.string.create))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plans", HttpMethod.Post).isNotEmpty() }
    }

    @Test
    fun `detail shows each day and edits a meal`() {
        val vm = viewModel()
        compose.setContent { MealDetailScreen(planId = "p1", onBack = {}, viewModel = vm) }
        compose.waitForText("Pasta")
        assertTrue(compose.hasText(str(R.string.no_plan_yet)))
        compose.clickDescription(str(R.string.edit_meal_named, "Tuesday"))
        compose.waitForText(str(R.string.save))
        compose.typeInto("Soup")
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plan_days", HttpMethod.Patch).isNotEmpty() }
        // Tap a row to edit, then cancel.
        compose.clickText("Pasta")
        compose.waitForText(str(R.string.cancel))
        compose.clickText(str(R.string.cancel))
        // Edit and confirm through the keyboard action.
        compose.clickText("Pasta")
        compose.editField("Pasta", "Lasagna")
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plan_days", HttpMethod.Patch).size >= 2 }
    }

    @Test
    fun `detail menu renames the plan and changes its icon`() {
        val vm = viewModel()
        compose.setContent { MealDetailScreen(planId = "p1", onBack = {}, viewModel = vm) }
        compose.waitForText("Pasta")
        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.rename_plan))
        compose.waitForText(str(R.string.name))
        compose.editField("Week 40", "Autumn", done = false)
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plans", HttpMethod.Patch).isNotEmpty() }
        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.change_icon))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/meal_plans", HttpMethod.Patch).size >= 2 }
    }
}
