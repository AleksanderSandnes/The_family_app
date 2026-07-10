// Calendar data access behind the FamilyRepositoryProtocol seam, so the view model is
// unit-testable with a mock.
import Foundation
import Supabase

extension FamilyRepository {
    /// Calendar events visible to the user: own OR in my family. Throws on query failure so
    /// the caller keeps the current list via `(try? …) ?? events`; no rollback.
    func fetchCalendarEvents(userId: String, familyId: String?) async throws -> [CalendarEventModel] {
        if let familyId {
            let fetched: [CalendarEventModel] = try await client.from("calendar_events")
                .select()
                .or("user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value
            // Filters out foreign-family rows the .or() may include.
            return fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        }
        let fetched: [CalendarEventModel] = try await client.from("calendar_events")
            .select()
            .eq("user_id", value: userId)
            .execute()
            .value
        return fetched.filter { $0.familyId == nil }
    }

    func insertCalendarEvent(_ event: CalendarEventModel) async {
        var payload: [String: AnyJSON] = [
            "user_id": .string(event.userId),
            "activity": .string(event.activity),
            "all_day": .bool(event.allDay),
            "date_from": .string(event.dateFrom),
            "date_to": .string(event.dateTo),
            "time_from": .string(event.timeFrom),
            "time_to": .string(event.timeTo),
            "icon": .string(event.icon),
            "is_private": .bool(event.isPrivate),
            "color": event.color.map { AnyJSON.double(Double($0)) } ?? .null,
            "attendee_ids": .array(event.attendeeIds.map { AnyJSON.string($0) }),
        ]
        if let familyId = event.familyId { payload["family_id"] = .string(familyId) }
        _ = try? await client.from("calendar_events").insert(payload).execute()
    }

    func updateCalendarEvent(_ event: CalendarEventModel) async {
        _ = try? await client.from("calendar_events")
            .update([
                "activity": AnyJSON.string(event.activity),
                "all_day": .bool(event.allDay),
                "date_from": .string(event.dateFrom),
                "date_to": .string(event.dateTo),
                "time_from": .string(event.timeFrom),
                "time_to": .string(event.timeTo),
                "icon": .string(event.icon),
                "is_private": .bool(event.isPrivate),
                "color": event.color.map { AnyJSON.double(Double($0)) } ?? .null,
                "attendee_ids": .array(event.attendeeIds.map { AnyJSON.string($0) }),
            ])
            .eq("id", value: event.id)
            .execute()
    }

    func deleteCalendarEvent(id: String) async {
        _ = try? await client.from("calendar_events").delete().eq("id", value: id).execute()
    }
}
