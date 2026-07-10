@testable import FamilyApp

// Pure-logic tests for chat time formatting, display-name, and preview resolution.
import XCTest

final class ChatTimeFormattersTests: XCTestCase {
    /// Fixed "now": 2026-07-05T12:00:00Z
    private let now: Int64 = 1783252800000

    func testRelativeTimeBuckets() {
        XCTAssertEqual(relativeTime("2026-07-05T11:59:30Z", nowMs: now), "now")
        XCTAssertEqual(relativeTime("2026-07-05T11:45:00Z", nowMs: now), "15m ago")
        XCTAssertEqual(relativeTime("2026-07-05T09:00:00Z", nowMs: now), "3h ago")
        XCTAssertEqual(relativeTime("2026-07-04T09:00:00Z", nowMs: now), "Yesterday")
        XCTAssertEqual(relativeTime("garbage", nowMs: now), "")
    }

    func testMessageTimeLabelToday() {
        // Same day → bare time (locale-stable h:mm a).
        let label = messageTimeLabel("2026-07-05T09:30:00Z", nowMs: now)
        XCTAssertFalse(label.contains("Yesterday"))
        XCTAssertTrue(label.contains(":30"), label)
    }

    func testMessageTimeLabelYesterday() {
        XCTAssertTrue(messageTimeLabel("2026-07-04T09:30:00Z", nowMs: now).hasPrefix("Yesterday "))
    }

    func testPresenceLabel() {
        XCTAssertEqual(presenceLabel("2026-07-05T11:59:00Z", nowMs: now), "Active now")
        XCTAssertEqual(presenceLabel("2026-07-05T11:30:00Z", nowMs: now), "Active 30m ago")
        XCTAssertNil(presenceLabel(nil, nowMs: now))
        XCTAssertNil(presenceLabel("garbage", nowMs: now))
    }

    func testMessageSeen() {
        XCTAssertTrue(messageSeen(otherLastRead: "2026-07-05T10:00:00Z", sentAt: "2026-07-05T09:59:00Z"))
        XCTAssertTrue(messageSeen(otherLastRead: "2026-07-05T10:00:00Z", sentAt: "2026-07-05T10:00:00Z"))
        XCTAssertFalse(messageSeen(otherLastRead: "2026-07-05T10:00:00Z", sentAt: "2026-07-05T10:00:01Z"))
        XCTAssertFalse(messageSeen(otherLastRead: nil, sentAt: "2026-07-05T10:00:00Z"))
    }

    func testGapExceedsTenMinutes() {
        XCTAssertFalse(gapExceedsTenMinutes(
            earlierIso: "2026-07-05T10:00:00Z", laterIso: "2026-07-05T10:10:00Z"
        ))
        XCTAssertTrue(gapExceedsTenMinutes(
            earlierIso: "2026-07-05T10:00:00Z", laterIso: "2026-07-05T10:10:01Z"
        ))
        XCTAssertFalse(gapExceedsTenMinutes(earlierIso: "bad", laterIso: "worse"))
    }

    func testParsesFractionalSeconds() {
        XCTAssertNotNil(parseInstantMs("2026-07-05T10:00:00.123456Z"))
        XCTAssertNotNil(parseInstantMs("2026-07-05T10:00:00Z"))
        XCTAssertNil(parseInstantMs("2026-07-05"))
    }
}

final class ConversationDisplayTests: XCTestCase {
    private func user(_ id: String, _ name: String) -> UserModel {
        var user = UserModel()
        user.id = id
        user.name = name
        return user
    }

    private func conversation(name: String = "", userTo: String? = nil) -> ConversationModel {
        var conv = ConversationModel()
        conv.name = name
        conv.userTo = userTo
        return conv
    }

    func testOneOnOneShowsOtherName() {
        let name = conversationDisplayName(
            conversation: conversation(),
            participants: [user("me", "Aleksander Sandnes"), user("u2", "Kari Sandnes")],
            currentUserId: "me"
        )
        XCTAssertEqual(name, "Kari Sandnes")
    }

    func testGroupUsesExplicitName() {
        let name = conversationDisplayName(
            conversation: conversation(name: "Family"),
            participants: [user("me", "A"), user("u2", "B"), user("u3", "C")],
            currentUserId: "me"
        )
        XCTAssertEqual(name, "Family")
    }

    func testUnnamedGroupJoinsFirstNames() {
        let name = conversationDisplayName(
            conversation: conversation(),
            participants: [
                user("me", "Aleksander Sandnes"),
                user("u2", "Kari Sandnes"),
                user("u3", "Ola Sandnes"),
            ],
            currentUserId: "me"
        )
        XCTAssertEqual(name, "Kari, Ola")
    }

    func testEmptyEverythingFallsBack() {
        XCTAssertEqual(
            conversationDisplayName(
                conversation: conversation(),
                participants: [],
                currentUserId: "me"
            ),
            "Group chat"
        )
    }

    func testPreviewText() {
        XCTAssertEqual(conversationPreviewText(lastMessage: nil, lastSenderName: nil), "No messages yet")

        var image = MessageModel()
        image.messageType = "image"
        XCTAssertEqual(conversationPreviewText(lastMessage: image, lastSenderName: "You"), "You: Photo")

        var voice = MessageModel()
        voice.messageType = "voice"
        XCTAssertEqual(
            conversationPreviewText(lastMessage: voice, lastSenderName: "Kari"),
            "Kari: Voice message"
        )

        var text = MessageModel()
        text.text = "hei"
        XCTAssertEqual(conversationPreviewText(lastMessage: text, lastSenderName: nil), "hei")
    }
}
