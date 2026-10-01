package com.sandnes.familyapp.ui.home

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.time.LocalDate

class HomeScreenTest : ComposeScreenTest() {
    private val today: LocalDate = LocalDate.now()

    private fun md(date: LocalDate) = "${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"

    private fun viewModel(
        familyId: String? = "f1",
        summary: Boolean = true,
        fail: Boolean = false,
    ): HomeViewModel {
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/meal_plans",
            if (summary) """[{"id":"p1","family_id":"f1","from_date":"${today.minusDays(1)}","to_date":"${today.plusDays(5)}"}]""" else "[]",
        )
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plan_days", """[{"id":"d1","meal_plan_id":"p1","date":"$today","food":"Tacos"}]""")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/calendar_events",
            if (summary) {
                """[{"id":"e1","user_id":"u1","date_from":"$today","date_to":"$today","activity":"Dentist","color":16711680,"attendee_ids":["u2","u3"]}]"""
            } else {
                "[]"
            },
        )
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/birthdays",
            if (summary) """[{"id":"b1","name":"Near","date":"1985-${md(today.plusDays(2))}"}]""" else "[]",
        )
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_lists", if (summary) """[{"id":"l1","family_id":"f1"}]""" else "[]")
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_items", """[{"id":"i1","list_id":"l1","item":"Milk"}]""")
        if (fail) {
            for (t in listOf("meal_plans", "calendar_events", "birthdays", "shopping_lists")) {
                backend.onJson(HttpMethod.Get, "/rest/v1/$t", "{}", HttpStatusCode.InternalServerError)
            }
        }
        val repo =
            fakeRepo(familyId = familyId) {
                coEvery { getFamily("f1") } returns FamilyModel(id = "f1", name = "The Nordmanns", photoUrl = "https://img.example/f.png")
                coEvery { getFamilyMembers("f1") } returns
                    listOf(
                        UserModel(id = "u1", name = "Ada", avatarColor = 0xFF0000FF.toInt()),
                        UserModel(id = "u2", name = "Bob"),
                        UserModel(id = "u3", name = "Cy"),
                    )
            }
        return HomeViewModel(repo)
    }

    @Test
    fun `dashboard shows greeting family summary and feature tiles that navigate`() {
        val opened = mutableListOf<String>()
        var family = 0
        val vm = viewModel()
        compose.setContent { HomeScreen(onOpen = { opened += it }, onOpenFamily = { family++ }, viewModel = vm) }
        compose.waitForText("The Nordmanns")
        compose.waitForText("Tacos")
        assertTrue(compose.hasText("Dentist"))
        assertTrue(compose.hasText("Near"))
        assertTrue(compose.hasText(str(R.string.count_left_to_buy, 1)))
        compose.clickText("The Nordmanns")
        assertEquals(1, family)
        compose.clickText("Tacos")
        compose.clickText("Dentist")
        compose.clickText("Near")
        compose.clickText(str(R.string.count_left_to_buy, 1))
        for (feature in listOf(R.string.shopping, R.string.meals, R.string.calendar, R.string.birthdays, R.string.wishlists, R.string.family_map)) {
            compose.clickDescription(str(R.string.feature_named, str(feature)))
        }
        assertEquals(10, opened.size)
    }

    @Test
    fun `without a family a banner invites the user to get started`() {
        var family = 0
        val vm = viewModel(familyId = null, summary = false)
        compose.setContent { HomeScreen(onOpen = {}, onOpenFamily = { family++ }, viewModel = vm) }
        compose.waitForText(str(R.string.no_family_yet))
        assertFalse(compose.hasText(str(R.string.quick_access)))
        compose.clickText(str(R.string.get_started))
        assertEquals(1, family)
    }

    @Test
    fun `a load error shows the banner but keeps the feature grid`() {
        val vm = viewModel(summary = false, fail = true)
        compose.setContent { HomeScreen(onOpen = {}, onOpenFamily = {}, viewModel = vm) }
        compose.waitForText(str(R.string.shopping))
        assertTrue(compose.hasText(str(R.string.meals)))
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp-land-mdpi")
    fun `tablet landscape uses the wide layout`() {
        val vm = viewModel()
        compose.setContent { HomeScreen(onOpen = {}, onOpenFamily = {}, viewModel = vm) }
        compose.waitForText("The Nordmanns")
    }

    @Test
    @Config(qualifiers = "w420dp-h800dp-land-mdpi")
    fun `phone landscape uses the compact tiles`() {
        val vm = viewModel()
        compose.setContent { HomeScreen(onOpen = {}, onOpenFamily = {}, viewModel = vm) }
        compose.waitForText("The Nordmanns")
    }

    @Test
    @Config(qualifiers = "w800dp-h1200dp-mdpi")
    fun `tablet portrait`() {
        val vm = viewModel()
        compose.setContent { HomeScreen(onOpen = {}, onOpenFamily = {}, viewModel = vm) }
        compose.waitForText("The Nordmanns")
    }
}
