// Shopping view model — the iOS twin of ShoppingViewModel.kt. Establishes the
// template every realtime feature follows:
//   - load → subscribe-once (guarded per familyId/listId)
//   - realtime event → pure data reload, never re-subscribe
//   - mutations: optimistic local update (temp UUID) → network call → reload
import Foundation
import Observation
import Supabase

/// Per-list shopping progress: items bought out of total.
struct ListProgress: Equatable {
    let bought: Int
    let total: Int
}

@Observable
@MainActor
final class ShoppingViewModel {
    /// Session cache so re-entering the screen renders instantly (Android companion cache).
    private static var cache: [ShoppingListModel] = []

    private(set) var lists: [ShoppingListModel] = ShoppingViewModel.cache
    private(set) var isLoading = false
    private(set) var selectedList: ShoppingListModel?
    private(set) var items: [ShoppingItemModel] = []
    private(set) var listProgress: [String: ListProgress] = [:]

    private let repo: FamilyRepositoryProtocol
    private let listsObserver: RealtimeObserving
    private let itemsObserver: RealtimeObserving
    private var subscribedListsFamilyId: String?
    private var subscribedItemsListId: String?
    private var familyChangedTask: Task<Void, Never>?

    init(
        repo: FamilyRepositoryProtocol? = nil,
        realtime: @MainActor () -> RealtimeObserving = { RealtimeObserver() }
    ) {
        self.repo = repo ?? FamilyRepository.shared
        listsObserver = realtime()
        itemsObserver = realtime()
        Task { await load() }
        familyChangedTask = Task { [weak self] in
            guard let stream = self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.reloadLists()
            }
        }
    }

    isolated deinit {
        familyChangedTask?.cancel()
    }

    /// Re-fetch from the server (screen resume / pull-to-refresh). Does NOT re-subscribe.
    func refresh() {
        Task { await reloadLists() }
    }

    private func load() async {
        guard repo.session.currentUserId != nil else {
            lists = []
            listProgress = [:]
            return
        }
        if lists.isEmpty { isLoading = true }
        await reloadLists()
        isLoading = false
    }

    /// Pure data reload — also subscribes when a family becomes known (once per familyId).
    private func reloadLists() async {
        guard let userId = repo.session.currentUserId else { return }
        let user = await repo.getUser(userId)
        let familyId = user?.familyId

        let result = await (try? repo.fetchShoppingLists(userId: userId, familyId: familyId)) ?? lists
        Self.cache = result
        lists = result
        await loadListProgress(listIds: result.map(\.id))

        if let familyId { subscribeToListsOnce(familyId: familyId) }
    }

    /// Loads bought/total progress per list in one query.
    private func loadListProgress(listIds: [String]) async {
        guard !listIds.isEmpty else {
            listProgress = [:]
            return
        }
        guard let allItems = try? await repo.fetchShoppingItems(listIds: listIds) else { return }
        listProgress = Dictionary(grouping: allItems, by: \.listId).mapValues { items in
            ListProgress(bought: items.count(where: \.checked), total: items.count)
        }
    }

    /// Subscribe to the lists channel at most once per familyId.
    private func subscribeToListsOnce(familyId: String) {
        guard subscribedListsFamilyId != familyId else { return }
        subscribedListsFamilyId = familyId
        listsObserver.start(
            table: "shopping_lists",
            scope: familyId,
            filter: .eq("family_id", value: familyId)
        ) { [weak self] in
            await self?.reloadLists()
        }
    }

    // MARK: - Detail

    func loadListDetail(_ listId: String) {
        Task {
            await reloadItems(listId: listId)
            subscribeToItemsOnce(listId: listId)
        }
    }

    /// Pure data reload for the detail screen — no subscribe.
    private func reloadItems(listId: String) async {
        async let listFetch = (try? repo.fetchShoppingList(id: listId)) ?? []
        async let itemsFetch = (try? repo.fetchShoppingItems(listId: listId)) ?? []
        if let list = await listFetch.first { selectedList = list }
        items = await itemsFetch
    }

    /// Subscribe to the items channel at most once per listId.
    private func subscribeToItemsOnce(listId: String) {
        guard subscribedItemsListId != listId else { return }
        subscribedItemsListId = listId
        itemsObserver.start(
            table: "shopping_items",
            scope: listId,
            filter: .eq("list_id", value: listId)
        ) { [weak self] in
            await self?.reloadItems(listId: listId)
        }
    }

    // MARK: - List mutations (optimistic → call → reload)

    func addList(title: String, icon: String = "shopping_cart", color: Int? = nil) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            let user = await repo.getUser(userId)
            var temp = ShoppingListModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.title = title
            temp.ownerUserId = userId
            temp.familyId = user?.familyId
            temp.icon = icon
            temp.color = color
            lists.append(temp)

            await repo.insertShoppingList(temp)
            await reloadLists()
        }
    }

    func changeListColor(listId: String, color: Int?) {
        Task {
            lists = lists.map { list in
                var list = list
                if list.id == listId { list.color = color }
                return list
            }
            selectedList?.color = color
            await repo.setShoppingListColor(id: listId, color: color)
            await reloadLists()
        }
    }

    func changeListIcon(listId: String, icon: String) {
        Task {
            lists = lists.map { list in
                var list = list
                if list.id == listId { list.icon = icon }
                return list
            }
            selectedList?.icon = icon
            await repo.setShoppingListIcon(id: listId, icon: icon)
            await reloadLists()
        }
    }

    func renameList(listId: String, newTitle: String) {
        Task {
            selectedList?.title = newTitle
            await repo.renameShoppingList(id: listId, title: newTitle)
            await reloadItems(listId: listId)
        }
    }

    func deleteList(_ list: ShoppingListModel) {
        Task {
            lists.removeAll { $0.id == list.id }
            await repo.deleteShoppingList(id: list.id)
            await reloadLists()
        }
    }

    // MARK: - Item mutations

    func addItem(listId: String, item: String) {
        Task {
            var temp = ShoppingItemModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.listId = listId
            temp.item = item
            items.append(temp)
            await repo.insertShoppingItem(temp)
            await reloadItems(listId: listId)
        }
    }

    func toggle(_ item: ShoppingItemModel) {
        Task {
            items = items.map { existing in
                var existing = existing
                if existing.id == item.id { existing.checked = !item.checked }
                return existing
            }
            await repo.setShoppingItemChecked(id: item.id, checked: !item.checked)
            await reloadItems(listId: item.listId)
        }
    }

    func renameItem(_ item: ShoppingItemModel, newName: String) {
        Task {
            items = items.map { existing in
                var existing = existing
                if existing.id == item.id { existing.item = newName }
                return existing
            }
            await repo.renameShoppingItem(id: item.id, item: newName)
            await reloadItems(listId: item.listId)
        }
    }

    func deleteItem(_ item: ShoppingItemModel) {
        Task {
            items.removeAll { $0.id == item.id }
            await repo.deleteShoppingItem(id: item.id)
            await reloadItems(listId: item.listId)
        }
    }

    /// Deletes all checked items from the given list.
    func clearCompleted(listId: String) {
        Task {
            guard items.contains(where: \.checked) else { return }
            items.removeAll(where: \.checked)
            await repo.clearCompletedShoppingItems(listId: listId)
            await reloadItems(listId: listId)
        }
    }
}

/// Progress label shown on list cards — mirrors shoppingProgressLabel in ShoppingScreens.kt.
func shoppingProgressLabel(
    _ progress: ListProgress?,
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    guard let progress, progress.total > 0 else { return L("No items yet", locale: locale) }
    if progress.bought == progress.total { return L("All bought", locale: locale) }
    return L("\(progress.bought) of \(progress.total) bought", locale: locale)
}
