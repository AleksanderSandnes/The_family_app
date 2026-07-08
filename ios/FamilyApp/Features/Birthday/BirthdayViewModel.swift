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

    private let repo: FamilyRepositoryProtocol
    private let observer: RealtimeObserving
    private var subscribedFamilyId: String?
    private var familyChangedTask: Task<Void, Never>?

    init(
        repo: FamilyRepositoryProtocol = FamilyRepository.shared,
        realtime: @MainActor () -> RealtimeObserving = { RealtimeObserver() }
    ) {
        self.repo = repo
        observer = realtime()
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

        let result = await (try? repo.fetchBirthdays(userId: userId, familyId: user.familyId)) ?? birthdays
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

            await repo.insertBirthday(temp)
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
            await repo.updateBirthday(id: id, name: name, date: date, icon: icon, color: color)
            await reload()
        }
    }

    func delete(_ birthday: BirthdayModel) {
        Task {
            birthdays.removeAll { $0.id == birthday.id }
            await repo.deleteBirthday(id: birthday.id)
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
