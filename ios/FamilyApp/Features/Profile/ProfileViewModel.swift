// Profile view model — the iOS twin of ProfileViewModel.kt. Avatar uploads go to
// avatars/{auth_uid}/avatar.jpg via StorageService (dual-ID rule) with a cache-busting
// query param, EXIF-normalized and downscaled first.
import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class ProfileViewModel {
    private(set) var user: UserModel?
    var error: String?
    private(set) var isUploading = false

    private let repo = FamilyRepository.shared

    init() {
        refresh()
    }

    func clearError() {
        error = nil
    }

    func refresh() {
        Task {
            guard let userId = repo.session.currentUserId else {
                user = nil
                return
            }
            user = await repo.getUser(userId)
        }
    }

    func saveAvatar(imageData: Data) {
        guard let compressed = ImageUtils.compressWithOrientation(imageData) else {
            error = "Could not read the selected photo."
            return
        }
        uploadAvatar(compressed)
    }

    func saveAvatar(image: UIImage) {
        guard let compressed = ImageUtils.compressWithOrientation(image) else {
            error = "Could not read the captured photo."
            return
        }
        uploadAvatar(compressed)
    }

    private func uploadAvatar(_ bytes: Data) {
        Task {
            guard let userId = repo.session.currentUserId, let current = user else { return }
            isUploading = true
            defer { isUploading = false }
            do {
                let url = try await StorageService.uploadAvatar(data: bytes, filename: "avatar.jpg")
                let cacheBusted = url + "?t=\(Int(Date().timeIntervalSince1970 * 1000))"
                await repo.updateProfile(userId: userId, update: ProfileUpdate(
                    name: current.name,
                    email: current.email,
                    birthday: current.birthday,
                    mobile: current.mobile,
                    avatarUrl: cacheBusted
                ))
                var updated = current
                updated.avatarUrl = cacheBusted
                user = updated
            } catch {
                self.error = "Failed to update photo. Please try again."
            }
        }
    }

    func removeAvatar() {
        Task {
            guard let userId = repo.session.currentUserId, let current = user else { return }
            do {
                try await StorageService.deleteAvatar(filename: "avatar.jpg")
                await repo.updateProfile(userId: userId, update: ProfileUpdate(
                    name: current.name,
                    email: current.email,
                    birthday: current.birthday,
                    mobile: current.mobile,
                    avatarUrl: nil
                ))
                var updated = current
                updated.avatarUrl = nil
                user = updated
            } catch {
                // Best-effort, mirrors Android's logged runCatching.
            }
        }
    }

    func save(name: String, email: String, birthday: String, mobile: String) {
        Task {
            guard let userId = repo.session.currentUserId, let current = user else { return }
            let update = ProfileUpdate(
                name: name.trimmingCharacters(in: .whitespaces),
                email: email.trimmingCharacters(in: .whitespaces),
                birthday: birthday.trimmingCharacters(in: .whitespaces),
                mobile: mobile.trimmingCharacters(in: .whitespaces),
                avatarUrl: current.avatarUrl
            )
            await repo.updateProfile(userId: userId, update: update)
            var updated = current
            updated.name = update.name
            updated.email = update.email
            updated.birthday = update.birthday
            updated.mobile = update.mobile
            user = updated
        }
    }

    func signOut() {
        Task { await repo.signOut() }
    }
}

/// "MMMM d, yyyy" or the raw string / em-dash — mirrors formatBirthday in ProfileScreens.kt.
func formatBirthday(_ raw: String?) -> String {
    guard let raw, !raw.isEmpty else { return "—" }
    guard let date = LocalDate(iso: raw) else { return raw }
    let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86400)
    let formatter = DateFormatter()
    formatter.dateFormat = "MMMM d, yyyy"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(identifier: "UTC")
    return formatter.string(from: instant)
}
