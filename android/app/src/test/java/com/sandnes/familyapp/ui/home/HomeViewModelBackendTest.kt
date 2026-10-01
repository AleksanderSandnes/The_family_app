package com.sandnes.familyapp.ui.home

import com.sandnes.familyapp.data.FamilyModel
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDate

/** HomeViewModel's dashboard summary against a fake Supabase backend. */
class HomeViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val familyChanged = MutableSharedFlow<Unit>()
    private val today: LocalDate = LocalDate.now()

    private val ada = UserModel(id = "u1", name = "Ada", familyId = "f1")

    @Before
    fun setUp() {
        backend.install()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        coEvery { repo.getUser("u1") } returns ada
        coEvery { repo.getFamily("f1") } returns FamilyModel(id = "f1", name = "Fam")
        coEvery { repo.getFamilyMembers("f1") } returns listOf(ada, UserModel(id = "u2", name = "Bob"))
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plans", """[{"id":"p1","family_id":"f1","from_date":"${today.minusDays(1)}","to_date":"${today.plusDays(5)}"}]""")
        backend.onJson(HttpMethod.Get, "/rest/v1/meal_plan_days", """[{"id":"d1","meal_plan_id":"p1","date":"$today","food":"Tacos"}]""")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/calendar_events",
            """[{"id":"e1","date_from":"${today.plusDays(3)}","date_to":"${today.plusDays(3)}","activity":"Later"},
                {"id":"e2","date_from":"${today.plusDays(1)}","date_to":"${today.plusDays(1)}","activity":"Soon"},
                {"id":"e0","date_from":"${today.minusDays(2)}","date_to":"${today.minusDays(2)}","activity":"Over"}]""",
        )
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/birthdays",
            """[{"id":"b1","name":"Far","date":"1990-${today.plusDays(40).monthValue.toString().padStart(2, '0')}-${today.plusDays(40).dayOfMonth.toString().padStart(2, '0')}"},
                {"id":"b2","name":"Near","date":"1985-${today.plusDays(2).monthValue.toString().padStart(2, '0')}-${today.plusDays(2).dayOfMonth.toString().padStart(2, '0')}"}]""",
        )
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_lists", """[{"id":"l1","family_id":"f1"}]""")
        backend.onJson(HttpMethod.Get, "/rest/v1/shopping_items", """[{"id":"i1","list_id":"l1","item":"Milk"},{"id":"i2","list_id":"l1","item":"Eggs"}]""")
    }

    @After
    fun tearDown() = backend.uninstall()

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun loaded(): HomeViewModel {
        val vm = HomeViewModel(repo)
        userId.value = "u1"
        settle { !vm.state.value.isLoading && vm.state.value.user != null }
        return vm
    }

    @Test
    fun `the dashboard summarises tonight's meal next event birthday and shopping`() =
        runTest(dispatcherRule.dispatcher) {
            val state = loaded().state.value
            assertEquals("Ada", state.user?.name)
            assertEquals("Fam", state.family?.name)
            assertEquals(2, state.memberCount)
            assertEquals("Tacos", state.tonightMeal)
            assertEquals("Soon", state.nextEvent?.activity)
            assertEquals("Near", state.nextBirthday?.name)
            assertEquals(today.plusDays(2), state.nextBirthdayDate)
            assertEquals(2, state.shoppingRemaining)
            assertTrue(state.hasSummary)
            assertFalse(state.loadError)
        }

    @Test
    fun `summary parts fall back independently when their queries fail`() =
        runTest(dispatcherRule.dispatcher) {
            for (table in listOf("meal_plans", "calendar_events", "birthdays", "shopping_lists")) {
                backend.onJson(HttpMethod.Get, "/rest/v1/$table", "{}", HttpStatusCode.InternalServerError)
            }
            val state = loaded().state.value
            assertNull(state.tonightMeal)
            assertNull(state.nextEvent)
            assertNull(state.nextBirthday)
            assertEquals(0, state.shoppingRemaining)
            assertFalse(state.hasSummary)
            assertEquals("Ada", state.user?.name)
        }

    @Test
    fun `no active meal plan and blank food yield no tonight meal`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/meal_plans", """[{"id":"p1","from_date":"${today.plusDays(2)}","to_date":"${today.plusDays(4)}"},{"id":"p2","from_date":"bad","to_date":"bad"}]""")
            assertNull(loaded().state.value.tonightMeal)
            backend.onJson(HttpMethod.Get, "/rest/v1/meal_plans", """[{"id":"p1","from_date":"${today.minusDays(1)}","to_date":"${today.plusDays(1)}"}]""")
            backend.onJson(HttpMethod.Get, "/rest/v1/meal_plan_days", """[{"id":"d1","food":"  "}]""")
            val vm = loaded()
            vm.refresh()
            settle { !vm.state.value.isLoading }
            assertNull(vm.state.value.tonightMeal)
        }

    @Test
    fun `empty shopping lists count nothing remaining`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/shopping_lists", "[]")
            assertEquals(0, loaded().state.value.shoppingRemaining)
        }

    @Test
    fun `a user without a family skips the network summary`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = null)
            val state = loaded().state.value
            assertNull(state.family)
            assertEquals(0, state.memberCount)
            assertTrue(backend.requests.isEmpty())
        }

    @Test
    fun `a missing profile is a load error and sign out resets the state`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns null
            val vm = HomeViewModel(repo)
            userId.value = "u1"
            settle { vm.state.value.loadError }
            assertFalse(vm.state.value.isLoading)
            coEvery { repo.getUser("u1") } returns ada
            userId.value = null
            settle { !vm.state.value.loadError && vm.state.value.user == null && !vm.state.value.isLoading }
        }

    @Test
    fun `an unexpected failure while loading is reported`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } throws IllegalStateException("boom")
            val vm = HomeViewModel(repo)
            userId.value = "u1"
            settle { vm.state.value.loadError && !vm.state.value.isLoading }
        }

    @Test
    fun `refresh and family changes reload the dashboard`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("meal_plans", HttpMethod.Get).size
            vm.refresh()
            settle { backend.requestsTo("meal_plans", HttpMethod.Get).size > before }
            val afterRefresh = backend.requestsTo("meal_plans", HttpMethod.Get).size
            familyChanged.emit(Unit)
            settle { backend.requestsTo("meal_plans", HttpMethod.Get).size > afterRefresh }
            userId.value = null
            settle { vm.state.value.user == null }
            vm.refresh()
            familyChanged.emit(Unit)
        }
}
