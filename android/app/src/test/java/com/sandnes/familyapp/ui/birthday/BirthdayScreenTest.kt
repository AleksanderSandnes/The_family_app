package com.sandnes.familyapp.ui.birthday

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp-mdpi")
class BirthdayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val backend = FakeSupabase()
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        backend.install()
        val cache = BirthdayViewModel::class.java.getDeclaredField("cache")
        cache.isAccessible = true
        cache.set(null, emptyList<Any?>())
    }

    @After
    fun tearDown() {
        backend.uninstall()
    }

    private fun viewModel(json: String): BirthdayViewModel {
        val repo = mockk<FamilyRepository>(relaxed = true)
        every { repo.currentUserId } returns MutableStateFlow("u1")
        every { repo.familyChanged } returns MutableSharedFlow()
        coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = "f1")
        backend.onJson(HttpMethod.Get, "/rest/v1/birthdays", json)
        return BirthdayViewModel(repo)
    }

    private fun birthday(
        id: String,
        name: String,
        date: LocalDate,
        owner: String,
    ) = """{"id":"$id","name":"$name","date":"$date","family_id":"f1","made_by_user_id":"$owner"}"""

    @Test
    fun `shows the empty state and adds a birthday from it`() {
        val vm = viewModel("[]")
        compose.setContent { BirthdayScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_birthdays))
        compose.clickText(str(R.string.add_birthday))
        compose.waitForText(str(R.string.icon).uppercase())
        // Confirm is disabled until a name and date exist.
        compose.onNodeWithText(str(R.string.add)).assertIsNotEnabled()
        compose.typeInto("Zed")
        compose.clickDescription(str(R.string.birthday))
        compose.clickText(str(R.string.ok))
        compose.clickText(str(R.string.add))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/birthdays", HttpMethod.Post).isNotEmpty() }
        assertTrue(
            backend
                .requestsTo("/rest/v1/birthdays", HttpMethod.Post)
                .single()
                .body
                .contains("Zed"),
        )
    }

    @Test
    fun `cancelling the add sheet keeps the list unchanged`() {
        val vm = viewModel("[]")
        compose.setContent { BirthdayScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_birthdays))
        compose.clickText(str(R.string.add_birthday))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.clickText(str(R.string.cancel))
        assertTrue(backend.requestsTo("/rest/v1/birthdays", HttpMethod.Post).isEmpty())
    }

    @Test
    fun `lists birthdays with urgency pills and edits an owned one`() {
        val vm =
            viewModel(
                "[" +
                    birthday("b1", "Todd", today.minusYears(30), "u1") + "," +
                    birthday("b2", "Soon", today.plusDays(3).minusYears(20), "u2") + "," +
                    birthday("b3", "Later", today.plusDays(60).minusYears(10), "u2") + "," +
                    """{"id":"b4","name":"Nodate","date":"not-a-date","family_id":"f1","made_by_user_id":"u2"}""" +
                    "]",
            )
        compose.setContent { BirthdayScreen(onBack = {}, viewModel = vm) }
        compose.waitForText("Todd")
        assertTrue(compose.hasText(str(R.string.today_exclaim) + " 🎉"))
        assertTrue(compose.hasText(str(R.string.in_days, 3)))
        assertTrue(compose.hasText("Nodate"))
        // Owned card opens the edit sheet; change the name and save.
        compose.clickText("Todd")
        compose.waitForText(str(R.string.edit_birthday))
        compose.typeInto("Tod2")
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/birthdays", HttpMethod.Patch).isNotEmpty() }
        assertTrue(
            backend
                .requestsTo("/rest/v1/birthdays", HttpMethod.Patch)
                .single()
                .body
                .contains("Tod2"),
        )
    }

    @Test
    fun `deleting an owned birthday asks first and cancel keeps it`() {
        val vm = viewModel("[" + birthday("b1", "Todd", today.minusYears(30), "u1") + "]")
        compose.setContent { BirthdayScreen(onBack = {}, viewModel = vm) }
        compose.waitForText("Todd")
        val row = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
        compose.runOnUiThread {
            compose
                .onAllNodes(row)[0]
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .first()
                .action()
        }
        compose.waitForText(str(R.string.delete_birthday_q))
        compose.clickText(str(R.string.cancel))
        assertTrue(backend.requestsTo("/rest/v1/birthdays", HttpMethod.Delete).isEmpty())
        compose.runOnUiThread {
            compose
                .onAllNodes(row)[0]
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .first()
                .action()
        }
        compose.waitForText(str(R.string.delete_birthday_q))
        compose.clickText(str(R.string.delete))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/birthdays", HttpMethod.Delete).isNotEmpty() }
    }

    @Test
    fun `shows the error snackbar when a write fails`() {
        val vm = viewModel("[]")
        backend.onJson(HttpMethod.Post, "/rest/v1/birthdays", "{}", io.ktor.http.HttpStatusCode.InternalServerError)
        compose.setContent { BirthdayScreen(onBack = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_birthdays))
        compose.clickText(str(R.string.add_birthday))
        compose.waitForText(str(R.string.icon).uppercase())
        compose.typeInto("Zed")
        compose.clickDescription(str(R.string.birthday))
        compose.clickText(str(R.string.ok))
        compose.clickText(str(R.string.add))
        compose.waitUntil(20_000) { compose.onAllNodesWithText(str(R.string.no_birthdays)).fetchSemanticsNodes().isNotEmpty() }
    }
}
