package com.sandnes.familyapp.ui.chat

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.ConversationModel
import com.sandnes.familyapp.data.ConversationWithPreview
import com.sandnes.familyapp.data.MessageModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ChatScreensTest : ComposeScreenTest() {
    private val conversations = MutableStateFlow<List<ConversationWithPreview>>(emptyList())
    private val familyMembers = MutableStateFlow<List<UserModel>>(emptyList())
    private val myId = MutableStateFlow<String?>("u1")
    private val loading = MutableStateFlow(false)
    private val navigate = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val conversation = MutableStateFlow<ConversationModel?>(null)
    private val messages = MutableStateFlow<List<MessageModel>>(emptyList())
    private val blocked = MutableStateFlow<Set<String>>(emptySet())
    private val isAdmin = MutableStateFlow(false)
    private val otherLastRead = MutableStateFlow<String?>(null)
    private val typing = MutableStateFlow<Set<String>>(emptySet())
    private val replyTo = MutableStateFlow<MessageModel?>(null)
    private val editing = MutableStateFlow<MessageModel?>(null)
    private val profiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    private val participants = MutableStateFlow<List<UserModel>>(emptyList())
    private val reactions = MutableStateFlow<Map<String, Map<String, List<String>>>>(emptyMap())
    private val undo = MutableStateFlow<MessageModel?>(null)
    private val deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val notices = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private lateinit var vm: ChatViewModel

    private val ada = UserModel(id = "u1", name = "Ada Lovelace", avatarColor = 0xFF0000FF.toInt())
    private val bob = UserModel(id = "u2", name = "Bob Builder", lastActiveAt = Instant.now().toString())
    private val cy = UserModel(id = "u3", name = "Cy Young", avatarUrl = "https://img.example/c.png")

    private fun ago(minutes: Long) = Instant.now().minusSeconds(minutes * 60).toString()

    @Suppress("LongParameterList")
    private fun msg(
        id: String,
        from: String,
        text: String,
        minutesAgo: Long,
        type: String = "text",
        media: String? = null,
        reply: String? = null,
        edited: String? = null,
    ) = MessageModel(id = id, conversationId = "c1", userFrom = from, text = text, sentAt = ago(minutesAgo), messageType = type, mediaUrl = media, replyToId = reply, editedAt = edited)

    @Before
    fun setUpViewModel() {
        vm = mockk(relaxed = true)
        every { vm.conversations } returns conversations
        every { vm.familyMembers } returns familyMembers
        every { vm.currentUserId } returns myId
        every { vm.isLoading } returns loading
        every { vm.navigateToConversation } returns navigate
        every { vm.conversation } returns conversation
        every { vm.visibleMessages } returns messages
        every { vm.messages } returns messages
        every { vm.blockedUserIds } returns blocked
        every { vm.isAdmin } returns isAdmin
        every { vm.otherLastRead } returns otherLastRead
        every { vm.typingUsers } returns typing
        every { vm.replyTo } returns replyTo
        every { vm.editing } returns editing
        every { vm.userProfiles } returns profiles
        every { vm.currentParticipants } returns participants
        every { vm.reactions } returns reactions
        every { vm.undoMessage } returns undo
        every { vm.conversationDeleted } returns deleted
        every { vm.errorEvent } returns errors
        every { vm.noticeEvent } returns notices
        profiles.value = mapOf("u1" to ada, "u2" to bob, "u3" to cy)
    }

    // ── Chat list ──────────────────────────────────────────────────────────

    @Suppress("LongParameterList")
    private fun preview(
        id: String,
        name: String = "",
        users: List<UserModel> = listOf(ada, bob),
        last: MessageModel? = msg("m$id", "u2", "See you soon", 3),
        sender: String? = "Bob",
        unread: Int = 0,
        userTo: String? = if (users.size == 2) "u2" else null,
        image: String? = null,
    ) = ConversationWithPreview(
        conversation = ConversationModel(id = id, userFrom = "u1", userTo = userTo, name = name, imageUri = image),
        lastMessage = last,
        lastSenderName = sender,
        unreadCount = unread,
        participants = users,
    )

    @Test
    fun `chat list shows conversations of every kind and opens one`() {
        var opened: String? = null
        conversations.value =
            listOf(
                preview("1", unread = 3),
                preview("2", name = "Family", users = listOf(ada, bob, cy), userTo = null, unread = 120),
                preview("3", users = listOf(ada, bob, cy), userTo = null, last = null, sender = null),
                preview("4", last = msg("m4", "u2", "", 5, type = "image")),
                preview("5", last = msg("m5", "u1", "", 7, type = "voice"), sender = null),
                preview("6", name = "Solo", users = listOf(ada), userTo = null, sender = null, image = "https://img.example/g.png"),
            )
        compose.setContent { ChatScreen(onOpen = { opened = it }, viewModel = vm) }
        compose.waitForText("Bob Builder")
        assertTrue(compose.hasText("Family"))
        assertTrue(compose.hasText("99+"))
        assertTrue(compose.hasText(str(R.string.no_messages_yet)))
        compose.clickText("Family")
        assertTrue(opened == "2")
    }

    @Test
    fun `chat list loading and empty states`() {
        loading.value = true
        compose.setContent { ChatScreen(onOpen = {}, viewModel = vm) }
        compose.waitForIdle()
        loading.value = false
        compose.waitForText(str(R.string.no_conversations_yet))
    }

    @Test
    fun `starting a new chat or group from the picker`() {
        familyMembers.value = listOf(ada, bob, cy)
        compose.setContent { ChatScreen(onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_conversations_yet))
        compose.clickDescription(str(R.string.new_conversation))
        compose.waitForText(str(R.string.select_family_members_to_chat_with))
        compose.clickText("Bob Builder")
        compose.clickText(str(R.string.start_chat))
        verify(timeout = 20_000) { vm.createConversation("", listOf("u2")) }
        compose.clickDescription(str(R.string.new_conversation))
        compose.clickText("Bob Builder")
        compose.clickText("Cy Young")
        compose.typeInto("Weekend", 0)
        compose.clickText(str(R.string.create_group))
        verify(timeout = 20_000) { vm.createConversation("Weekend", any()) }
    }

    @Test
    fun `new conversation picker explains when nobody else is in the family`() {
        familyMembers.value = listOf(ada)
        compose.setContent { ChatScreen(onOpen = {}, viewModel = vm) }
        compose.waitForText(str(R.string.no_conversations_yet))
        compose.clickDescription(str(R.string.new_conversation))
        compose.waitForText(str(R.string.no_other_family_members_yet))
    }

    @Test
    fun `a newly created conversation is opened from the list`() {
        var opened: String? = null
        compose.setContent { ChatScreen(onOpen = { opened = it }, viewModel = vm) }
        compose.waitForText(str(R.string.no_conversations_yet))
        navigate.tryEmit("fresh")
        compose.waitUntil(20_000) { opened == "fresh" }
    }

    // ── Conversation ───────────────────────────────────────────────────────

    private fun oneOnOne() {
        conversation.value = ConversationModel(id = "c1", userFrom = "u1", userTo = "u2")
        participants.value = listOf(ada, bob)
        familyMembers.value = listOf(ada, bob, cy)
        messages.value =
            listOf(
                msg("m1", "u2", "Hello there", 90),
                msg("m2", "u1", "Hi Bob", 85, edited = ago(80)),
                msg("m3", "u2", "", 40, type = "image", media = "https://img.example/p.png"),
                msg("m4", "u1", "", 30, type = "voice", media = "https://img.example/v.m4a"),
                msg("m5", "u2", "Ada joined", 20, type = "system"),
                msg("m6", "u1", "Replying to you", 10, reply = "m1"),
                msg("m7", "u2", "Quoting a photo", 5, reply = "m3"),
            )
    }

    private fun open(id: String = "c1") {
        compose.setContent { ConversationScreen(conversationId = id, onBack = {}, viewModel = vm) }
    }

    @Test
    fun `conversation renders messages groups time separators and reactions`() {
        oneOnOne()
        reactions.value = mapOf("m1" to mapOf("👍" to listOf("u1", "u3"), "❤️" to listOf("u2")), "m2" to mapOf("😆" to listOf("u2")))
        otherLastRead.value = Instant.now().toString()
        typing.value = setOf("u2")
        open()
        compose.waitForText("Hello there")
        assertTrue(compose.hasText("Hi Bob"))
        assertTrue(compose.hasText(str(R.string.edited)))
        assertTrue(compose.hasText("Replying to you"))
        verify { vm.loadConversation("c1") }
        verify { vm.setCurrentConversation("c1") }
        verify { vm.markRead("c1") }
    }

    @Test
    fun `empty conversation invites a first message and sending works`() {
        conversation.value = ConversationModel(id = "c1", userFrom = "u1", userTo = "u2")
        participants.value = listOf(ada, bob)
        open()
        compose.waitForText(str(R.string.say_hello))
        compose.typeInto("Good morning", 0)
        compose.clickDescription(str(R.string.send))
        verify { vm.send("c1", "Good morning") }
        verify(atLeast = 1) { vm.setTyping(true) }
    }

    @Test
    fun `message actions through the long press menu`() {
        oneOnOne()
        open()
        compose.waitForText("Hi Bob")
        // Own message: react, edit, delete.
        longPress("Hi Bob")
        compose.waitForText(str(R.string.edit))
        compose.clickText("👍")
        verify { vm.toggleReaction("m2", "c1", "👍") }
        longPress("Hi Bob")
        compose.clickText(str(R.string.edit))
        verify { vm.startEditing(match { it.id == "m2" }) }
        longPress("Hi Bob")
        compose.clickText(str(R.string.delete))
        verify { vm.deleteMessage(match { it.id == "m2" }) }
        // Someone else's message: report and block.
        longPress("Hello there")
        compose.waitForText(str(R.string.report))
        compose.clickText(str(R.string.report))
        compose.waitForText(str(R.string.report_message_title))
        compose.clickText(str(R.string.report_reason_spam))
        compose.clickText(str(R.string.report_send))
        verify { vm.reportMessage(match { it.id == "m1" }, ReportReason.SPAM, any()) }
        longPress("Hello there")
        compose.clickText(str(R.string.block))
        compose.waitForText(str(R.string.block_user_body))
        compose.clickText(str(R.string.block))
        verify { vm.blockUser("u2", any()) }
    }

    private fun longPress(text: String) {
        val node = compose.onAllNodesWithText(text)[0].fetchSemanticsNode()
        compose.runOnUiThread { node.config[SemanticsActions.OnLongClick].action?.invoke() }
        compose.waitForIdle()
    }

    @Test
    fun `tapping a message toggles its exact time and swiping replies`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        compose.clickText("Hello there")
        val row = compose.onAllNodesWithText("Hello there")[0]
        row.performTouchInput { down(center) }
        row.performTouchInput {
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(60f, 0f),
            )
        }
        compose.waitForIdle()
        row.performTouchInput {
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(120f, 0f),
            )
        }
        compose.waitForIdle()
        row.performTouchInput { up() }
        compose.waitForIdle()
        verify(timeout = 20_000) { vm.setReplyTo(match { it.id == "m1" }) }
    }

    @Test
    fun `editing and reply banners can be dismissed`() {
        oneOnOne()
        editing.value = messages.value[1]
        replyTo.value = messages.value[0]
        open()
        compose.waitForText(str(R.string.editing_message))
        compose.waitForText(str(R.string.replying_to), substring = true)
        compose.clickDescription(str(R.string.cancel))
        verify { vm.cancelEditing() }
        compose.clickDescription(str(R.string.cancel_reply))
        verify { vm.clearReplyTo() }
    }

    @Test
    fun `committing an edit goes through the edit path`() {
        oneOnOne()
        editing.value = messages.value[1]
        open()
        compose.waitForText(str(R.string.editing_message))
        compose.waitForText("Hi Bob")
        compose.clickDescription(str(R.string.send))
        verify { vm.commitEdit("Hi Bob") }
    }

    @Test
    fun `reply banner names photo and voice quotes`() {
        oneOnOne()
        replyTo.value = messages.value[2]
        open()
        compose.waitForText("📷", substring = true)
        replyTo.value = messages.value[3]
        compose.waitForText("🎤", substring = true)
    }

    @Test
    fun `one on one options block unblock rename image and delete`() {
        oneOnOne()
        conversation.value = ConversationModel(id = "c1", userFrom = "u1", userTo = "u2", imageUri = "https://img.example/g.png")
        open()
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.block_user_named, "Bob Builder"))
        compose.waitForText(str(R.string.block_user_body))
        compose.clickText(str(R.string.cancel))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.remove_image))
        verify { vm.removeImage("c1") }
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.change_image))
        compose.waitForText(str(R.string.group_image))
        compose.clickText(str(R.string.take_photo))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.change_image))
        compose.clickText(str(R.string.choose_from_gallery))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.change_image))
        compose.clickText(str(R.string.remove_image))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.change_image))
        compose.clickText(str(R.string.cancel))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.delete_conversation))
        compose.waitForText(str(R.string.delete_conversation_confirm))
        compose.clickText(str(R.string.cancel))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.delete_conversation))
        compose.clickText(str(R.string.delete))
        verify { vm.deleteConversation("c1") }
    }

    @Test
    fun `an already blocked user can be unblocked from the menu`() {
        oneOnOne()
        blocked.value = setOf("u2")
        open()
        compose.waitForText("Hi Bob")
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.unblock_user_named, "Bob Builder"))
        verify { vm.unblockUser("u2", "Bob Builder") }
    }

    @Test
    fun `adding a member to a one on one chat`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.add_member_creates_group))
        compose.waitForText(str(R.string.tap_a_member_to_add))
        compose.clickText("Cy Young")
        verify { vm.addMember("c1", "u3") }
    }

    private fun group() {
        oneOnOne()
        conversation.value = ConversationModel(id = "c1", userFrom = "u1", name = "Family")
        participants.value = listOf(ada, bob, cy)
        familyMembers.value = listOf(ada, bob, cy, UserModel(id = "u4", name = "Di Dee"))
    }

    @Test
    fun `group options rename remove members and list them`() {
        group()
        isAdmin.value = true
        open()
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.rename))
        compose.waitForText(str(R.string.rename_conversation))
        compose.clickText(str(R.string.rename))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.members))
        compose.waitForText(str(R.string.members_count_title, 3))
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.remove_member))
        compose.waitForText(str(R.string.name_with_you_suffix, "Ada Lovelace"))
        compose.clickText(str(R.string.remove), index = 0)
        verify { vm.removeMember("c1", any()) }
    }

    @Test
    fun `leaving a group goes back`() {
        group()
        var back = 0
        compose.setContent { ConversationScreen("c1", { back++ }, viewModel = vm) }
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.options))
        compose.clickText(str(R.string.remove_member))
        compose.clickText(str(R.string.leave))
        assertTrue(back > 0)
    }

    @Test
    fun `events from the view model show as snackbars and trigger navigation`() {
        oneOnOne()
        var back = 0
        var next: String? = null
        compose.setContent { ConversationScreen("c1", { back++ }, { next = it }, vm) }
        compose.waitForText("Hello there")
        errors.tryEmit("Something broke")
        compose.waitForText("Something broke")
        notices.tryEmit("FYI")
        compose.waitUntil(20_000) { compose.hasText("FYI") || compose.hasText("Something broke").not() }
        navigate.tryEmit("c9")
        compose.waitUntil(20_000) { next == "c9" }
        deleted.tryEmit(Unit)
        compose.waitUntil(20_000) { back >= 2 }
    }

    @Test
    fun `a deleted message offers undo`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        undo.value = messages.value[1]
        compose.waitForText(str(R.string.message_deleted))
        compose.clickText(str(R.string.undo))
        verify { vm.restoreMessage(match { it.id == "m2" }) }
        verify(timeout = 20_000) { vm.clearUndoMessage() }
    }

    @Test
    fun `attachments menu offers gallery and camera and the mic can be held`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.photo_library))
        compose.clickText(str(R.string.photo_library))
        compose.clickDescription(str(R.string.photo_library))
        // Android's FileProvider expects device path separators; Robolectric runs
        // on Windows too. This UI test exercises the picker, not provider paths.
        io.mockk.mockkStatic(androidx.core.content.FileProvider::class)
        try {
            every {
                androidx.core.content.FileProvider
                    .getUriForFile(any(), any(), any())
            } returns android.net.Uri.parse("content://com.sandnes.familyapp.fileprovider/camera_captures/test.jpg")
            compose.clickText(str(R.string.camera))
        } finally {
            io.mockk.unmockkStatic(androidx.core.content.FileProvider::class)
        }
        compose.onAllNodesWithText("x").fetchSemanticsNodes()
    }

    @Test
    fun `holding the mic starts a recording and releasing a short one discards it`() {
        oneOnOne()
        org.robolectric.Shadows
            .shadowOf(com.sandnes.familyapp.testutil.appContext as android.app.Application)
            .grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        open()
        compose.waitForText("Hello there")
        val mic =
            compose.onAllNodes(
                androidx.compose.ui.test
                    .hasContentDescription(str(R.string.record_voice_message)),
            )[0]
        mic.performTouchInput {
            down(center)
            advanceEventTime(200)
        }
        compose.waitForIdle()
        mic.performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun `mic without permission asks for it`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        val mic =
            compose.onAllNodes(
                androidx.compose.ui.test
                    .hasContentDescription(str(R.string.record_voice_message)),
            )[0]
        mic.performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun `image messages open a full screen viewer with download and close`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.image_label))
        compose.waitForText("", substring = true)
        compose.clickDescription(str(R.string.download_image))
        compose.clickDescription(str(R.string.close))
        compose.clickDescription(str(R.string.image_label))
        compose.clickDescription(str(R.string.full_screen_image))
    }

    @Test
    fun `voice messages play and pause`() {
        oneOnOne()
        open()
        compose.waitForText("Hello there")
        compose.clickDescription(str(R.string.play_voice_message))
        compose.waitForIdle()
        compose.clickDescription(str(R.string.play_voice_message))
        compose.waitForIdle()
    }
}
