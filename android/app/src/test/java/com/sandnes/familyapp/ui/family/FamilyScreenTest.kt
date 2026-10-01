package com.sandnes.familyapp.ui.family

import android.app.Application
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyModel
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.appContext
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.runSwipeDelete
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyScreenTest : ComposeScreenTest() {
    private val members =
        """[{"id":"u1","name":"Ada","email":"ada@example.com","mobile":"123","family_id":"f1","avatar_color":-16711936},
            {"id":"u2","name":"Bob","family_id":"f1"},
            {"id":"u3","name":"Cy","family_id":"f1"},
            {"id":"u4","name":"Di","family_id":"f1"},
            {"id":"u5","name":"Ed","family_id":"f1"},
            {"id":"u6","name":"Flo","family_id":"f1"}]"""

    private fun viewModel(
        familyId: String? = "f1",
        adminId: String? = "u1",
        pendingCode: String? = null,
    ): Pair<FamilyViewModel, com.sandnes.familyapp.data.FamilyRepository> {
        backend.onJson(HttpMethod.Get, "/rest/v1/users", members)
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/family_relations",
            """[{"id":"r1","family_id":"f1","from_user_id":"u1","to_user_id":"u2","relation":"Brother"}]""",
        )
        val repo =
            fakeRepo(familyId = familyId) {
                every { pendingJoinCode } returns MutableStateFlow(pendingCode)
                coEvery { getFamily("f1") } returns FamilyModel(id = "f1", name = "The Nordmanns", joinCode = "ABCD1234", adminId = adminId)
                coEvery { createFamily(any(), any(), any()) } returns Result.success("f1")
                coEvery { joinFamily(any(), any()) } returns Result.success("f1")
                coEvery { removeFamilyMember(any()) } returns Result.success(Unit)
            }
        return FamilyViewModel(appContext as Application, repo) to repo
    }

    @Test
    fun `without a family the screen offers create and join`() {
        val (vm, repo) = viewModel(familyId = null)
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText(str(R.string.bring_your_family_together))
        compose.clickText(str(R.string.create_a_family), index = 0)
        compose.waitForText(str(R.string.family_name))
        compose.clickText(str(R.string.cancel))
        compose.clickText(str(R.string.join_with_invite_code))
        compose.waitForText(str(R.string.invite_code))
        compose.typeInto("WXYZ")
        compose.clickText(str(R.string.join))
        coVerify(timeout = 20_000) { repo.joinFamily("WXYZ", "u1") }
    }

    @Test
    fun `creating a family needs a name`() {
        val (vm, repo) = viewModel(familyId = null)
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText(str(R.string.bring_your_family_together))
        compose.clickText(str(R.string.create_a_family), index = 0)
        compose.waitForText(str(R.string.family_name))
        compose.typeInto("Hansens")
        compose.clickText(str(R.string.create))
        coVerify(timeout = 20_000) { repo.createFamily("Hansens", any(), "u1") }
    }

    @Test
    fun `a pending invite code opens the join dialog prefilled`() {
        val (vm, _) = viewModel(familyId = null, pendingCode = "PRE123")
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText(str(R.string.join_a_family))
        assertTrue(compose.hasText("PRE123"))
    }

    @Test
    fun `a failed join shows the error banner`() {
        val (vm, repo) = viewModel(familyId = null)
        coEvery { repo.joinFamily(any(), any()) } returns Result.failure(IllegalStateException("Bad code"))
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText(str(R.string.bring_your_family_together))
        compose.clickText(str(R.string.join_with_invite_code))
        compose.typeInto("NOPE")
        compose.clickText(str(R.string.join))
        compose.waitForText("Bad code", substring = true)
    }

    @Test
    fun `admin sees the header members and can open the invite tools`() {
        val (vm, _) = viewModel()
        compose.setContent { FamilyScreen(onBack = {}, viewModel = vm) }
        compose.waitForText("The Nordmanns")
        assertTrue(compose.hasText(str(R.string.member_count, 6)))
        assertTrue(compose.hasText("+2"))
        assertTrue(compose.hasText("Brother"))
        compose.clickText(str(R.string.share_invite))
        compose.clickText(str(R.string.qr_code))
        compose.waitForText(str(R.string.scan_to_join))
        compose.clickText(str(R.string.done))
    }

    @Test
    fun `admin menu offers a family photo change`() {
        val (vm, _) = viewModel()
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("The Nordmanns")
        compose.clickDescription(str(R.string.more_options))
        compose.clickText(str(R.string.change_family_photo))
        compose.clickDescription(str(R.string.change_family_photo))
    }

    @Test
    fun `non admins get no admin tools`() {
        val (vm, _) = viewModel(adminId = "u2")
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("The Nordmanns")
        assertFalse(compose.hasText(str(R.string.change_family_photo)))
        assertTrue(compose.hasText(str(R.string.admin)))
    }

    @Test
    fun `member profile sheet shows details and sets a relation`() {
        val (vm, repo) = viewModel()
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("Cy")
        compose.clickText("Cy")
        compose.waitForText(str(R.string.your_relation))
        assertTrue(compose.hasText(str(R.string.not_set)))
        compose.clickText(str(R.string.set_relation))
        compose.clickText(str(R.string.relation_cousin))
        compose.waitUntil(20_000) { backend.requestsTo("family_relations", HttpMethod.Post).isNotEmpty() }
        assertTrue(
            backend
                .requestsTo("family_relations", HttpMethod.Post)
                .first()
                .body
                .contains("Cousin"),
        )
    }

    @Test
    fun `an existing relation can be cleared from the profile sheet`() {
        val (vm, _) = viewModel()
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("Bob")
        compose.clickText("Bob")
        compose.waitForText(str(R.string.your_relation))
        compose.clickText("Brother", index = 1)
        compose.clickText(str(R.string.none))
        compose.waitUntil(20_000) { backend.requestsTo("family_relations").size >= 2 }
    }

    @Test
    fun `own profile has no relation row`() {
        val (vm, _) = viewModel()
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("Ada")
        compose.clickText("Ada")
        compose.waitForText("ada@example.com")
        assertFalse(compose.hasText(str(R.string.your_relation)))
    }

    @Test
    fun `admin can remove a member after confirming`() {
        val (vm, repo) = viewModel()
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("Cy")
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.remove_member_q))
        compose.clickText(str(R.string.cancel))
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.remove_member_q))
        compose.clickText(str(R.string.remove))
        coVerify(timeout = 20_000) { repo.removeFamilyMember(any()) }
    }

    @Test
    fun `leaving the family is confirmed`() {
        val (vm, repo) = viewModel()
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText("The Nordmanns")
        compose.clickText(str(R.string.leave_family))
        compose.waitForText(str(R.string.leave_family_q))
        compose.clickText(str(R.string.cancel))
        compose.clickText(str(R.string.leave_family))
        compose.clickText(str(R.string.leave))
        coVerify(timeout = 20_000) { repo.leaveFamily("u1") }
    }

    @Test
    fun `joining a family with members prompts to set relations`() {
        val (vm, repo) = viewModel(familyId = null)
        compose.setContent { FamilyScreen(viewModel = vm) }
        compose.waitForText(str(R.string.bring_your_family_together))
        // After joining, the repository now reports the family.
        coEvery { repo.getUser("u1") } returns
            com.sandnes.familyapp.data
                .UserModel(id = "u1", name = "Ada", familyId = "f1")
        compose.clickText(str(R.string.join_with_invite_code))
        compose.typeInto("GOOD")
        compose.clickText(str(R.string.join))
        compose.waitForText(str(R.string.set_your_relations))
        compose.clickText(str(R.string.set_relation), index = 0)
        compose.clickText(str(R.string.relation_friend))
        compose.clickText(str(R.string.done))
    }
}
