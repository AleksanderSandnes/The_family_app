// Cached app identity is a profile hint; authenticated restoration opens navigation.
import Foundation
import Observation

enum AuthGate: Equatable {
    case loading
    case signedOut
    case needsPermissions
    case signedIn
}

@Observable
@MainActor
final class RootViewModel {
    private let store: SessionStore
    private let repo: FamilyRepositoryProtocol
    private var bootstrapped = false
    private var restoring = false
    private var verifiedAuthID: String?
    private var verifiedAppID: String?
    private var syncedAppID: String?
    private var generation = 0
    private var authListener: Task<Void, Never>?
    private var profileTask: Task<Void, Never>?

    init(repo: FamilyRepositoryProtocol? = nil) {
        let repository = repo ?? FamilyRepository.shared
        self.repo = repository
        store = repository.session
    }

    isolated deinit {
        authListener?.cancel()
        profileTask?.cancel()
    }

    var gate: AuthGate {
        if !bootstrapped {
            return .loading
        }
        guard let verifiedAuthID, let verifiedAppID,
              repo.currentAuthUserID() == verifiedAuthID, store.currentUserId == verifiedAppID
        else { return .signedOut }
        return store.permissionsRequested ? .signedIn : .needsPermissions
    }

    func bootstrap() async {
        guard !bootstrapped, !restoring else { return }
        restoring = true
        defer { restoring = false }
        authListener?.cancel()
        let events = repo.authSessionEvents()
        authListener = Task { [weak self] in
            for await event in events {
                guard !Task.isCancelled else { break }
                self?.receive(event)
            }
        }
        let revision = generation
        do {
            let authID = try await repo.restoreAuthSession()
            guard generation == revision, !Task.isCancelled else { return }
            await resolveProfile(authID: authID, revision: revision)
        } catch {
            guard generation == revision, !Task.isCancelled else { return }
            closeGate()
        }
    }

    func receive(_ event: AuthSessionEvent) {
        generation += 1
        profileTask?.cancel()
        switch event {
        case .signedOut:
            closeGate()
        case let .authenticated(authID):
            // Token refresh does not require a second profile lookup for the same
            // already verified account. Offline refresh failures keep that binding.
            if verifiedAuthID == authID, store.currentUserId == verifiedAppID, verifiedAppID != nil {
                bootstrapped = true
                return
            }
            verifiedAuthID = nil
            verifiedAppID = nil
            syncedAppID = nil
            let revision = generation
            profileTask = Task { [weak self] in
                guard let self else { return }
                await resolveProfile(authID: authID, revision: revision)
            }
        }
    }

    private func resolveProfile(authID: String, revision: Int) async {
        do {
            let appID = try await repo.resolveAuthenticatedAppUserID()
            guard generation == revision, !Task.isCancelled,
                  repo.currentAuthUserID() == authID else { return }
            store.signIn(userId: appID)
            verifiedAuthID = authID
            verifiedAppID = appID
            bootstrapped = true
            await repo.touchLastActive()
        } catch {
            guard generation == revision, !Task.isCancelled else { return }
            closeGate()
        }
    }

    private func closeGate() {
        verifiedAuthID = nil
        verifiedAppID = nil
        syncedAppID = nil
        store.signOut()
        repo.invalidateUserCache()
        bootstrapped = true
    }

    func onSignedIn() async {
        guard case .signedIn = gate, let appID = verifiedAppID, syncedAppID != appID else { return }
        syncedAppID = appID
        await repo.syncPushToken()
        guard verifiedAppID == appID, case .signedIn = gate else { return }
        await repo.syncNotificationPrefsToServer()
    }

    func completePermissionsOnboarding() {
        store.setPermissionsRequested()
    }
}
