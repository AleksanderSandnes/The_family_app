// Delete-account state for the Settings screen. On success the repository clears the
// local session and RootViewModel switches to the signed-out flow, so there is no
// success state to render here.
import Foundation
import Observation

@Observable
@MainActor
final class DeleteAccountViewModel {
    private(set) var isDeleting = false
    var failed = false

    private let repo: FamilyRepositoryProtocol

    init(repo: FamilyRepositoryProtocol? = nil) {
        self.repo = repo ?? FamilyRepository.shared
    }

    func deleteAccount() {
        guard !isDeleting else { return }
        isDeleting = true
        failed = false
        Task {
            do {
                try await repo.deleteAccount()
            } catch {
                failed = true
            }
            isDeleting = false
        }
    }
}
