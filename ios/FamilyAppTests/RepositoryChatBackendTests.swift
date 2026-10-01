@testable import FamilyApp

// FamilyRepository chat helpers against the stubbed Supabase transport: reads, writes,
// moderation, reactions and group images. Every response is fictional.
import XCTest

@MainActor
final class RepositoryChatBackendTests: XCTestCase {
    private let repo = FamilyRepository.shared

    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
        SessionStore.shared.signIn(userId: "user-emma")
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    // MARK: - Reads

    func testConversationAndMessageReads() async throws {
        StubTransport.respond(table: "conversations", with: [ConversationModel(id: "chat-1", name: "Family Nordmann")])
        StubTransport.respond(
            table: "messages",
            with: [MessageModel(id: "m1", conversationId: "chat-1", userFrom: "user-lars", text: "Tacos?")]
        )

        let conversations = try await repo.fetchConversations()
        let single = try await repo.fetchConversation(id: "chat-1")
        let messages = try await repo.fetchMessages(conversationId: "chat-1")
        let last = await repo.getLastMessage(conversationId: "chat-1")

        XCTAssertEqual(conversations.map(\.name), ["Family Nordmann"])
        XCTAssertEqual(single.first?.id, "chat-1")
        XCTAssertEqual(messages.map(\.text), ["Tacos?"])
        XCTAssertEqual(last?.id, "m1")
        let reads = StubTransport.requests(to: "/rest/v1/messages")
        XCTAssertTrue(reads.allSatisfy { $0.hasFilter("conversation_id", "eq.chat-1") })
        XCTAssertTrue(reads.contains { $0.query.contains("limit=1") })
    }

    func testParticipantUserAndReactionReads() async throws {
        StubTransport.respond(
            table: "conversation_participants",
            with: [ConversationParticipantModel(id: "p1", conversationId: "chat-1", userId: "user-emma")]
        )
        StubTransport.respond(table: "users", with: [UserModel(id: "user-lars", name: "Lars")])
        StubTransport.respond(
            table: "message_reactions",
            with: [
                MessageReactionModel(id: "r1", messageId: "m1", conversationId: "c1", userId: "lars", emoji: "🌮"),
            ]
        )

        let mine = try await repo.fetchMyParticipants(userId: "user-emma", conversationIds: ["chat-1"])
        let many = try await repo.fetchParticipants(conversationIds: ["chat-1", "chat-2"])
        let one = try await repo.fetchParticipants(conversationId: "chat-1")
        let byUser = try await repo.fetchParticipants(userId: "user-emma")
        let users = try await repo.fetchUsers(ids: ["user-lars"])
        let reactions = try await repo.fetchReactions(conversationId: "chat-1")

        XCTAssertEqual([mine, many, one, byUser].map(\.count), [1, 1, 1, 1])
        XCTAssertEqual(users.first?.name, "Lars")
        XCTAssertEqual(reactions.first?.emoji, "🌮")
        let participantReads = StubTransport.requests(to: "/rest/v1/conversation_participants")
        XCTAssertTrue(participantReads[1].hasFilter("conversation_id", "in.(chat-1,chat-2)"))
    }

    func testEmptyIdListsSkipTheNetwork() async throws {
        let mine = try await repo.fetchMyParticipants(userId: "user-emma", conversationIds: [])
        let participants = try await repo.fetchParticipants(conversationIds: [])
        let users = try await repo.fetchUsers(ids: [])
        XCTAssertTrue(mine.isEmpty && participants.isEmpty && users.isEmpty)
        XCTAssertTrue(StubTransport.requests.isEmpty)
    }

    func testUnreadCountReadsTheExactCountHeader() async throws {
        StubTransport.respond("HEAD", "/rest/v1/messages", body: Data(), headers: ["Content-Range": "*/3"])
        let unread = await repo.countUnreadMessages(conversationId: "chat-1", userId: "user-emma", after: "2026-10-01")
        XCTAssertEqual(unread, 3)
        let request = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/messages").first)
        XCTAssertTrue(request.hasFilter("user_from", "neq.user-emma"))
        XCTAssertTrue(request.hasFilter("sent_at", "gt.2026-10-01"))
    }

    func testUnreadCountFallsBackToZero() async {
        StubTransport.respond("HEAD", "/rest/v1/messages", status: 500, body: Data())
        let unread = await repo.countUnreadMessages(conversationId: "chat-1", userId: "user-emma", after: "x")
        XCTAssertEqual(unread, 0)
    }

    // MARK: - Message writes

    func testSendEditDeleteAndReadReceipt() async throws {
        try await repo.sendMessage(conversationId: "chat-1", text: "On my way")
        try await repo.editMessage(messageId: "m1", newText: "On my way!")
        try await repo.deleteMessage(messageId: "m1")
        await repo.markConversationRead(conversationId: "chat-1")

        let insert = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/messages", method: "POST").first)
        XCTAssertEqual(insert.jsonObject["user_from"] as? String, "user-emma")
        XCTAssertEqual(insert.jsonObject["message_type"] as? String, "text")
        let edit = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/messages", method: "PATCH").first)
        XCTAssertNotNil(edit.jsonObject["edited_at"])
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/messages", method: "DELETE").count, 1)
        let receipt = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/conversation_participants").first)
        XCTAssertTrue(receipt.hasFilter("user_id", "eq.user-emma"))
    }

    func testTypedMessageInsertsCarryTheirKind() async throws {
        try await repo.insertTextMessage(conversationId: "chat-1", userFrom: "user-emma", text: "Hi", replyToId: "m0")
        try await repo.insertTextMessage(conversationId: "chat-1", userFrom: "user-emma", text: "Hi", replyToId: nil)
        await repo.insertSystemMessage(conversationId: "chat-1", userFrom: "user-emma", text: "Lars joined")
        try await repo.insertImageMessage(conversationId: "chat-1", userFrom: "user-emma", mediaUrl: "chat-media/a.jpg")
        try await repo.insertVoiceMessage(conversationId: "chat-1", userFrom: "user-emma", mediaUrl: "chat-media/a.m4a")

        let inserts = StubTransport.requests(to: "/rest/v1/messages", method: "POST").map(\.jsonObject)
        XCTAssertEqual(inserts[0]["reply_to_id"] as? String, "m0")
        XCTAssertNil(inserts[1]["reply_to_id"])
        XCTAssertEqual(inserts[2]["message_type"] as? String, "system")
        XCTAssertEqual(inserts[3]["message_type"] as? String, "image")
        XCTAssertEqual(inserts[4]["message_type"] as? String, "voice")
    }

    func testSignedOutWritesThrowNotAuthenticated() async {
        SessionStore.shared.signOut()
        await assertNotAuthenticated { try await self.repo.sendMessage(conversationId: "c", text: "t") }
        await assertNotAuthenticated { try await self.repo.blockUser(userId: "u") }
        await assertNotAuthenticated {
            try await self.repo.addReaction(messageId: "m", conversationId: "c", emoji: "👍")
        }
        await assertNotAuthenticated { try await self.repo.removeReaction(messageId: "m") }
        await repo.markConversationRead(conversationId: "c")
        XCTAssertTrue(StubTransport.requests.isEmpty)
    }

    // MARK: - Moderation and reactions

    func testBlockUnblockReportAndReactions() async throws {
        StubTransport.respond("GET", "/rest/v1/user_blocks", body: StubJSON.literal([["blocked_id": "user-x"]]))

        let blocked = try await repo.fetchBlockedUserIds()
        try await repo.blockUser(userId: "user-x")
        try await repo.unblockUser(userId: "user-x")
        try await repo.reportMessage(messageId: "m1", reason: ReportReason.spam, details: "  spam  ")
        try await repo.addReaction(messageId: "m1", conversationId: "chat-1", emoji: "👍")
        try await repo.removeReaction(messageId: "m1")

        XCTAssertEqual(blocked, ["user-x"])
        let block = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/user_blocks", method: "POST").first)
        XCTAssertEqual(block.jsonObject["blocker_id"] as? String, "user-emma")
        let report = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/rpc/report_message").first)
        XCTAssertEqual(report.jsonObject["p_details"] as? String, "spam")
        XCTAssertEqual(report.jsonObject["p_reason"] as? String, ReportReason.spam.rawValue)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/message_reactions", method: "POST").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/message_reactions", method: "DELETE").count, 1)
    }

    // MARK: - Conversations and group images

    func testConversationLifecycleWrites() async throws {
        StubTransport.respond(
            "POST",
            "/rest/v1/conversations",
            body: StubJSON.encode(ConversationModel(id: "chat-new", userFrom: "user-emma", name: "Cabin trip"))
        )

        let created = try await repo.insertConversation(userFrom: "user-emma", name: "Cabin trip", familyId: "fam-1")
        _ = try await repo.insertConversation(userFrom: "user-emma", name: "Direct", familyId: nil)
        try await repo.insertParticipant(conversationId: "chat-new", userId: "user-lars")
        await repo.deleteParticipant(conversationId: "chat-new", userId: "user-lars")
        await repo.renameConversation(id: "chat-new", name: "Cabin weekend")
        try await repo.setConversationImage(id: "chat-new", url: "group-images/chat-new/image.jpg")
        await repo.clearConversationImage(id: "chat-new")
        try await repo.deleteConversation(id: "chat-new")

        XCTAssertEqual(created.id, "chat-new")
        let inserts = StubTransport.requests(to: "/rest/v1/conversations", method: "POST").map(\.jsonObject)
        XCTAssertEqual(inserts[0]["family_id"] as? String, "fam-1")
        XCTAssertNil(inserts[1]["family_id"])
        let updates = StubTransport.requests(to: "/rest/v1/conversations", method: "PATCH").map(\.jsonObject)
        XCTAssertEqual(updates[0]["name"] as? String, "Cabin weekend")
        XCTAssertEqual(updates[1]["image_uri"] as? String, "group-images/chat-new/image.jpg")
        XCTAssertTrue(updates[2]["image_uri"] is NSNull)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/conversations", method: "DELETE").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/conversation_participants", method: "DELETE").count, 1)
    }

    func testGroupImageUploadAndRemoval() async throws {
        let url = try await repo.uploadGroupImage(conversationId: "chat-1", data: Data("pixels".utf8))
        await repo.removeGroupImage(conversationId: "chat-1")
        XCTAssertTrue(url.contains("/storage/v1/object/public/group-images/chat-1/image.jpg?t="))
        XCTAssertFalse(StubTransport.requests(to: "/storage/v1/object/group-images/chat-1/image.jpg").isEmpty)
        XCTAssertFalse(StubTransport.requests(to: "/storage/v1/object/group-images", method: "DELETE").isEmpty)
    }

    private func assertNotAuthenticated(
        _ operation: () async throws -> Void,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        do {
            try await operation()
            XCTFail("Expected notAuthenticated", file: file, line: line)
        } catch {
            XCTAssertEqual(error as? RepositoryError, .notAuthenticated, file: file, line: line)
        }
    }
}
