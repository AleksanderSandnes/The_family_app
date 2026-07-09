// Behaviour tests for ChatViewModel via MockRepository + NoopRealtimeObserver. The VM is
// always built with the mock repo and a no-op realtime factory, so construction opens no
// live Supabase channel or network. Tests that would otherwise open the raw typing/reactions
// presence channels (which happen only inside loadConversation) deliberately avoid that path.
@testable import FamilyApp
import XCTest

@MainActor
final class ChatViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String? = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> ChatViewModel {
        ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    private func conversation(id: String, name: String = "Chat") -> ConversationModel {
        var conv = ConversationModel()
        conv.id = id
        conv.name = name
        conv.familyId = "f1"
        return conv
    }

    private func message(
        id: String, conv: String, from: String, sentAt: String, text: String = "hi"
    ) -> MessageModel {
        var msg = MessageModel()
        msg.id = id
        msg.conversationId = conv
        msg.userFrom = from
        msg.sentAt = sentAt
        msg.text = text
        return msg
    }

    private func participant(
        conv: String, user: String, lastRead: String?
    ) -> ConversationParticipantModel {
        var part = ConversationParticipantModel()
        part.conversationId = conv
        part.userId = user
        part.lastReadAt = lastRead
        return part
    }

    // MARK: - Conversation list load + preview assembly

    func testLoadConversationsBuildsPreviews() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1", name: "Family")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-01T00:00:00Z")],
        ]
        mock.lastMessageByConversation = [
            "c1": message(id: "m1", conv: "c1", from: "u2", sentAt: "2026-01-02T00:00:00Z", text: "yo"),
        ]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }
        let preview = vm.conversations.first { $0.conversation.id == "c1" }
        XCTAssertEqual(preview?.conversation.name, "Family")
        XCTAssertEqual(preview?.lastMessage?.text, "yo")
    }

    func testLoadConversationsEmptyWhenSignedOut() async {
        let mock = MockRepository() // no signIn
        let vm = makeVM(mock)
        await waitUntil { true }
        XCTAssertTrue(vm.conversations.isEmpty)
    }

    // MARK: - Unread counts (the past-bug hotspot)

    func testUnreadCountsMessagesFromOthersNewerThanLastRead() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-01T00:00:10Z")],
        ]
        mock.messagesByConversation = [
            "c1": [
                // Before my last_read → already seen, not counted.
                message(id: "old", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:05Z"),
                // After last_read, from another user → counted.
                message(id: "new1", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:20Z"),
                message(id: "new2", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:30Z"),
            ],
        ]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }
        XCTAssertEqual(vm.conversations.first?.unreadCount, 2)
        XCTAssertEqual(vm.totalUnread, 2)
    }

    func testUnreadIgnoresOwnMessages() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-01T00:00:00Z")],
        ]
        mock.messagesByConversation = [
            "c1": [
                // My own message after last_read → must never count.
                message(id: "mine", conv: "c1", from: "u1", sentAt: "2026-01-01T00:00:20Z"),
                message(id: "theirs", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:20Z"),
            ],
        ]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }
        XCTAssertEqual(vm.conversations.first?.unreadCount, 1)
    }

    func testUnreadIsZeroWhenLastReadAfterAllMessages() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-02T00:00:00Z")],
        ]
        mock.messagesByConversation = [
            "c1": [message(id: "m1", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:20Z")],
        ]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }
        XCTAssertEqual(vm.conversations.first?.unreadCount, 0)
    }

    /// Regression for the badge-resurrects-on-launch bug: a nil last_read_at (the state the
    /// app was permanently stuck in when conversation_participants lacked GRANT UPDATE, so
    /// markConversationRead's write was silently denied) falls back to the epoch and counts
    /// every message from others. Once last_read_at persists, the same load reports zero —
    /// which is exactly what the grant migration restores.
    func testUnreadFallsBackToAllWhenLastReadIsNil() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: nil)],
        ]
        mock.messagesByConversation = [
            "c1": [
                message(id: "m1", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:10Z"),
                message(id: "m2", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:20Z"),
            ],
        ]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }
        // Never-read → every message from others is unread (the resurrection symptom).
        XCTAssertEqual(vm.conversations.first?.unreadCount, 2)

        // Simulate the persisted read (what the restored UPDATE grant now allows) and reload.
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-01T00:00:30Z")],
        ]
        await vm.refreshConversations()
        await waitUntil { vm.conversations.first?.unreadCount == 0 }
        XCTAssertEqual(vm.conversations.first?.unreadCount, 0)
    }

    func testUnreadZeroForOpenConversation() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-01T00:00:00Z")],
        ]
        mock.messagesByConversation = [
            "c1": [message(id: "m1", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:20Z")],
        ]
        let vm = makeVM(mock)
        vm.setCurrentConversation("c1") // the open thread never badges itself
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }
        XCTAssertEqual(vm.conversations.first?.unreadCount, 0)
    }

    func testMarkReadCallsRepoAndZeroesUnread() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        mock.participantsByConversation = [
            "c1": [participant(conv: "c1", user: "u1", lastRead: "2026-01-01T00:00:00Z")],
        ]
        mock.messagesByConversation = [
            "c1": [message(id: "m1", conv: "c1", from: "u2", sentAt: "2026-01-01T00:00:20Z")],
        ]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.first?.unreadCount == 1 }

        vm.markRead("c1")
        await waitUntil { mock.markReadConversations.contains("c1") }
        XCTAssertEqual(mock.markReadConversations.first, "c1")
        await waitUntil { vm.conversations.first?.unreadCount == 0 }
        XCTAssertEqual(vm.conversations.first?.unreadCount, 0)
    }

    // MARK: - Sending

    func testSendInsertsMessageAndOptimistic() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }
        // Seed the server so the post-send reload returns the message.
        mock.messagesByConversation = [
            "c1": [message(id: "s1", conv: "c1", from: "u1", sentAt: "2026-01-01T00:00:00Z", text: "Hello")],
        ]
        vm.send(conversationId: "c1", text: "Hello")
        await waitUntil { !mock.insertedTextMessages.isEmpty }
        let inserted = mock.insertedTextMessages.first
        XCTAssertEqual(inserted?.conversationId, "c1")
        XCTAssertEqual(inserted?.userFrom, "u1")
        XCTAssertEqual(inserted?.text, "Hello")
        XCTAssertNil(inserted?.replyToId)
        await waitUntil { vm.messages.contains { $0.text == "Hello" } }
        XCTAssertTrue(vm.messages.contains { $0.text == "Hello" })
    }

    // MARK: - Members

    func testCreateConversationInsertsConversationAndParticipants() async {
        let mock = makeMock()
        mock.insertConversationResult = conversation(id: "new1", name: "Group")
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.createConversation(name: "Group", memberIds: ["u2", "u3"])
        await waitUntil { !mock.insertedConversations.isEmpty }
        XCTAssertEqual(mock.insertedConversations.first?.userFrom, "u1")
        XCTAssertEqual(mock.insertedConversations.first?.name, "Group")
        XCTAssertEqual(mock.insertedConversations.first?.familyId, "f1")
        await waitUntil { mock.insertedParticipants.count >= 3 }
        // Creator plus both invited members.
        XCTAssertEqual(Set(mock.insertedParticipants.map(\.userId)), ["u1", "u2", "u3"])
    }

    func testAddMemberPromotesOneOnOneToGroup() async {
        // With no open thread, currentParticipants is empty (count <= 2), so addMember takes
        // the 1:1 → group promotion path: create a fresh conversation + add the new member.
        let mock = makeMock()
        mock.insertConversationResult = conversation(id: "grp1", name: "")
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.addMember(conversationId: "c1", newUserId: "u2")
        await waitUntil { !mock.insertedConversations.isEmpty }
        XCTAssertEqual(mock.insertedConversations.first?.userFrom, "u1")
        await waitUntil { vm.navigateToConversation == "grp1" }
        XCTAssertEqual(vm.navigateToConversation, "grp1")
        XCTAssertTrue(mock.insertedParticipants.contains { $0.userId == "u2" })
    }

    func testRemoveMemberSelfCallsRepoDelete() async {
        // Removing yourself (target == me) skips the thread reload, so no live channel opens.
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.removeMember(conversationId: "c1", targetUserId: "u1")
        await waitUntil { !mock.deletedParticipants.isEmpty }
        XCTAssertEqual(mock.deletedParticipants.first?.conversationId, "c1")
        XCTAssertEqual(mock.deletedParticipants.first?.userId, "u1")
    }

    func testRenameConversationCallsRepoAndTrims() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.renameConversation(id: "c1", name: "  New Name  ")
        await waitUntil { !mock.renamedConversations.isEmpty }
        XCTAssertEqual(mock.renamedConversations.first?.id, "c1")
        XCTAssertEqual(mock.renamedConversations.first?.name, "New Name")
    }

    func testDeleteConversationCallsRepoAndFlags() async {
        let mock = makeMock()
        mock.conversationsResult = [conversation(id: "c1")]
        let vm = makeVM(mock)
        await waitUntil { vm.conversations.contains { $0.conversation.id == "c1" } }

        mock.conversationsResult = []
        vm.deleteConversation("c1")
        await waitUntil { mock.deletedConversationIds.contains("c1") }
        XCTAssertEqual(mock.deletedConversationIds.first, "c1")
        XCTAssertTrue(mock.removedGroupImageIds.contains("c1"))
        await waitUntil { vm.conversationDeleted }
        XCTAssertTrue(vm.conversationDeleted)
    }

    // MARK: - Reactions

    func testToggleReactionAddsOptimisticallyAndCallsRepo() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.toggleReaction(messageId: "m1", conversationId: "c1", emoji: "❤️")
        await waitUntil { !mock.addedReactions.isEmpty }
        XCTAssertEqual(mock.addedReactions.first?.messageId, "m1")
        XCTAssertEqual(mock.addedReactions.first?.emoji, "❤️")
        XCTAssertTrue(vm.reactions["m1"]?["❤️"]?.contains("u1") ?? false)
    }

    func testToggleReactionRemovesWhenSameEmoji() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.toggleReaction(messageId: "m1", conversationId: "c1", emoji: "❤️")
        await waitUntil { !mock.addedReactions.isEmpty }
        vm.toggleReaction(messageId: "m1", conversationId: "c1", emoji: "❤️")
        await waitUntil { !mock.removedReactions.isEmpty }
        XCTAssertEqual(mock.removedReactions.first, "m1")
        await waitUntil { vm.reactions["m1"] == nil }
        XCTAssertNil(vm.reactions["m1"])
    }
}
