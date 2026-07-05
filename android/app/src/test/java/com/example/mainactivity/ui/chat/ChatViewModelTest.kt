package com.example.mainactivity.ui.chat

import android.app.Application
import app.cash.turbine.test
import com.example.mainactivity.data.ConversationModel
import com.example.mainactivity.data.ConversationWithPreview
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.remote.SupabaseManager
import com.example.mainactivity.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [ChatViewModel].
 *
 * ChatViewModel accesses SupabaseManager.client directly for all DB, Realtime, and Storage
 * operations. Unlike CalendarViewModel (which short-circuits when getUser returns null),
 * ChatViewModel always proceeds to the DB call in loadConversations(). To keep every test
 * deterministic and free from real network I/O, we stub the singleton:
 *
 *   mockkObject(SupabaseManager)
 *   every { SupabaseManager.client } throws RuntimeException("…")
 *
 * All Supabase accesses are already wrapped in runCatching in production code, so the
 * thrown exception is caught silently and the test coroutines run entirely on the
 * StandardTestDispatcher — no real HTTP calls, no OkHttp thread leaks.
 *
 * Tests cover:
 *   - Initial StateFlow values
 *   - totalUnread derivation (pure map over conversations StateFlow)
 *   - setCurrentConversation state machine
 *   - send() null-user guard, optimistic replyTo clear, and error-event emission
 *   - deleteConversation non-optimistic contract and error-event emission
 *   - markRead optimistic unread clear (with totalUnread propagation)
 *   - setReplyTo / clearReplyTo
 *   - userId lifecycle (null → non-null → null)
 *   - familyChanged reload trigger
 *
 * Conversations are injected into the private _conversations MutableStateFlow via reflection
 * wherever pre-loaded state is required, avoiding the DB layer entirely.
 */
@RunWith(JUnit4::class)
class ChatViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: ChatViewModel

    @Before
    fun setUp() {
        // Stub SupabaseManager.client to throw synchronously so all runCatching-wrapped
        // Supabase calls fail immediately on the test dispatcher (no real IO).
        mockkObject(SupabaseManager)
        every { SupabaseManager.client } throws RuntimeException("Supabase client not available in unit tests")

        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        coEvery { repo.getUser(any()) } returns null

        val app = mockk<Application>(relaxed = true)
        vm = ChatViewModel(app, repo)
    }

    @After
    fun tearDown() {
        unmockkObject(SupabaseManager)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun makePreview(
        id: String,
        unreadCount: Int = 0,
    ): ConversationWithPreview =
        ConversationWithPreview(
            conversation = ConversationModel(id = id, userFrom = "user-1"),
            lastMessage = null,
            lastSenderName = null,
            unreadCount = unreadCount,
            participants = emptyList(),
        )

    /** Injects directly into the private _conversations backing field via reflection. */
    private fun injectConversations(conversations: List<ConversationWithPreview>) {
        val field = ChatViewModel::class.java.getDeclaredField("_conversations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<List<ConversationWithPreview>>).value = conversations
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial state — before any coroutine is advanced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial conversations list is empty`() {
        assertTrue("conversations must start empty", vm.conversations.value.isEmpty())
    }

    @Test
    fun `initial messages list is empty`() {
        assertTrue("messages must start empty", vm.messages.value.isEmpty())
    }

    @Test
    fun `initial currentConversationId is null`() {
        assertNull("currentConversationId must start null", vm.currentConversationId.value)
    }

    @Test
    fun `initial isLoading is false`() {
        assertFalse("isLoading must start false", vm.isLoading.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. totalUnread derivation — pure map over conversations StateFlow
    //    Must be collected via Turbine because stateIn uses WhileSubscribed(5000);
    //    reading .value without an active subscriber returns the initial 0.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `totalUnread is zero for empty conversations`() =
        runTest(dispatcherRule.dispatcher) {
            vm.totalUnread.test {
                assertEquals("initial totalUnread must be 0", 0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `totalUnread sums unread counts across all conversations`() =
        runTest(dispatcherRule.dispatcher) {
            vm.totalUnread.test {
                awaitItem() // consume initial 0 before injection

                injectConversations(
                    listOf(
                        makePreview("c1", unreadCount = 3),
                        makePreview("c2", unreadCount = 2),
                        makePreview("c3", unreadCount = 0),
                    ),
                )
                advanceUntilIdle()

                assertEquals("totalUnread should sum all unread counts", 5, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `totalUnread is zero when all conversations have zero unread`() =
        runTest(dispatcherRule.dispatcher) {
            injectConversations(
                listOf(
                    makePreview("c1", unreadCount = 0),
                    makePreview("c2", unreadCount = 0),
                ),
            )

            vm.totalUnread.test {
                advanceUntilIdle()
                assertEquals(
                    "totalUnread should be 0 when no unread messages",
                    0,
                    expectMostRecentItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `totalUnread updates when conversations change`() =
        runTest(dispatcherRule.dispatcher) {
            vm.totalUnread.test {
                awaitItem() // initial 0

                injectConversations(listOf(makePreview("c1", unreadCount = 7)))
                advanceUntilIdle()
                assertEquals(
                    "totalUnread should reflect single conversation unread",
                    7,
                    expectMostRecentItem(),
                )

                injectConversations(emptyList())
                advanceUntilIdle()
                assertEquals(
                    "totalUnread should drop to 0 after clearing conversations",
                    0,
                    expectMostRecentItem(),
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. setCurrentConversation — sets / clears the public StateFlow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setCurrentConversation sets the conversation ID`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setCurrentConversation("conv-abc")
            advanceUntilIdle()

            assertEquals("conv-abc", vm.currentConversationId.value)
        }

    @Test
    fun `setCurrentConversation with null clears the ID`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setCurrentConversation("conv-1")
            vm.setCurrentConversation(null)
            advanceUntilIdle()

            assertNull(
                "currentConversationId should be null after clearing",
                vm.currentConversationId.value,
            )
        }

    @Test
    fun `setCurrentConversation replaces previously set value`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setCurrentConversation("first")
            vm.setCurrentConversation("second")
            advanceUntilIdle()

            assertEquals("second", vm.currentConversationId.value)
        }

    @Test
    fun `currentConversationId StateFlow emits values via Turbine`() =
        runTest(dispatcherRule.dispatcher) {
            vm.currentConversationId.test {
                assertNull("initial emission must be null", awaitItem())

                vm.setCurrentConversation("conv-1")
                assertEquals("conv-1", awaitItem())

                vm.setCurrentConversation(null)
                assertNull("emission after null must be null", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. send() — null guard, replyTo clear, error event, rollback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `send with null userId does not modify messages`() =
        runTest(dispatcherRule.dispatcher) {
            // userId is null by default — send() hits the ?: return@launch guard
            vm.send("conv-1", "hello")
            advanceUntilIdle()

            assertTrue(
                "messages must stay empty when userId is null",
                vm.messages.value.isEmpty(),
            )
        }

    @Test
    fun `send with null userId does not clear replyTo`() =
        runTest(dispatcherRule.dispatcher) {
            val replyMsg =
                MessageModel(
                    id = "msg-1",
                    conversationId = "conv-1",
                    userFrom = "u1",
                    text = "original",
                )
            vm.setReplyTo(replyMsg)

            vm.send("conv-1", "some text")
            advanceUntilIdle()

            assertEquals(
                "replyTo should NOT be cleared when send() returns early due to null userId",
                replyMsg,
                vm.replyTo.value,
            )
        }

    @Test
    fun `send with valid userId clears replyTo optimistically`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            val replyMsg =
                MessageModel(
                    id = "msg-1",
                    conversationId = "conv-1",
                    userFrom = "u1",
                    text = "original",
                )
            vm.setReplyTo(replyMsg)
            assertEquals("pre-condition: replyTo should be set", replyMsg, vm.replyTo.value)

            vm.send("conv-1", "reply text")
            advanceUntilIdle()

            assertNull("replyTo should be cleared by send() before DB call", vm.replyTo.value)
        }

    @Test
    fun `send emits errorEvent when DB is unavailable`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            vm.errorEvent.test {
                vm.send("conv-1", "hello")
                advanceUntilIdle()

                assertEquals("Failed to send message", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `send rolls back temp message when DB insert fails`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            vm.send("conv-1", "hello")
            advanceUntilIdle()

            assertTrue(
                "messages must be empty after rollback (DB insert failed + loadMessages failed)",
                vm.messages.value.isEmpty(),
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. deleteConversation — non-optimistic contract and error event
    //    deleteConversation() only removes the item in .onSuccess; since DB fails
    //    the list is unchanged and errorEvent is emitted.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleteConversation emits errorEvent when DB is unavailable`() =
        runTest(dispatcherRule.dispatcher) {
            vm.errorEvent.test {
                vm.deleteConversation("conv-1")
                advanceUntilIdle()

                assertEquals("Failed to delete conversation", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteConversation does not remove conversation when DB fails`() =
        runTest(dispatcherRule.dispatcher) {
            injectConversations(listOf(makePreview("conv-1")))

            vm.deleteConversation("conv-1")
            advanceUntilIdle()

            assertEquals(
                "conversation must stay in list when DB delete fails (no optimistic removal)",
                1,
                vm.conversations.value.size,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. markRead — optimistic unread clear via injection + real markRead call
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `markRead sets unreadCount to zero for the targeted conversation`() =
        runTest(dispatcherRule.dispatcher) {
            injectConversations(
                listOf(
                    makePreview("c1", unreadCount = 5),
                    makePreview("c2", unreadCount = 3),
                ),
            )

            vm.markRead("c1")
            advanceUntilIdle()

            val c1 = vm.conversations.value.first { it.conversation.id == "c1" }
            assertEquals("c1 unreadCount should be 0 after markRead", 0, c1.unreadCount)
        }

    @Test
    fun `markRead does not change unread count for other conversations`() =
        runTest(dispatcherRule.dispatcher) {
            injectConversations(
                listOf(
                    makePreview("c1", unreadCount = 5),
                    makePreview("c2", unreadCount = 3),
                ),
            )

            vm.markRead("c1")
            advanceUntilIdle()

            val c2 = vm.conversations.value.first { it.conversation.id == "c2" }
            assertEquals("c2 unreadCount must be unchanged after marking c1 as read", 3, c2.unreadCount)
        }

    @Test
    fun `markRead reduces totalUnread to reflect only remaining unread conversations`() =
        runTest(dispatcherRule.dispatcher) {
            injectConversations(
                listOf(
                    makePreview("c1", unreadCount = 5),
                    makePreview("c2", unreadCount = 3),
                ),
            )

            vm.totalUnread.test {
                advanceUntilIdle() // let stateIn collect and compute current value (8)
                expectMostRecentItem() // drain

                vm.markRead("c1")
                advanceUntilIdle()

                assertEquals(
                    "totalUnread should equal c2's unread count (3) after marking c1 read",
                    3,
                    expectMostRecentItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. setReplyTo / clearReplyTo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setReplyTo stores the message in replyTo StateFlow`() {
        val msg = MessageModel(id = "msg-1", conversationId = "conv-1", userFrom = "u1", text = "hi")
        vm.setReplyTo(msg)
        assertEquals(msg, vm.replyTo.value)
    }

    @Test
    fun `clearReplyTo resets replyTo to null`() {
        val msg = MessageModel(id = "msg-1", conversationId = "conv-1", userFrom = "u1", text = "hi")
        vm.setReplyTo(msg)
        vm.clearReplyTo()
        assertNull("replyTo should be null after clearReplyTo", vm.replyTo.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. userId lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `null userId results in empty conversations and no loading`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            assertTrue(
                "conversations should be empty when userId is null",
                vm.conversations.value.isEmpty(),
            )
            assertFalse("isLoading should be false after null userId", vm.isLoading.value)
        }

    @Test
    fun `null userId does not trigger getUser`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    @Test
    fun `non-null userId triggers at least one getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            coVerify(atLeast = 1) { repo.getUser("user-1") }
        }

    @Test
    fun `userId becoming null after non-null clears conversations`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            userId.value = null
            advanceUntilIdle()

            assertTrue(
                "conversations should be cleared when userId becomes null",
                vm.conversations.value.isEmpty(),
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. familyChanged — triggers a reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `familyChanged emission triggers an additional getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()
            // At this point getUser was called at least once by the init collector

            familyChanged.emit(Unit)
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }
}
