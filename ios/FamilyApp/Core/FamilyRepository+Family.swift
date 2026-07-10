// Family storage access behind the FamilyRepositoryProtocol seam, so the view model is
// unit-testable with a mock. Other family operations
// (rename/leave/remove/setRelation/updateFamilyPhoto) live on the repo.
import Foundation
import Supabase

extension FamilyRepository {
    /// Uploads a family photo (upsert) to `group-images/family-photos/{familyId}/photo.jpg`
    /// and returns its public URL with a cache-busting query param. Throws so the caller can
    /// surface a friendly error.
    func uploadFamilyPhotoImage(familyId: String, data: Data) async throws -> String {
        let bucket = client.storage.from("group-images")
        let path = "family-photos/\(familyId)/photo.jpg"
        try await bucket.upload(path, data: data, options: FileOptions(upsert: true))
        let cacheBuster = Int(Date().timeIntervalSince1970 * 1000)
        return try bucket.getPublicURL(path: path).absoluteString + "?t=\(cacheBuster)"
    }
}
