// Report/block moderation: pure visibility rules plus ChatViewModel behaviour via
// MockRepository (no live Supabase). Backend contract: supabase/security/moderation.sql.
@testable import FamilyApp
import XCTest

@MainActor
final class ChatModerationTests: XCTestCase {
    private struct Failure: Error {}

    private func message(_ id: String, from: String, type: String = "text") -> MessageModel {
        var msg = MessageModel()
        msg.id = id
        msg.conversationId = "c1"
        msg.userFrom = from
        msg.text = "fictional"
        msg.messageType = type
        return msg
    }

    private func makeVM(_ mock: MockRepository) -> ChatViewModel {
        ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    private func makeMock() -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: "emma")
        return mock
    }

    func testBlockedSendersAreHiddenButSystemMessagesRemain() {
        let list = [
            message("1", from: "emma"),
            message("2", from: "lars"),
            message("3", from: "lars", type: "system"),
            message("4", from: "nora"),
        ]
        XCTAssertEqual(ChatModeration.visibleMessages(list, blocked: ["lars"]).map(\.id), ["1", "3", "4"])
        XCTAssertEqual(ChatModeration.visibleMessages(list, blocked: []).map(\.id), ["1", "2", "3", "4"])
    }

    func testOnlyOtherPeoplesPersistedMessagesCanBeModerated() {
        XCTAssertTrue(ChatModeration.canModerate(message("1", from: "lars"), myId: "emma"))
        XCTAssertFalse(ChatModeration.canModerate(message("1", from: "emma"), myId: "emma"))
        XCTAssertFalse(ChatModeration.canModerate(message("1", from: "lars"), myId: nil))
        XCTAssertFalse(ChatModeration.canModerate(message("1", from: "lars", type: "system"), myId: "emma"))
        XCTAssertFalse(ChatModeration.canModerate(message("temp-1", from: "lars"), myId: "emma"))
    }

    func testReportReasonsMatchTheDatabaseConstraint() {
        XCTAssertEqual(ReportReason.allCases.map(\.rawValue), ["spam", "harassment", "inappropriate", "other"])
    }

    func testBlockHidesMessagesAndUnblockRestoresThem() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        vm.messages = [message("1", from: "emma"), message("2", from: "lars")]

        await vm.blockUser("lars", name: "Lars")
        XCTAssertEqual(mock.blockedUsers, ["lars"])
        XCTAssertEqual(vm.visibleMessages.map(\.id), ["1"])
        XCTAssertNotNil(vm.noticeMessage)

        await vm.unblockUser("lars", name: "Lars")
        XCTAssertEqual(mock.unblockedUsers, ["lars"])
        XCTAssertEqual(vm.visibleMessages.map(\.id), ["1", "2"])
    }

    func testFailedBlockRollsBackAndReportsError() async {
        let mock = makeMock()
        mock.moderationError = Failure()
        let vm = makeVM(mock)
        vm.messages = [message("2", from: "lars")]

        await vm.blockUser("lars", name: "Lars")
        XCTAssertFalse(vm.blockedUserIds.contains("lars"))
        XCTAssertEqual(vm.visibleMessages.count, 1)
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertNil(vm.noticeMessage)
    }

    func testFailedUnblockKeepsTheBlock() async {
        let mock = makeMock()
        mock.blockedIdsResult = ["lars"]
        let vm = makeVM(mock)
        await vm.loadBlocks()
        mock.moderationError = Failure()

        await vm.unblockUser("lars", name: "Lars")
        XCTAssertTrue(vm.blockedUserIds.contains("lars"))
        XCTAssertNotNil(vm.errorMessage)
    }

    func testReportSendsReasonAndConfirms() async {
        let mock = makeMock()
        let vm = makeVM(mock)

        await vm.reportMessage(message("2", from: "lars"), reason: .harassment, details: "fictional")
        XCTAssertEqual(mock.reports.count, 1)
        XCTAssertEqual(mock.reports.first?.messageId, "2")
        XCTAssertEqual(mock.reports.first?.reason, .harassment)
        XCTAssertNotNil(vm.noticeMessage)
    }

    func testFailedReportShowsError() async {
        let mock = makeMock()
        mock.moderationError = Failure()
        let vm = makeVM(mock)

        await vm.reportMessage(message("2", from: "lars"), reason: .spam)
        XCTAssertTrue(mock.reports.isEmpty)
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertNil(vm.noticeMessage)
    }
}
