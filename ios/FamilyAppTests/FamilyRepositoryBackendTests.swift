@testable import FamilyApp

// FamilyRepository against the stubbed Supabase transport: family membership, the user
// cache, relations, presence and push tokens. Every response is fictional.
import XCTest

@MainActor
final class FamilyRepositoryBackendTests: XCTestCase {
    private let repo = FamilyRepository.shared

    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
    }

    override func tearDown() async throws {
        repo.pushTokenProvider = nil
        repo.forgetPushToken()
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    // MARK: - User cache

    func testGetUserCachesSuccessfulFetches() async {
        StubTransport.respond(table: "users", with: [UserModel(id: "user-lars", name: "Lars Nordmann")])
        let first = await repo.getUser("user-lars")
        let second = await repo.getUser("user-lars")
        XCTAssertEqual(first?.name, "Lars Nordmann")
        XCTAssertEqual(second?.name, "Lars Nordmann")
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/users").count, 1)
        XCTAssertTrue(StubTransport.requests[0].hasFilter("id", "eq.user-lars"))
    }

    func testGetUserDoesNotCacheMisses() async {
        let missing = await repo.getUser("user-ghost")
        XCTAssertNil(missing)
        StubTransport.respond(table: "users", with: [UserModel(id: "user-ghost", name: "Found later")])
        let found = await repo.getUser("user-ghost")
        XCTAssertEqual(found?.name, "Found later")
    }

    func testInvalidateUserCacheForcesRefetch() async {
        StubTransport.respond(table: "users", with: [UserModel(id: "user-nora", name: "Nora")])
        _ = await repo.getUser("user-nora")
        repo.invalidateUserCache()
        _ = await repo.getUser("user-nora")
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/users").count, 2)
    }

    func testGetFamilyMembersFiltersByFamily() async {
        StubTransport.respond(
            table: "users",
            with: [
                UserModel(id: "a", name: "Emma", familyId: "fam-1"),
                UserModel(id: "b", name: "Lars", familyId: "fam-1"),
            ]
        )
        let members = await repo.getFamilyMembers(familyId: "fam-1")
        XCTAssertEqual(members.map(\.name), ["Emma", "Lars"])
        XCTAssertTrue(StubTransport.requests[0].hasFilter("family_id", "eq.fam-1"))
    }

    // MARK: - Family

    func testGetFamilyAndAdminCheck() async {
        StubTransport.respond(table: "users", with: [UserModel(id: "user-emma", familyId: "fam-1")])
        StubTransport.respond(
            table: "families",
            with: [FamilyModel(id: "fam-1", name: "Nordmann", adminId: "user-emma")]
        )
        let family = await repo.getFamily(familyId: "fam-1")
        let isAdmin = await repo.isFamilyAdmin(userId: "user-emma")
        XCTAssertEqual(family?.name, "Nordmann")
        XCTAssertTrue(isAdmin)
    }

    func testIsFamilyAdminIsFalseWithoutFamily() async {
        StubTransport.respond(table: "users", with: [UserModel(id: "user-solo")])
        let isAdmin = await repo.isFamilyAdmin(userId: "user-solo")
        XCTAssertFalse(isAdmin)
    }

    func testCreateFamilyInsertsAssignsAndSyncsBirthday() async throws {
        StubTransport.respond(
            "POST",
            "/rest/v1/families",
            body: StubJSON.encode(FamilyModel(id: "fam-new", name: "Nordmann", joinCode: "DEMO42"))
        )
        StubTransport.respond(
            table: "users",
            with: [UserModel(id: "user-emma", name: "Emma", birthday: "1988-04-12", familyId: "fam-new")]
        )
        var changes = repo.familyChanged().makeAsyncIterator()

        let familyId = try await repo.createFamily(name: "  Nordmann ", code: "DEMO42", userId: "user-emma")

        XCTAssertEqual(familyId, "fam-new")
        let insert = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/families", method: "POST").first)
        XCTAssertEqual(insert.jsonObject["name"] as? String, "Nordmann")
        XCTAssertEqual(insert.jsonObject["join_code"] as? String, "DEMO42")
        let assign = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/users", method: "PATCH").first)
        XCTAssertEqual(assign.jsonObject["family_id"] as? String, "fam-new")
        let birthday = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/birthdays", method: "POST").first)
        XCTAssertEqual(birthday.jsonObject["name"] as? String, "Emma's birthday")
        let changed: Void? = await changes.next()
        XCTAssertNotNil(changed)
    }

    func testJoinFamilyUsesRpcAndUpdatesExistingBirthday() async throws {
        StubTransport.respond("POST", "/rest/v1/rpc/join_family", body: StubJSON.literal("fam-1"))
        StubTransport.respond(
            table: "users",
            with: [UserModel(id: "user-lars", name: "Lars", birthday: "1986-09-03", familyId: "fam-1")]
        )
        StubTransport.respond(table: "birthdays", with: [BirthdayModel(id: "bday-1", name: "Old", date: "1986-09-03")])

        let familyId = try await repo.joinFamily(code: " DEMO42 ", userId: "user-lars")

        XCTAssertEqual(familyId, "fam-1")
        let rpc = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/rpc/join_family").first)
        XCTAssertEqual(rpc.jsonObject["p_code"] as? String, "DEMO42")
        let update = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/birthdays", method: "PATCH").first)
        XCTAssertTrue(update.hasFilter("id", "eq.bday-1"))
    }

    func testJoinFamilyWithUnknownCodeThrows() async {
        StubTransport.respond("POST", "/rest/v1/rpc/join_family", body: Data("null".utf8))
        do {
            _ = try await repo.joinFamily(code: "NOPE", userId: "user-lars")
            XCTFail("Expected joinCodeIncorrect")
        } catch {
            XCTAssertEqual(error as? RepositoryError, .joinCodeIncorrect)
        }
    }

    func testLeaveFamilyCleansUpSoloConversationsAndOwnedRows() async throws {
        StubTransport.respond(table: "users", with: [UserModel(id: "user-jonas", familyId: "fam-1")])
        StubTransport.respond(
            table: "conversation_participants",
            with: [
                ConversationParticipantModel(id: "p1", conversationId: "solo", userId: "user-jonas"),
                ConversationParticipantModel(id: "p2", conversationId: "group", userId: "user-jonas"),
                ConversationParticipantModel(id: "p3", conversationId: "group", userId: "user-emma"),
            ]
        )

        await repo.leaveFamily(userId: "user-jonas")

        let deletedConversation = try XCTUnwrap(
            StubTransport.requests(to: "/rest/v1/conversations", method: "DELETE").first
        )
        XCTAssertTrue(deletedConversation.hasFilter("id", "in.(solo)"))
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/calendar_events", method: "DELETE").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/birthdays", method: "DELETE").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/shopping_lists", method: "DELETE").count, 1)
        let clear = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/users", method: "PATCH").first)
        XCTAssertTrue(clear.jsonObject.keys.contains("family_id"))
    }

    func testLeaveFamilyWithoutFamilyOnlyClearsMembership() async {
        StubTransport.respond(table: "users", with: [UserModel(id: "user-solo")])
        await repo.leaveFamily(userId: "user-solo")
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/users", method: "PATCH").count, 1)
        XCTAssertTrue(StubTransport.requests(to: "/rest/v1/calendar_events").isEmpty)
    }

    func testLeaveFamilyForUnknownUserDoesNothing() async {
        await repo.leaveFamily(userId: "user-ghost")
        XCTAssertTrue(StubTransport.requests(to: "/rest/v1/users", method: "PATCH").isEmpty)
    }

    func testUpdateProfileWritesFieldsAndResyncsBirthday() async throws {
        StubTransport.respond(
            table: "users",
            with: [UserModel(id: "user-emma", name: "Emma", birthday: "1988-04-12", familyId: "fam-1")]
        )
        let update = ProfileUpdate(
            name: "Emma",
            email: "emma@example.com",
            birthday: "1988-04-12",
            mobile: "+47 400 00 000",
            avatarUrl: nil
        )
        await repo.updateProfile(userId: "user-emma", update: update)
        let write = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/users", method: "PATCH").first)
        XCTAssertEqual(write.jsonObject["mobile"] as? String, "+47 400 00 000")
        XCTAssertTrue(write.jsonObject["avatar_url"] is NSNull)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/birthdays", method: "POST").count, 1)
    }

    func testRemoveRenameAndPhotoWrites() async throws {
        try await repo.removeFamilyMember(memberId: "user-jonas")
        try await repo.renameFamily(familyId: "fam-1", newName: "  Nordmann  ")
        try await repo.updateFamilyPhoto(familyId: "fam-1", photoUrl: "https://fictional.example/photo.jpg")
        let familyWrites = StubTransport.requests(to: "/rest/v1/families", method: "PATCH")
        XCTAssertEqual(familyWrites.first?.jsonObject["name"] as? String, "Nordmann")
        XCTAssertEqual(familyWrites.last?.jsonObject["photo_url"] as? String, "https://fictional.example/photo.jpg")
        XCTAssertTrue(StubTransport.requests(to: "/rest/v1/users", method: "PATCH")[0].hasFilter("id", "eq.user-jonas"))
    }

    func testUploadFamilyPhotoReturnsCacheBustedPublicURL() async throws {
        let url = try await repo.uploadFamilyPhotoImage(familyId: "fam-1", data: Data("pixels".utf8))
        XCTAssertTrue(url.contains("/storage/v1/object/public/group-images/family-photos/fam-1/photo.jpg?t="))
        let uploads = StubTransport.requests(to: "/storage/v1/object/group-images/family-photos/fam-1/photo.jpg")
        XCTAssertFalse(uploads.isEmpty)
    }

    // MARK: - Relations

    func testRelationsReadAndWrite() async throws {
        StubTransport.respond(
            table: "family_relations",
            with: [
                FamilyRelationModel(id: "r1", fromUserId: "me", toUserId: "lars", relation: "Husband"),
                FamilyRelationModel(id: "r2", fromUserId: "me", toUserId: "lars", relation: "Duplicate"),
            ]
        )
        let relations = await repo.getMyRelations(userId: "me")
        XCTAssertEqual(relations, ["lars": "Husband"])

        await repo.setRelation(fromUserId: "me", toUserId: "nora", familyId: "fam-1", relation: " Daughter ")
        await repo.setRelation(fromUserId: "me", toUserId: "nora", familyId: "fam-1", relation: "   ")
        let upsert = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/family_relations", method: "POST").first)
        XCTAssertEqual(upsert.jsonObject["relation"] as? String, "Daughter")
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/family_relations", method: "DELETE").count, 1)
    }

    // MARK: - Join code, presence, push

    func testPendingJoinCodeIsConsumedOnce() {
        repo.setPendingJoinCode("DEMO42")
        XCTAssertEqual(repo.consumePendingJoinCode(), "DEMO42")
        XCTAssertNil(repo.consumePendingJoinCode())
    }

    func testPresenceAndPushTokensRequireASignedInUser() async {
        SessionStore.shared.signOut()
        repo.pushTokenProvider = { "fictional-fcm-token" }
        await repo.touchLastActive()
        await repo.syncPushToken()
        XCTAssertTrue(StubTransport.requests.isEmpty)
    }

    func testPresenceAndPushTokenLifecycle() async throws {
        SessionStore.shared.signIn(userId: "user-emma")
        repo.pushTokenProvider = { "fictional-fcm-token" }

        await repo.touchLastActive()
        await repo.syncPushToken()
        await repo.unregisterPushToken()

        let presence = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/users", method: "PATCH").first)
        XCTAssertNotNil(presence.jsonObject["last_active_at"])
        let upsert = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/device_push_tokens", method: "POST").first)
        XCTAssertEqual(upsert.jsonObject["platform"] as? String, "ios")
        let delete = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/device_push_tokens", method: "DELETE").first)
        XCTAssertTrue(delete.hasFilter("token", "eq.fictional-fcm-token"))
    }

    func testUnregisterWithoutAnyTokenIsANoOp() async {
        await repo.unregisterPushToken()
        XCTAssertTrue(StubTransport.requests.isEmpty)
    }

    // MARK: - Palette

    func testPaletteMatchesJavaHashCodeSelection() {
        XCTAssertEqual(FamilyRepository.javaHashCode(""), 0)
        XCTAssertEqual(FamilyRepository.javaHashCode("a"), 97)
        XCTAssertTrue(FamilyRepository.avatarColors.contains(FamilyRepository.palette("Emma Nordmann")))
        XCTAssertEqual(RepositoryError.joinCodeIncorrect.errorDescription, "Join code is incorrect.")
        XCTAssertEqual(RepositoryError.notAuthenticated.errorDescription, "Not signed in.")
        XCTAssertEqual(RepositoryError.profileNotFound.errorDescription, "User profile not found.")
    }
}
