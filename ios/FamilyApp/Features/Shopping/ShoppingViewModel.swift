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

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient { SupabaseClientProvider.client }

    private let listsObserver = RealtimeObserver()
    private let itemsObserver = RealtimeObserver()
    private var subscribedListsFamilyId: String?
    private var subscribedItemsListId: String?
    private var familyChangedTask: Task<Void, Never>?

    init() {
        Task { await load() }
        familyChangedTask = Task { [weak self] in
            guard let stream = await self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.reloadLists()
            }
        }
    }

    deinit {
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

        let result: [ShoppingListModel]
        if let familyId {
            let fetched: [ShoppingListModel] = (try? await client.from("shopping_lists")
                .select()
                .or("owner_user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value) ?? lists
            result = fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        } else {
            let fetched: [ShoppingListModel] = (try? await client.from("shopping_lists")
                .select()
                .eq("owner_user_id", value: userId)
                .execute()
                .value) ?? lists
            result = fetched.filter { $0.familyId == nil }
        }
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
        guard let allItems: [ShoppingItemModel] = try? await client.from("shopping_items")
            .select()
            .in("list_id", values: listIds)
            .execute()
            .value
        else { return }
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
            filter: "family_id=eq.\(familyId)"
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
        async let listFetch: [ShoppingListModel]? = try? client.from("shopping_lists")
            .select()
            .eq("id", value: listId)
            .execute()
            .value
        async let itemsFetch: [ShoppingItemModel]? = try? client.from("shopping_items")
            .select()
            .eq("list_id", value: listId)
            .execute()
            .value
        if let list = await listFetch?.first { selectedList = list }
        if let fetched = await itemsFetch { items = fetched }
    }

    /// Subscribe to the items channel at most once per listId.
    private func subscribeToItemsOnce(listId: String) {
        guard subscribedItemsListId != listId else { return }
        subscribedItemsListId = listId
        itemsObserver.start(
            table: "shopping_items",
            scope: listId,
            filter: "list_id=eq.\(listId)"
        ) { [weak self] in
            await self?.reloadItems(listId: listId)
        }
    }

    // MARK: - List mutations (optimistic → call → reload)

    func addList(title: String, icon: String = "shopping_cart") {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            let user = await repo.getUser(userId)
            var temp = ShoppingListModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.title = title
            temp.ownerUserId = userId
            temp.familyId = user?.familyId
            temp.icon = icon
            lists.append(temp)

            var payload: [String: AnyJSON] = [
                "title": .string(title),
                "icon": .string(icon),
                "owner_user_id": .string(userId),
            ]
            if let familyId = user?.familyId { payload["family_id"] = .string(familyId) }
            try? await client.from("shopping_lists").insert(payload).execute()
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
            try? await client.from("shopping_lists")
                .update(["icon": AnyJSON.string(icon)])
                .eq("id", value: listId)
                .execute()
            await reloadLists()
        }
    }

    func renameList(listId: String, newTitle: String) {
        Task {
            selectedList?.title = newTitle
            try? await client.from("shopping_lists")
                .update(["title": AnyJSON.string(newTitle)])
                .eq("id", value: listId)
                .execute()
            await reloadItems(listId: listId)
        }
    }

    func deleteList(_ list: ShoppingListModel) {
        Task {
            lists.removeAll { $0.id == list.id }
            try? await client.from("shopping_lists").delete().eq("id", value: list.id).execute()
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
            try? await client.from("shopping_items")
                .insert(["list_id": AnyJSON.string(listId), "item": .string(item)])
                .execute()
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
            try? await client.from("shopping_items")
                .update(["checked": AnyJSON.bool(!item.checked)])
                .eq("id", value: item.id)
                .execute()
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
            try? await client.from("shopping_items")
                .update(["item": AnyJSON.string(newName)])
                .eq("id", value: item.id)
                .execute()
            await reloadItems(listId: item.listId)
        }
    }

    func deleteItem(_ item: ShoppingItemModel) {
        Task {
            items.removeAll { $0.id == item.id }
            try? await client.from("shopping_items").delete().eq("id", value: item.id).execute()
            await reloadItems(listId: item.listId)
        }
    }

    /// Deletes all checked items from the given list.
    func clearCompleted(listId: String) {
        Task {
            guard items.contains(where: \.checked) else { return }
            items.removeAll(where: \.checked)
            try? await client.from("shopping_items")
                .delete()
                .eq("list_id", value: listId)
                .eq("checked", value: true)
                .execute()
            await reloadItems(listId: listId)
        }
    }
}

/// Progress label shown on list cards — mirrors shoppingProgressLabel in ShoppingScreens.kt.
func shoppingProgressLabel(_ progress: ListProgress?) -> String {
    guard let progress, progress.total > 0 else { return "No items yet" }
    if progress.bought == progress.total { return "All bought" }
    return "\(progress.bought) of \(progress.total) bought"
}
