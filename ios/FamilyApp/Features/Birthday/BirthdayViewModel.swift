// Birthday view model — the iOS twin of BirthdayViewModel.kt.
import Foundation
import Observation
import Supabase

@Observable
@MainActor
final class BirthdayViewModel {
    private static var cache: [BirthdayModel] = []

    private(set) var birthdays: [BirthdayModel] = BirthdayViewModel.cache
    private(set) var isLoading = false

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    private let observer = RealtimeObserver()
    private var subscribedFamilyId: String?
    private var familyChangedTask: Task<Void, Never>?

    init() {
        Task { await load() }
        familyChangedTask = Task { [weak self] in
            guard let stream = self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.load()
            }
        }
    }

    isolated deinit {
        familyChangedTask?.cancel()
    }

    func refresh() {
        Task { await reload() }
    }

    /// Fetch birthdays without touching the subscription.
    private func reload() async {
        guard let userId = repo.session.currentUserId else {
            birthdays = []
            return
        }
        if birthdays.isEmpty { isLoading = true }
        defer { isLoading = false }
        guard let user = await repo.getUser(userId) else { return }

        let result: [BirthdayModel]
        if let familyId = user.familyId {
            let fetched: [BirthdayModel] = await (try? client.from("birthdays")
                .select()
                .or("made_by_user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value) ?? birthdays
            result = fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        } else {
            result = await (try? client.from("birthdays")
                .select()
                .eq("made_by_user_id", value: userId)
                .execute()
                .value) ?? birthdays
        }
        Self.cache = result
        birthdays = result
    }

    /// Full load: reload then subscribe once per family.
    private func load() async {
        await reload()
        guard let userId = repo.session.currentUserId,
              let familyId = await repo.getUser(userId)?.familyId,
              subscribedFamilyId != familyId
        else { return }
        subscribedFamilyId = familyId
        observer.start(
            table: "birthdays",
            scope: familyId,
            filter: .eq("family_id", value: familyId)
        ) { [weak self] in
            await self?.reload()
        }
    }

    // MARK: - Mutations

    /// The current app user id — lets the screen gate editing to the birthday's creator.
    var currentUserId: String? {
        repo.session.currentUserId
    }

    func add(name: String, date: String, icon: String, color: Int?) {
        Task {
            guard let userId = repo.session.currentUserId,
                  let user = await repo.getUser(userId) else { return }
            var temp = BirthdayModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.name = name
            temp.date = date
            temp.familyId = user.familyId
            temp.madeByUserId = userId
            temp.icon = icon
            temp.color = color
            birthdays.append(temp)

            var payload: [String: AnyJSON] = [
                "name": .string(name),
                "date": .string(date),
                "made_by_user_id": .string(userId),
                "icon": .string(icon),
                "color": color.map { AnyJSON.double(Double($0)) } ?? .null,
            ]
            if let familyId = user.familyId { payload["family_id"] = .string(familyId) }
            _ = try? await client.from("birthdays").insert(payload).execute()
            await reload()
        }
    }

    func update(id: String, name: String, date: String, icon: String, color: Int?) {
        Task {
            birthdays = birthdays.map { existing in
                var existing = existing
                if existing.id == id {
                    existing.name = name
                    existing.date = date
                    existing.icon = icon
                    existing.color = color
                }
                return existing
            }
            _ = try? await client.from("birthdays")
                .update([
                    "name": AnyJSON.string(name),
                    "date": .string(date),
                    "icon": .string(icon),
                    "color": color.map { AnyJSON.double(Double($0)) } ?? .null,
                ])
                .eq("id", value: id)
                .execute()
            await reload()
        }
    }

    func delete(_ birthday: BirthdayModel) {
        Task {
            birthdays.removeAll { $0.id == birthday.id }
            _ = try? await client.from("birthdays").delete().eq("id", value: birthday.id).execute()
            await reload()
        }
    }
}

/// Sort ascending by next occurrence — mirrors the sortedWith in BirthdayScreen.kt.
func sortedByNextBirthday(_ birthdays: [BirthdayModel], today: LocalDate = .today()) -> [BirthdayModel] {
    let farFuture = LocalDate(year: 9999, month: 12, day: 31)
    return birthdays.sorted { lhs, rhs in
        (nextBirthdayDate(lhs.date, today: today) ?? farFuture)
            < (nextBirthdayDate(rhs.date, today: today) ?? farFuture)
    }
}
