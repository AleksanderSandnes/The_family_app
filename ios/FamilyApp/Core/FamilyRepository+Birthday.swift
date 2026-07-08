// Birthday data access — moved out of BirthdayViewModel so the VM depends only on the
// FamilyRepositoryProtocol seam and can be unit-tested with a mock.
import Foundation
import Supabase

extension FamilyRepository {
    /// Birthdays visible to the user: made-by-me OR in my family. Throws on query failure so
    /// the caller can keep the current list (no-rollback parity via `(try? …) ?? birthdays`).
    func fetchBirthdays(userId: String, familyId: String?) async throws -> [BirthdayModel] {
        if let familyId {
            let fetched: [BirthdayModel] = try await client.from("birthdays")
                .select()
                .or("made_by_user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value
            return fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        }
        return try await client.from("birthdays")
            .select()
            .eq("made_by_user_id", value: userId)
            .execute()
            .value
    }

    func insertBirthday(_ birthday: BirthdayModel) async {
        var payload: [String: AnyJSON] = [
            "name": .string(birthday.name),
            "date": .string(birthday.date),
            "made_by_user_id": .string(birthday.madeByUserId),
            "icon": .string(birthday.icon),
            "color": birthday.color.map { AnyJSON.double(Double($0)) } ?? .null,
        ]
        if let familyId = birthday.familyId { payload["family_id"] = .string(familyId) }
        _ = try? await client.from("birthdays").insert(payload).execute()
    }

    func updateBirthday(id: String, name: String, date: String, icon: String, color: Int?) async {
        _ = try? await client.from("birthdays")
            .update([
                "name": AnyJSON.string(name),
                "date": .string(date),
                "icon": .string(icon),
                "color": color.map { AnyJSON.double(Double($0)) } ?? .null,
            ])
            .eq("id", value: id)
            .execute()
    }

    func deleteBirthday(id: String) async {
        _ = try? await client.from("birthdays").delete().eq("id", value: id).execute()
    }
}
