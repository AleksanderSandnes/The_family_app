// Location data access — moved out of FamilyMapViewModel so the VM depends only on the
// FamilyRepositoryProtocol seam and can be unit-tested with a mock. Each method preserves
// the exact query semantics and payload fields of the original inline `client.from(...)`
// calls on `user_locations`.
import Foundation
import Supabase

extension FamilyRepository {
    /// Visible member locations for a family. Throws so the caller keeps its current list
    /// (no-rollback parity via `(try? …) ?? locations`). The caller filters out its own row.
    func fetchUserLocations(familyId: String) async throws -> [UserLocationModel] {
        try await client.from("user_locations")
            .select()
            .eq("family_id", value: familyId)
            .eq("visible", value: true)
            .execute()
            .value
    }

    /// Upserts the current user's location. `updated_at` is stamped server-side-parity here
    /// (isoNow()) so call sites don't build storage payloads. Preserves the exact fields.
    func upsertUserLocation(_ location: UserLocationModel) async {
        _ = try? await client.from("user_locations").upsert([
            "user_id": AnyJSON.string(location.userId),
            "family_id": location.familyId.map { AnyJSON.string($0) } ?? .null,
            "lat": .double(location.lat),
            "lng": .double(location.lng),
            "display_name": .string(location.displayName),
            "visible": .bool(location.visible),
            "updated_at": .string(isoNow()),
        ]).execute()
    }

    /// Hides the current user's pin (sets visible = false). Update only.
    func clearUserLocationVisibility(userId: String) async {
        _ = try? await client.from("user_locations")
            .update(["visible": AnyJSON.bool(false)])
            .eq("user_id", value: userId)
            .execute()
    }
}
