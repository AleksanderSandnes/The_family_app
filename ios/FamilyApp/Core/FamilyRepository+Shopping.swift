// Shopping data access behind the FamilyRepositoryProtocol seam, so the view model is
// unit-testable with a mock. Fetches throw so the caller can fall back to its current value
// (`(try? …) ?? …`).
//
// Note: `fetchShoppingLists(familyId:)` in FamilyRepository+Home.swift is a family-only
// query; the shopping list screen needs the owner-OR-family variant below, so it does not
// reuse it. `fetchUncheckedShoppingItems(listIds:)` there filters checked=false, whereas
// progress needs all items — hence the distinct `fetchShoppingItems(listIds:)`.
import Foundation
import Supabase

extension FamilyRepository {
    /// Lists visible to the user: own OR in my family. Throws so the caller keeps the current
    /// list via `(try? …) ?? lists` (no rollback). Filters out foreign-family rows.
    func fetchShoppingLists(userId: String, familyId: String?) async throws -> [ShoppingListModel] {
        if let familyId {
            let fetched: [ShoppingListModel] = try await client.from("shopping_lists")
                .select()
                .or("owner_user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value
            return fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        }
        let fetched: [ShoppingListModel] = try await client.from("shopping_lists")
            .select()
            .eq("owner_user_id", value: userId)
            .execute()
            .value
        return fetched.filter { $0.familyId == nil }
    }

    /// All items across the given lists (both checked and unchecked) — feeds progress counts.
    func fetchShoppingItems(listIds: [String]) async throws -> [ShoppingItemModel] {
        guard !listIds.isEmpty else { return [] }
        return try await client.from("shopping_items")
            .select()
            .in("list_id", values: listIds)
            .execute()
            .value
    }

    /// A single list by id (detail screen picks `.first`).
    func fetchShoppingList(id: String) async throws -> [ShoppingListModel] {
        try await client.from("shopping_lists")
            .select()
            .eq("id", value: id)
            .execute()
            .value
    }

    /// All items for one list (detail screen; no checked filter).
    func fetchShoppingItems(listId: String) async throws -> [ShoppingItemModel] {
        try await client.from("shopping_items")
            .select()
            .eq("list_id", value: listId)
            .execute()
            .value
    }

    /// Inserts a list (payload: title/icon/owner/color + optional family).
    func insertShoppingList(_ list: ShoppingListModel) async {
        var payload: [String: AnyJSON] = [
            "title": .string(list.title),
            "icon": .string(list.icon),
            "owner_user_id": .string(list.ownerUserId),
            "color": list.color.map { AnyJSON.integer($0) } ?? .null,
        ]
        if let familyId = list.familyId { payload["family_id"] = .string(familyId) }
        _ = try? await client.from("shopping_lists").insert(payload).execute()
    }

    func setShoppingListColor(id: String, color: Int?) async {
        _ = try? await client.from("shopping_lists")
            .update(["color": color.map { AnyJSON.integer($0) } ?? .null])
            .eq("id", value: id)
            .execute()
    }

    func setShoppingListIcon(id: String, icon: String) async {
        _ = try? await client.from("shopping_lists")
            .update(["icon": AnyJSON.string(icon)])
            .eq("id", value: id)
            .execute()
    }

    func renameShoppingList(id: String, title: String) async {
        _ = try? await client.from("shopping_lists")
            .update(["title": AnyJSON.string(title)])
            .eq("id", value: id)
            .execute()
    }

    func deleteShoppingList(id: String) async {
        _ = try? await client.from("shopping_lists").delete().eq("id", value: id).execute()
    }

    /// Inserts one item (payload: list_id + item).
    func insertShoppingItem(_ item: ShoppingItemModel) async {
        _ = try? await client.from("shopping_items")
            .insert(["list_id": AnyJSON.string(item.listId), "item": .string(item.item)])
            .execute()
    }

    func setShoppingItemChecked(id: String, checked: Bool) async {
        _ = try? await client.from("shopping_items")
            .update(["checked": AnyJSON.bool(checked)])
            .eq("id", value: id)
            .execute()
    }

    func renameShoppingItem(id: String, item: String) async {
        _ = try? await client.from("shopping_items")
            .update(["item": AnyJSON.string(item)])
            .eq("id", value: id)
            .execute()
    }

    func deleteShoppingItem(id: String) async {
        _ = try? await client.from("shopping_items").delete().eq("id", value: id).execute()
    }

    /// Deletes all checked items from the given list.
    func clearCompletedShoppingItems(listId: String) async {
        _ = try? await client.from("shopping_items")
            .delete()
            .eq("list_id", value: listId)
            .eq("checked", value: true)
            .execute()
    }
}
