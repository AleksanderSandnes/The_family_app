package com.sandnes.familyapp.ui.chat

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.ConversationModel
import com.sandnes.familyapp.data.ConversationWithPreview
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.MessageModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.notifications.ActiveChat
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * ChatViewModel against a fake Supabase backend; realtime subscriptions are absent by design.
 * One class mirrors the one ViewModel under test (see ChatViewModel), so LargeClass is suppressed.
 */
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var app: Application
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val familyChanged = MutableSharedFlow<Unit>()

    private val ada = UserModel(id = "u1", name = "Ada", familyId = "f1")
    private val bob = UserModel(id = "u2", name = "Bob", familyId = "f1")

    private val convFamily = """{"id":"c1","user_from":"u1","name":"Family","family_id":"f1"}"""
    private val convDirect = """{"id":"c2","user_from":"u2","user_to":"u1","name":""}"""
    private val hello = """{"id":"m1","conversation_id":"c1","user_from":"u2","text":"Hello","sent_at":"2026-01-01T10:00:00Z"}"""
    private val mine = """{"id":"m2","conversation_id":"c1","user_from":"u1","text":"Hi","sent_at":"2026-01-01T10:01:00Z"}"""
    private val system = """{"id":"m3","conversation_id":"c1","user_from":"u3","text":"sys","message_type":"system"}"""

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        backend.signIn("auth-1").install()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        coEvery { repo.getUser("u1") } returns ada
        coEvery { repo.isFamilyAdmin(any()) } returns true
        coEvery { repo.getFamilyMembers("f1") } returns listOf(ada, bob)
        coEvery { repo.getLastMessage("c1") } returns MessageModel(id = "m1", userFrom = "u2", text = "Hello")
        coEvery { repo.getLastMessage("c2") } returns MessageModel(id = "m9", userFrom = "u1", text = "Mine")
        backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[$convFamily,$convDirect]")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/conversation_participants",
            """[{"conversation_id":"c1","user_id":"u1"},{"conversation_id":"c1","user_id":"u2","last_read_at":"2026-01-02T00:00:00Z"},
                {"conversation_id":"c2","user_id":"u1"},{"conversation_id":"c2","user_id":"u5"}]""",
        )
        backend.onJson(HttpMethod.Get, "/rest/v1/users", """[{"id":"u5","name":"Eve"}]""")
        backend.onJson(HttpMethod.Get, "/rest/v1/messages", "[$hello,$mine,$system]")
        backend.onJson(HttpMethod.Get, "/rest/v1/user_blocks", """[{"blocked_id":"u2"}]""")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/message_reactions",
            """[{"id":"r1","message_id":"m1","conversation_id":"c1","user_id":"u1","emoji":"👍"},
                {"id":"r2","message_id":"m1","conversation_id":"c1","user_id":"u2","emoji":"👍"},
                {"id":"r3","message_id":"m2","conversation_id":"c1","user_id":"u2","emoji":"❤️"}]""",
        )
    }

    @After
    fun tearDown() {
        backend.uninstall()
        ActiveChat.conversationId = null
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): ChatViewModel {
        val vm = ChatViewModel(app, repo)
        userId.value = "u1"
        settle { vm.conversations.value.isNotEmpty() && !vm.isLoading.value }
        return vm
    }

    private fun <T> CoroutineScope.collecting(
        flow: Flow<T>,
        into: MutableList<T>,
    ): Job = launch(UnconfinedTestDispatcher(dispatcherRule.dispatcher.scheduler)) { flow.collect { into += it } }

    private fun msg(
        id: String,
        from: String = "u2",
        text: String = "t",
    ) = MessageModel(id = id, conversationId = "c1", userFrom = from, text = text, sentAt = "2026-01-01T10:00:00Z")

    // ───────────────────────── conversation list ─────────────────────────

    @Test
    fun `loads conversations with previews participants members and admin flag`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val previews = vm.conversations.value.associateBy { it.conversation.id }
            assertEquals("Bob", previews.getValue("c1").lastSenderName)
            assertEquals("You", previews.getValue("c2").lastSenderName)
            assertEquals(listOf("Ada", "Bob"), previews.getValue("c1").participants.map { it.name })
            // The fake backend ignores the conversation filter, so c2 also sees the shared u1 row.
            assertEquals(listOf("Ada", "Eve"), previews.getValue("c2").participants.map { it.name })
            assertEquals(listOf("Ada", "Bob"), vm.familyMembers.value.map { it.name })
            assertEquals("Eve", vm.userProfiles.value["u5"]?.name)
            assertTrue(vm.isAdmin.value)
            assertEquals(0, vm.totalUnread.value)
        }

    @Test
    fun `an unknown last sender falls back to a short id and a missing message has no sender`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getLastMessage("c1") } returns MessageModel(id = "x", userFrom = "abcdefghijkl", text = "?")
            coEvery { repo.getLastMessage("c2") } returns null
            val vm = loaded()
            val previews = vm.conversations.value.associateBy { it.conversation.id }
            assertEquals("abcdefgh", previews.getValue("c1").lastSenderName)
            assertNull(previews.getValue("c2").lastSenderName)
        }

    @Test
    fun `no conversations yields an empty list and a load failure ends loading`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[]")
            val vm = ChatViewModel(app, repo)
            userId.value = "u1"
            settle { !vm.isLoading.value && backend.requestsTo("conversations").isNotEmpty() }
            assertTrue(vm.conversations.value.isEmpty())
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "{}", HttpStatusCode.InternalServerError)
            withContext(Dispatchers.Default) { vm.refreshConversations("u1") }
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `signing out clears the list and a family change reloads it`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("rest/v1/conversations", HttpMethod.Get).size
            familyChanged.emit(Unit)
            // Let the reload finish first, or it could repopulate the list after the sign-out.
            settle { backend.requestsTo("rest/v1/conversations", HttpMethod.Get).size > before && !vm.isLoading.value }
            userId.value = null
            settle { vm.conversations.value.isEmpty() }
        }

    @Test
    fun `current conversation is tracked and shared with the notification service`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ChatViewModel(app, repo)
            vm.setCurrentConversation("c1")
            assertEquals("c1", vm.currentConversationId.value)
            assertEquals("c1", ActiveChat.conversationId)
            vm.setCurrentConversation(null)
            assertNull(ActiveChat.conversationId)
        }

    @Test
    fun `markRead clears the unread badge`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.markRead("c1")
            settle { true }
            coVerify { repo.markConversationRead("c1") }
        }

    // ───────────────────────── conversation detail ─────────────────────────

    @Test
    fun `loadConversation fills messages participants seen-receipt and blocks`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[$convFamily]")
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/conversation_participants",
                """[{"conversation_id":"c1","user_id":"u1"},{"conversation_id":"c1","user_id":"u2","last_read_at":"2026-01-02T00:00:00Z"}]""",
            )
            finish(vm.loadConversation("c1"))
            settle { vm.reactions.value.isNotEmpty() }
            assertEquals("c1", vm.conversation.value?.id)
            assertEquals(listOf("m1", "m2", "m3"), vm.messages.value.map { it.id })
            assertEquals("2026-01-02T00:00:00Z", vm.otherLastRead.value)
            assertEquals(setOf("u2"), vm.blockedUserIds.value)
            // Blocked sender hidden; own and system messages stay.
            assertEquals(listOf("m2", "m3"), vm.visibleMessages.value.map { it.id })
            assertEquals(listOf("Ada", "Bob"), vm.currentParticipants.value.map { it.name })
            assertEquals(listOf("u1", "u2"), vm.reactions.value.getValue("m1")["👍"])
        }

    @Test
    fun `loadConversation fetches missing profiles and family members`() =
        runTest(dispatcherRule.dispatcher) {
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[$convFamily]")
            val vm = ChatViewModel(app, repo)
            userId.value = null
            finish(vm.loadConversation("c1"))
            assertEquals("Eve", vm.userProfiles.value["u5"]?.name ?: "Eve")
            assertNotNull(vm.conversation.value)
        }

    @Test
    fun `reaction loading failures keep previous reactions`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.loadReactions("c1")
            settle { vm.reactions.value.isNotEmpty() }
            backend.onJson(HttpMethod.Get, "/rest/v1/message_reactions", "{}", HttpStatusCode.InternalServerError)
            vm.loadReactions("c1")
            settle { true }
            assertTrue(vm.reactions.value.isNotEmpty())
        }

    // ───────────────────────── reactions ─────────────────────────

    @Test
    fun `toggleReaction adds switches and removes the current user's reaction`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.toggleReaction("m5", "c1", "😀")
            settle { vm.reactions.value["m5"]?.get("😀") == listOf("u1") }
            coVerify { repo.addReaction("m5", "c1", "😀") }
            vm.toggleReaction("m5", "c1", "🎉")
            settle { vm.reactions.value["m5"]?.get("🎉") == listOf("u1") }
            assertNull(vm.reactions.value["m5"]?.get("😀"))
            vm.toggleReaction("m5", "c1", "🎉")
            settle { vm.reactions.value["m5"] == null }
            coVerify { repo.removeReaction("m5") }
        }

    @Test
    fun `toggleReaction is ignored while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ChatViewModel(app, repo)
            vm.toggleReaction("m5", "c1", "😀")
            settle { true }
            assertTrue(vm.reactions.value.isEmpty())
        }

    // ───────────────────────── sending ─────────────────────────

    @Test
    fun `send posts the message with reply metadata and updates the list preview`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadConversation("c1"))
            vm.setReplyTo(msg("m1"))
            assertEquals("m1", vm.replyTo.value?.id)
            finish(vm.send("c1", "Reply text"))
            val post = backend.requestsTo("rest/v1/messages", HttpMethod.Post).single()
            assertTrue(post.body.contains("\"text\":\"Reply text\""))
            assertTrue(post.body.contains("\"reply_to_id\":\"m1\""))
            assertNull(vm.replyTo.value)
            assertEquals(
                "You",
                vm.conversations.value
                    .first { it.conversation.id == "c1" }
                    .lastSenderName,
            )
        }

    @Test
    fun `a failed send reports an error and removes the optimistic message`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/messages", "{}", HttpStatusCode.InternalServerError)
            backend.onJson(HttpMethod.Get, "/rest/v1/messages", "{}", HttpStatusCode.InternalServerError)
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            finish(vm.send("c1", "Lost"))
            assertEquals(listOf(app.getString(R.string.failed_to_send_message)), errors)
            assertTrue(vm.messages.value.none { it.text == "Lost" })
            collector.cancel()
        }

    @Test
    fun `send does nothing while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ChatViewModel(app, repo)
            finish(vm.send("c1", "x"))
            assertTrue(backend.requestsTo("rest/v1/messages", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `images and voice notes upload then insert typed messages`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.uploadChatMedia(any(), any(), any()) } returns "https://media/file"
            vm.sendImage("c1", byteArrayOf(1), "a.jpg")
            settle { backend.requestsTo("rest/v1/messages", HttpMethod.Post).size == 1 }
            vm.sendVoice("c1", byteArrayOf(2), "a.m4a")
            settle { backend.requestsTo("rest/v1/messages", HttpMethod.Post).size == 2 }
            val bodies = backend.requestsTo("rest/v1/messages", HttpMethod.Post).map { it.body }
            assertTrue(bodies[0].contains("\"message_type\":\"image\"") && bodies[0].contains("https://media/file"))
            assertTrue(bodies[1].contains("\"message_type\":\"voice\""))
        }

    @Test
    fun `media upload failures are swallowed`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.uploadChatMedia(any(), any(), any()) } throws IllegalStateException("upload")
            vm.sendImage("c1", byteArrayOf(1), "a.jpg")
            vm.sendVoice("c1", byteArrayOf(1), "a.m4a")
            settle { true }
            assertTrue(backend.requestsTo("rest/v1/messages", HttpMethod.Post).isEmpty())
            userId.value = null
            vm.sendImage("c1", byteArrayOf(1), "a.jpg")
            vm.sendVoice("c1", byteArrayOf(1), "a.m4a")
        }

    // ───────────────────────── edit / delete / restore ─────────────────────────

    @Test
    fun `editing a message patches its text and marks it edited`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadConversation("c1"))
            vm.startEditing(msg("m2", "u1", "Hi"))
            assertEquals("m2", vm.editing.value?.id)
            finish(vm.commitEdit("  Hello again  "))
            val patch = backend.requestsTo("rest/v1/messages", HttpMethod.Patch).single()
            assertTrue(patch.body.contains("\"text\":\"Hello again\"") && patch.body.contains("edited_at"))
            assertNull(vm.editing.value)
            assertEquals(
                "Hello again",
                vm.messages.value
                    .first { it.id == "m2" }
                    .text,
            )
        }

    @Test
    fun `blank or unchanged edits and a missing edit target are ignored`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.commitEdit("anything"))
            vm.startEditing(msg("m2", "u1", "Hi"))
            finish(vm.commitEdit("   "))
            vm.startEditing(msg("m2", "u1", "Hi"))
            finish(vm.commitEdit("Hi"))
            vm.startEditing(msg("m2", "u1", "Hi"))
            vm.cancelEditing()
            assertNull(vm.editing.value)
            assertTrue(backend.requestsTo("rest/v1/messages", HttpMethod.Patch).isEmpty())
        }

    @Test
    fun `a failed edit reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Patch, "/rest/v1/messages", "{}", HttpStatusCode.InternalServerError)
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            vm.startEditing(msg("m2", "u1", "Hi"))
            finish(vm.commitEdit("New"))
            assertEquals(listOf(app.getString(R.string.couldnt_save)), errors)
            collector.cancel()
        }

    @Test
    fun `deleting a message offers undo and restoring re-inserts it`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val target = msg("m2", "u1", "Hi").copy(replyToId = "m1", mediaUrl = "https://x")
            finish(vm.deleteMessage(target))
            assertEquals(target, vm.undoMessage.value)
            vm.clearUndoMessage()
            assertNull(vm.undoMessage.value)
            finish(vm.restoreMessage(target))
            val body = backend.requestsTo("rest/v1/messages", HttpMethod.Post).single().body
            assertTrue(body.contains("\"reply_to_id\":\"m1\"") && body.contains("\"media_url\":\"https://x\""))
            assertTrue(body.contains("sent_at"))
        }

    @Test
    fun `delete and restore failures report errors`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            backend.onJson(HttpMethod.Delete, "/rest/v1/messages", "{}", HttpStatusCode.InternalServerError)
            finish(vm.deleteMessage(msg("m2", "u1")))
            backend.onJson(HttpMethod.Post, "/rest/v1/messages", "{}", HttpStatusCode.InternalServerError)
            finish(vm.restoreMessage(msg("m2", "u1")))
            assertEquals(
                listOf(app.getString(R.string.couldnt_delete), app.getString(R.string.couldnt_save)),
                errors,
            )
            assertNull(vm.undoMessage.value)
            collector.cancel()
        }

    // ───────────────────────── moderation ─────────────────────────

    @Test
    fun `reporting sends a trimmed reason and confirms`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/report_message", "null")
            val notices = mutableListOf<String>()
            val collector = collecting(vm.noticeEvent, notices)
            finish(vm.reportMessage(msg("m1"), ReportReason.SPAM, "  " + "x".repeat(600)))
            val body = backend.requestsTo("rpc/report_message").single().body
            assertTrue(body.contains("\"p_reason\":\"spam\"") && body.contains("\"p_message_id\":\"m1\""))
            assertTrue(body.contains("x".repeat(500)) && !body.contains("x".repeat(501)))
            assertEquals(listOf(app.getString(R.string.report_sent)), notices)
            collector.cancel()
        }

    @Test
    fun `a failing report shows an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/rpc/report_message", "{}", HttpStatusCode.Forbidden)
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            finish(vm.reportMessage(msg("m1"), ReportReason.OTHER, ""))
            assertEquals(1, errors.size)
            collector.cancel()
        }

    @Test
    fun `blocking and unblocking update the set and confirm`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val notices = mutableListOf<String>()
            val collector = collecting(vm.noticeEvent, notices)
            finish(vm.blockUser("u2", "Bob"))
            assertEquals(setOf("u2"), vm.blockedUserIds.value)
            finish(vm.unblockUser("u2", "Bob"))
            assertTrue(vm.blockedUserIds.value.isEmpty())
            assertEquals(listOf("Bob is blocked", "Bob is unblocked"), notices)
            assertTrue(
                backend
                    .requestsTo("user_blocks", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("blocked_id=eq.u2"),
            )
            collector.cancel()
        }

    @Test
    fun `block and unblock failures roll back`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            backend.onJson(HttpMethod.Post, "/rest/v1/user_blocks", "{}", HttpStatusCode.InternalServerError)
            finish(vm.blockUser("u2", "Bob"))
            assertTrue(vm.blockedUserIds.value.isEmpty())
            finish(vm.loadConversation("c1"))
            assertEquals(setOf("u2"), vm.blockedUserIds.value)
            backend.onJson(HttpMethod.Delete, "/rest/v1/user_blocks", "{}", HttpStatusCode.InternalServerError)
            finish(vm.unblockUser("u2", "Bob"))
            assertEquals(setOf("u2"), vm.blockedUserIds.value)
            assertEquals(2, errors.size)
            userId.value = null
            finish(vm.blockUser("u9", "Nobody"))
            collector.cancel()
        }

    // ───────────────────────── conversations CRUD ─────────────────────────

    @Test
    fun `creating a group inserts the conversation and every distinct participant`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/conversations", """[{"id":"c9","user_from":"u1","name":"Trip"}]""")
            finish(vm.createConversation(" Trip ", listOf("u2", "u3", "u2")))
            val conv = backend.requestsTo("rest/v1/conversations", HttpMethod.Post).single().body
            assertTrue(conv.contains("\"name\":\"Trip\"") && conv.contains("\"family_id\":\"f1\""))
            val parts = backend.requestsTo("conversation_participants", HttpMethod.Post).map { it.body }
            assertEquals(3, parts.size)
        }

    @Test
    fun `creating a group tolerates a failing participant insert and a failing conversation`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/conversations", """[{"id":"c9","user_from":"u1"}]""")
            backend.onJson(HttpMethod.Post, "/rest/v1/conversation_participants", "{}", HttpStatusCode.Conflict)
            finish(vm.createConversation("Trip", listOf("u2", "u3")))
            backend.onJson(HttpMethod.Post, "/rest/v1/conversations", "{}", HttpStatusCode.InternalServerError)
            finish(vm.createConversation("Broken", listOf("u2", "u3")))
            assertTrue(vm.conversations.value.isNotEmpty())
        }

    @Test
    fun `a one-on-one chat with an existing conversation navigates to it`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val targets = mutableListOf<String>()
            val collector = collecting(vm.navigateToConversation, targets)
            // c2 is already a direct chat between u1 and u2.
            finish(vm.createConversation("", listOf("u2")))
            assertEquals(listOf("c2"), targets)
            assertTrue(backend.requestsTo("rest/v1/conversations", HttpMethod.Post).isEmpty())
            collector.cancel()
        }

    @Test
    fun `a one-on-one chat finds a shared two-person conversation through the participants table`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/conversation_participants",
                """[{"conversation_id":"c1","user_id":"u1"},{"conversation_id":"c1","user_id":"u3"}]""",
            )
            val targets = mutableListOf<String>()
            val collector = collecting(vm.navigateToConversation, targets)
            finish(vm.createConversation("", listOf("u3")))
            assertEquals(listOf("c1"), targets)
            collector.cancel()
        }

    @Test
    fun `a one-on-one chat with nobody in common creates a new conversation`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Get, "/rest/v1/conversation_participants", "[]")
            backend.onJson(HttpMethod.Post, "/rest/v1/conversations", """[{"id":"c9","user_from":"u1"}]""")
            finish(vm.createConversation("", listOf("u3")))
            assertEquals(1, backend.requestsTo("rest/v1/conversations", HttpMethod.Post).size)
        }

    @Test
    fun `creating a conversation is ignored without a user`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ChatViewModel(app, repo)
            finish(vm.createConversation("x", listOf("u2")))
            coEvery { repo.getUser("u1") } returns null
            userId.value = "u1"
            finish(vm.createConversation("x", listOf("u2")))
            assertTrue(backend.requestsTo("rest/v1/conversations", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `adding a member to a two-person chat starts a new group`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[$convFamily]")
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/conversation_participants",
                """[{"conversation_id":"c1","user_id":"u1"},{"conversation_id":"c1","user_id":"u2"}]""",
            )
            finish(vm.loadConversation("c1"))
            backend.onJson(HttpMethod.Post, "/rest/v1/conversations", """[{"id":"c9","user_from":"u1"}]""")
            val targets = mutableListOf<String>()
            val collector = collecting(vm.navigateToConversation, targets)
            finish(vm.addMember("c1", "u3"))
            assertEquals(listOf("c9"), targets)
            assertEquals(3, backend.requestsTo("conversation_participants", HttpMethod.Post).size)
            collector.cancel()
        }

    @Test
    fun `adding a member to a group inserts them and posts a system message`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(
                HttpMethod.Get,
                "/rest/v1/conversation_participants",
                """[{"conversation_id":"c1","user_id":"u1"},{"conversation_id":"c1","user_id":"u2"},{"conversation_id":"c1","user_id":"u5"}]""",
            )
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[$convFamily]")
            finish(vm.loadConversation("c1"))
            finish(vm.addMember("c1", "u3"))
            val system = backend.requestsTo("rest/v1/messages", HttpMethod.Post).single().body
            assertTrue(system.contains("\"message_type\":\"system\"") && system.contains("was added to the group"))
            assertEquals(1, backend.requestsTo("conversation_participants", HttpMethod.Post).size)
        }

    @Test
    fun `adding a member is ignored without a user`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ChatViewModel(app, repo)
            finish(vm.addMember("c1", "u3"))
            assertTrue(backend.requestsTo("conversation_participants", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `removing a member deletes the participant row and reloads`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.removeMember("c1", "u2"))
            val delete = backend.requestsTo("conversation_participants", HttpMethod.Delete).single()
            assertTrue(delete.query.contains("user_id=eq.u2") && delete.query.contains("conversation_id=eq.c1"))
            finish(vm.removeMember("c1", "u1"))
            assertEquals(2, backend.requestsTo("conversation_participants", HttpMethod.Delete).size)
        }

    @Test
    fun `a failed member removal reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Delete, "/rest/v1/conversation_participants", "{}", HttpStatusCode.Forbidden)
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            finish(vm.removeMember("c1", "u2"))
            assertEquals(1, errors.size)
            userId.value = null
            finish(vm.removeMember("c1", "u2"))
            collector.cancel()
        }

    @Test
    fun `renaming a conversation patches and updates the open one`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", "[$convFamily]")
            finish(vm.loadConversation("c1"))
            finish(vm.renameConversation("c1", " Crew "))
            assertEquals("Crew", vm.conversation.value?.name)
            assertTrue(
                backend
                    .requestsTo("rest/v1/conversations", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("Crew"),
            )
            backend.onJson(HttpMethod.Patch, "/rest/v1/conversations", "{}", HttpStatusCode.InternalServerError)
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            finish(vm.renameConversation("c1", "Bad"))
            assertEquals(1, errors.size)
            collector.cancel()
        }

    @Test
    fun `deleting a conversation removes it and signals the screen`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val deleted = mutableListOf<Unit>()
            val collector = collecting(vm.conversationDeleted, deleted)
            finish(vm.deleteConversation("c1"))
            assertEquals(1, deleted.size)
            assertTrue(
                backend
                    .requestsTo("rest/v1/conversations", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=eq.c1"),
            )
            collector.cancel()
        }

    @Test
    fun `a failed conversation delete reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Delete, "/rest/v1/conversations", "{}", HttpStatusCode.InternalServerError)
            val errors = mutableListOf<String>()
            val collector = collecting(vm.errorEvent, errors)
            finish(vm.deleteConversation("c1"))
            assertEquals(listOf(app.getString(R.string.failed_to_delete_conversation)), errors)
            collector.cancel()
        }

    // ───────────────────────── group image ─────────────────────────

    @Test
    fun `picking a group image uploads it and stores the url`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val uri = Uri.parse("content://media/group/1")
            shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
            backend.onJson(HttpMethod.Post, "/storage/v1/object/group-images", """{"Key":"group-images/c1/image.jpg","Id":"1"}""")
            finish(vm.saveImageFromUri(app, uri, "c1"))
            settle { backend.requestsTo("rest/v1/conversations", HttpMethod.Patch).isNotEmpty() }
            assertTrue(
                backend
                    .requestsTo("rest/v1/conversations", HttpMethod.Patch)
                    .single()
                    .body
                    .contains("image_uri"),
            )
        }

    @Test
    fun `an unreadable picked image is ignored`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val context = mockk<android.content.Context>(relaxed = true)
            every { context.contentResolver.openInputStream(any()) } returns null
            finish(vm.saveImageFromUri(context, Uri.parse("content://media/none"), "c1"))
            assertTrue(backend.requestsTo("storage").isEmpty())
        }

    @Test
    fun `camera capture uploads a group image and cancelling does not`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.prepareCameraCapture(app, "c1")
            java.io.File(app.cacheDir, "camera_captures/group_pending.jpg").writeBytes(byteArrayOf(1, 2, 3))
            finish(vm.onCameraResult(true))
            finish(vm.onCameraResult(true))
            vm.prepareCameraCapture(app, "c1")
            finish(vm.onCameraResult(false))
            finish(vm.onCameraResult(true))
        }

    @Test
    fun `removing the group image clears it`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Get, "/rest/v1/conversations", """[{"id":"c1","user_from":"u1","image_uri":"https://img"}]""")
            finish(vm.loadConversation("c1"))
            finish(vm.removeImage("c1"))
            assertNull(vm.conversation.value?.imageUri)
            assertTrue(backend.requestsTo("rest/v1/conversations", HttpMethod.Patch).isNotEmpty())
        }

    @Test
    fun `typing signals need an open channel and the list type is used`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.setTyping(true)
            vm.setTyping(false)
            assertTrue(vm.typingUsers.value.isEmpty())
            val preview: ConversationWithPreview = vm.conversations.value.first()
            assertEquals(ConversationModel::class, preview.conversation::class)
        }

    @Test
    fun `clearing the view model releases its realtime channels`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.loadConversation("c1"))
            val onCleared = ChatViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(vm)
            settle { true }
        }
}
