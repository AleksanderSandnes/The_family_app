// Family storage access — moved out of FamilyViewModel so the VM depends only on the
// FamilyRepositoryProtocol seam and can be unit-tested with a mock. Preserves the exact
// bucket + path convention of the original inline `client.storage` call. The other family
// operations (rename/leave/remove/setRelation/updateFamilyPhoto) already live on the repo.
import Foundation
import Supabase

extension FamilyRepository {
    /// Uploads a family photo (upsert) to `group-images/family-photos/{familyId}/photo.jpg`
    /// and returns its public URL with a cache-busting query param — mirrors the Android
    /// bucket + path convention. Throws so the caller can surface a friendly error.
    func uploadFamilyPhotoImage(familyId: String, data: Data) async throws -> String {
        let bucket = client.storage.from("group-images")
        let path = "family-photos/\(familyId)/photo.jpg"
        try await bucket.upload(path, data: data, options: FileOptions(upsert: true))
        let cacheBuster = Int(Date().timeIntervalSince1970 * 1000)
        return try bucket.getPublicURL(path: path).absoluteString + "?t=\(cacheBuster)"
    }
}
