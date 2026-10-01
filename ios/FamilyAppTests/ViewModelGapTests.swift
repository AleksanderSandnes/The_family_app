@testable import FamilyApp

// View-model paths the behaviour suites left open: avatar and family-photo uploads (real
// StorageService over the stub transport), family create/join outcomes, chat editing,
// replies, media sends and 1:1 reuse, auth code resends and deep-link routing.
import UIKit
import XCTest

@MainActor
final class ViewModelGapTests: XCTestCase {
    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    private func jpeg() -> Data {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { context in
            UIColor.systemPurple.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
        return image.jpegData(compressionQuality: 0.9)!
    }

    private struct Failure: LocalizedError {
        var errorDescription: String? {
            "Fictional failure"
        }
    }

    // MARK: - Profile avatar

    func testSaveAvatarUploadsToTheAuthFolderAndUpdatesTheProfile() async throws {
        try await StubSupabase.signIn()
        let mock = DemoFamily.mock()
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { vm.user != nil }

        vm.saveAvatar(imageData: jpeg())
        await waitUntil { !mock.updatedProfiles.isEmpty }

        let avatar = try XCTUnwrap(mock.updatedProfiles.last?.update.avatarUrl)
        XCTAssertTrue(avatar.contains("/avatars/\(StubSupabase.authUserID)/avatar.jpg?t="))
        XCTAssertEqual(vm.user?.avatarUrl, avatar)
        XCTAssertFalse(vm.isUploading)
    }

    func testSaveCapturedAvatarImage() async throws {
        try await StubSupabase.signIn()
        let mock = DemoFamily.mock()
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { vm.user != nil }

        try vm.saveAvatar(image: XCTUnwrap(UIImage(data: jpeg())))
        await waitUntil { !mock.updatedProfiles.isEmpty }

        XCTAssertNotNil(mock.updatedProfiles.last?.update.avatarUrl)
    }

    func testAvatarErrorsSurfaceAndClear() async {
        let mock = DemoFamily.mock()
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { vm.user != nil }

        vm.saveAvatar(imageData: Data("not an image".utf8))
        XCTAssertNotNil(vm.error)
        vm.clearError()
        XCTAssertNil(vm.error)

        // Valid bytes but no auth session: StorageService refuses, the VM reports it.
        vm.saveAvatar(imageData: jpeg())
        await waitUntil { vm.error != nil }
        XCTAssertNotNil(vm.error)
        XCTAssertTrue(mock.updatedProfiles.isEmpty)
    }

    func testRemoveAvatarDeletesTheFileAndClearsTheURL() async throws {
        try await StubSupabase.signIn()
        let mock = DemoFamily.mock()
        var emma = DemoFamily.emma
        emma.avatarUrl = "https://fictional.example/avatar.jpg"
        mock.users[emma.id] = emma
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { vm.user != nil }

        vm.removeAvatar()
        await waitUntil { !mock.updatedProfiles.isEmpty }

        XCTAssertNil(mock.updatedProfiles.last?.update.avatarUrl)
        XCTAssertNil(vm.user?.avatarUrl)
        XCTAssertFalse(StubTransport.requests(to: "/storage/v1/object/avatars", method: "DELETE").isEmpty)
    }

    // MARK: - Family

    func testCreateAndJoinFamilyOutcomes() async {
        let mock = DemoFamily.mock()
        let vm = FamilyViewModel(repo: mock)
        await waitUntil { vm.family != nil }

        vm.createFamily(name: "Nordmann", code: "DEMO42")
        await waitUntil { mock.renamedFamilies.contains { $0.name == "Nordmann" } }

        vm.joinFamily(code: "DEMO42")
        await waitUntil { vm.promptRelationSetup }
        XCTAssertTrue(vm.promptRelationSetup)

        mock.createFamilyError = Failure()
        vm.createFamily(name: "Again", code: "X")
        await waitUntil { vm.error != nil }
        XCTAssertEqual(vm.error, "Fictional failure")
        vm.clearError()
        XCTAssertNil(vm.error)

        mock.joinFamilyError = RepositoryError.joinCodeIncorrect
        vm.joinFamily(code: "NOPE")
        await waitUntil { vm.error != nil }
        XCTAssertEqual(vm.error, RepositoryError.joinCodeIncorrect.errorDescription)
    }

    func testUploadFamilyPhoto() async {
        let mock = DemoFamily.mock()
        let vm = FamilyViewModel(repo: mock)
        await waitUntil { vm.family != nil }

        vm.uploadFamilyPhoto(jpeg())
        await waitUntil { !mock.updatedFamilyPhotos.isEmpty }
        XCTAssertEqual(mock.uploadedFamilyPhotos, [DemoFamily.familyId])
        XCTAssertEqual(mock.updatedFamilyPhotos.first?.url, mock.familyPhotoURLResult)

        vm.uploadFamilyPhoto(Data("broken".utf8))
        await waitUntil { vm.error != nil }
        XCTAssertNotNil(vm.error)
    }

    // MARK: - Chat

    func testEditingAndDeletingMessages() async {
        let mock = DemoFamily.mock()
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        vm.messages = mock.messagesByConversation["chat-1"] ?? []
        let target = vm.messages[1]

        vm.startEditing(target)
        vm.cancelEditing()
        vm.commitEdit(newText: "ignored without an edit in progress")
        vm.startEditing(target)
        vm.commitEdit(newText: "  \(target.text)  ")
        XCTAssertTrue(mock.editedMessages.isEmpty)

        vm.startEditing(target)
        vm.commitEdit(newText: "Oat milk and bread, please!")
        await waitUntil { !mock.editedMessages.isEmpty }
        XCTAssertEqual(vm.messages[1].text, "Oat milk and bread, please!")
        XCTAssertNotNil(vm.messages[1].editedAt)

        vm.deleteMessage(target)
        await waitUntil { !mock.deletedMessages.isEmpty }
        XCTAssertFalse(vm.messages.contains { $0.id == target.id })
        XCTAssertEqual(mock.deletedMessages, [target.id])
    }

    func testRepliesAndSendFailures() async {
        let mock = DemoFamily.mock()
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        let original = mock.messagesByConversation["chat-1"]?.first ?? MessageModel()

        vm.setReplyTo(original)
        vm.clearReplyTo()
        XCTAssertNil(vm.replyTo)
        vm.setReplyTo(original)
        vm.send(conversationId: "chat-1", text: "On it")
        await waitUntil { !mock.insertedTextMessages.isEmpty }
        XCTAssertEqual(mock.insertedTextMessages.first?.replyToId, original.id)
        XCTAssertNil(vm.replyTo)

        mock.insertTextMessageError = Failure()
        vm.send(conversationId: "chat-1", text: "Will fail")
        await waitUntil { vm.errorMessage != nil }
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertFalse(vm.messages.contains { $0.text == "Will fail" })
    }

    func testImageAndVoiceSends() async throws {
        let mock = DemoFamily.mock()
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })

        vm.sendImage(conversationId: "chat-1", data: jpeg(), filename: "photo.jpg")
        await waitUntil { vm.errorMessage != nil }
        XCTAssertNotNil(vm.errorMessage, "uploads need an auth session")

        try await StubSupabase.signIn()
        vm.errorMessage = nil
        vm.sendImage(conversationId: "chat-1", data: jpeg(), filename: "photo.jpg")
        vm.sendVoice(conversationId: "chat-1", data: Data("m4a".utf8), filename: "note.m4a")
        await waitUntil { !mock.insertedImageMessages.isEmpty && !mock.insertedVoiceMessages.isEmpty }
        XCTAssertTrue(mock.insertedImageMessages[0].mediaUrl.hasSuffix("/chat-1/\(StubSupabase.authUserID)/photo.jpg"))
        XCTAssertTrue(mock.insertedVoiceMessages[0].mediaUrl.hasSuffix("/note.m4a"))
        XCTAssertNil(vm.errorMessage)
    }

    func testVoiceSendWithoutSessionReportsAnError() async {
        let mock = DemoFamily.mock()
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        vm.sendVoice(conversationId: "chat-1", data: Data("m4a".utf8), filename: "note.m4a")
        await waitUntil { vm.errorMessage != nil }
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertTrue(mock.insertedVoiceMessages.isEmpty)
    }

    func testOneOnOneConversationsAreReused() async {
        let mock = DemoFamily.mock()
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()

        // Direct conversation with Lars exists as user_from/user_to.
        vm.createConversation(name: "", memberIds: [DemoFamily.lars.id])
        await waitUntil { vm.navigateToConversation != nil }
        XCTAssertEqual(vm.navigateToConversation, "chat-2")
    }

    func testOneOnOneFoundThroughSharedParticipants() async {
        let mock = DemoFamily.mock()
        let pair = [DemoFamily.emma, DemoFamily.nora].map {
            ConversationParticipantModel(id: "pair-\($0.id)", conversationId: "chat-3", userId: $0.id)
        }
        mock.conversationsResult.append(ConversationModel(id: "chat-3", userFrom: DemoFamily.emma.id, name: "Nora"))
        mock.participantsByConversation["chat-3"] = pair
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()

        vm.createConversation(name: "", memberIds: [DemoFamily.nora.id])
        await waitUntil { vm.navigateToConversation != nil }
        XCTAssertEqual(vm.navigateToConversation, "chat-3")
    }

    // MARK: - Auth resends

    func testResendCodesStartCooldownsAndReportFailures() async {
        let mock = MockRepository()
        let vm = AuthViewModel(repo: mock)
        vm.resetEmail = "emma@example.com"
        vm.verifyEmail = "emma@example.com"

        vm.resendResetCode()
        await waitUntil { !vm.loading }
        vm.resendResetCode()
        vm.resendSignupCode()
        await waitUntil { !vm.loading }
        vm.resendSignupCode()
        XCTAssertEqual(mock.resetEmailCalls, ["emma@example.com"])
        XCTAssertEqual(mock.resendSignupCalls, ["emma@example.com"])

        let failing = MockRepository()
        failing.sendResetError = Failure()
        failing.resendSignupError = Failure()
        let failingVM = AuthViewModel(repo: failing)
        failingVM.resendResetCode()
        await waitUntil { failingVM.error != nil }
        XCTAssertNotNil(failingVM.error)
        failingVM.clearError()
        failingVM.resendSignupCode()
        await waitUntil { failingVM.error != nil }
        XCTAssertNotNil(failingVM.error)
    }

    // MARK: - Deep links

    func testDeepLinkRouting() throws {
        let mock = MockRepository()
        let router = DeepLinkRouter(repo: mock)

        try router.handle(XCTUnwrap(URL(string: "familyapp://chat/chat-1")))
        try router.handle(XCTUnwrap(URL(string: "familyapp://join?code=DEMO42")))
        try router.handle(XCTUnwrap(URL(string: "familyapp://wishlist?token=tok-1")))
        try router.handle(XCTUnwrap(URL(string: "familyapp://auth?code=fictional-code")))
        try router.handle(XCTUnwrap(URL(string: "familyapp://unknown")))

        XCTAssertEqual(router.pendingConversationId, "chat-1")
        XCTAssertEqual(mock.consumePendingJoinCode(), "DEMO42")
        XCTAssertEqual(router.pendingWishlistShareToken, "tok-1")
    }
}
