// Family view model — the iOS twin of FamilyViewModel.kt.
import Foundation
import Observation
import Supabase

private let joinCodeLength = 8

@Observable
@MainActor
final class FamilyViewModel {
    private(set) var family: FamilyModel?
    private(set) var members: [UserModel] = []
    private(set) var currentUser: UserModel?
    /// My relation to each other member (their id → relation label), from my perspective.
    private(set) var relations: [String: String] = [:]
    var error: String?
    private(set) var isUploading = false

    /// Invite code captured from a deep link; FamilyScreen opens the join flow with it.
    var pendingJoinCode: String? {
        repo.pendingJoinCode
    }

    func consumePendingJoinCode() -> String? {
        repo.consumePendingJoinCode()
    }

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    init() {
        refresh()
    }

    func refresh() {
        Task { await load() }
    }

    private func load() async {
        guard let userId = repo.session.currentUserId else {
            family = nil
            members = []
            currentUser = nil
            return
        }
        guard let user = await repo.getUser(userId) else { return }
        currentUser = user
        if let familyId = user.familyId {
            family = await repo.getFamily(familyId: familyId)
            members = await repo.getFamilyMembers(familyId: familyId)
            relations = await repo.getMyRelations(userId: userId)
        } else {
            family = nil
            members = []
            relations = [:]
        }
    }

    /// Sets my relation ("Dad", "Wife", …) to another member, or clears it when empty.
    func setRelation(toUserId: String, relation: String) {
        guard let userId = repo.session.currentUserId, let familyId = family?.id else { return }
        // Optimistic local update.
        if relation.trimmingCharacters(in: .whitespaces).isEmpty {
            relations.removeValue(forKey: toUserId)
        } else {
            relations[toUserId] = relation
        }
        Task {
            await repo.setRelation(
                fromUserId: userId, toUserId: toUserId, familyId: familyId, relation: relation
            )
        }
    }

    func clearError() {
        error = nil
    }

    func createFamily(name: String, code: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            do {
                _ = try await repo.createFamily(name: name, code: code, userId: userId)
                await load()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    /// Set right after joining a family with existing members — FamilyScreen shows the
    /// "set your relations" prompt so the newcomer can label the people already there.
    var promptRelationSetup = false

    func joinFamily(code: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            do {
                _ = try await repo.joinFamily(code: code, userId: userId)
                await load()
                // Offer to set relations to the members already in the family.
                if members.contains(where: { $0.id != userId }) {
                    promptRelationSetup = true
                }
            } catch {
                self.error = (error as? RepositoryError)?.errorDescription ?? error.localizedDescription
            }
        }
    }

    func leaveFamily() {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            await repo.leaveFamily(userId: userId)
            await load()
        }
    }

    func renameFamily(_ newName: String) {
        Task {
            guard let familyId = family?.id else { return }
            do {
                try await repo.renameFamily(familyId: familyId, newName: newName)
                await load()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    func uploadFamilyPhoto(_ imageData: Data) {
        Task {
            guard let familyId = family?.id else { return }
            isUploading = true
            defer { isUploading = false }
            guard let compressed = ImageUtils.compressWithOrientation(imageData) else {
                error = L("Could not read the selected photo.")
                return
            }
            do {
                // Same bucket + path convention as Android: group-images/family-photos/{id}/photo.jpg
                let bucket = client.storage.from("group-images")
                let path = "family-photos/\(familyId)/photo.jpg"
                try await bucket.upload(path, data: compressed, options: FileOptions(upsert: true))
                let cacheBuster = Int(Date().timeIntervalSince1970 * 1000)
                let url = try bucket.getPublicURL(path: path).absoluteString + "?t=\(cacheBuster)"
                try await repo.updateFamilyPhoto(familyId: familyId, photoUrl: url)
                await load()
            } catch {
                self.error = L("Failed to update photo. Please try again.")
            }
        }
    }

    func removeMember(_ memberId: String) {
        Task {
            do {
                try await repo.removeFamilyMember(memberId: memberId)
                await load()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

/// 8-char uppercase invite code — mirrors generateJoinCode in FamilyViewModel.kt.
func generateJoinCode() -> String {
    String(UUID().uuidString.prefix(joinCodeLength)).uppercased()
}

/// Share-sheet invite message — mirrors the Android share text. Pass `appLocale` so
/// the chrome around the (verbatim) family name and code renders in the app language.
func inviteMessage(
    familyName: String,
    joinCode: String,
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    let link = DeepLinkURL.invite(code: joinCode).absoluteString
    return L(
        """
        Join our family "\(familyName)" on The Family App!

        Tap to join: \(link)

        Or open the app, tap "Join with Invite Code", and enter: \(joinCode)
        """,
        locale: locale
    )
}
