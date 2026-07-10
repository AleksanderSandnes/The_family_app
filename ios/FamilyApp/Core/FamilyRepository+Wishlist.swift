// Wishlist data access — moved out of WishlistViewModel so the VM depends only on the
// FamilyRepositoryProtocol seam and can be unit-tested with a mock. Each method preserves
// the exact query semantics and payload fields of the original inline `client.from(...)`
// calls. Fetches throw so the caller can fall back to its current value (`(try? …) ?? …`).
//
// Note the secret-gift reservation flow: `wish_reservations` rows are RLS-hidden from the
// wishlist owner, so `fetchWishReservations` returns an empty set for them. Reservations are
// insert/delete only (there is no update) — mirrored here.
import Foundation
import Supabase

extension FamilyRepository {
    /// Wishlists visible to the user: own OR in my family. Throws so the caller keeps the
    /// current list (no-rollback parity via `(try? …) ?? wishlists`). Filters out foreign-family
    /// rows.
    func fetchWishlists(userId: String, familyId: String?) async throws -> [WishlistModel] {
        if let familyId {
            let fetched: [WishlistModel] = try await client.from("wishlists")
                .select()
                .or("owner_user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value
            return fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        }
        let fetched: [WishlistModel] = try await client.from("wishlists")
            .select()
            .eq("owner_user_id", value: userId)
            .execute()
            .value
        return fetched.filter { $0.familyId == nil }
    }

    /// A single wishlist by id (detail screen picks `.first`).
    func fetchWishlist(id: String) async throws -> [WishlistModel] {
        try await client.from("wishlists")
            .select()
            .eq("id", value: id)
            .execute()
            .value
    }

    /// All wishes for one wishlist (detail screen).
    func fetchWishes(wishlistId: String) async throws -> [WishModel] {
        try await client.from("wishes")
            .select()
            .eq("wishlist_id", value: wishlistId)
            .execute()
            .value
    }

    /// Reservations the current user is allowed to see (RLS returns none on their own lists).
    func fetchWishReservations(wishIds: [String]) async throws -> [WishReservationModel] {
        guard !wishIds.isEmpty else { return [] }
        return try await client.from("wish_reservations")
            .select()
            .in("wish_id", values: wishIds)
            .execute()
            .value
    }

    /// Any user's row by id (used to resolve a wishlist owner's display name).
    func fetchUser(id: String) async throws -> [UserModel] {
        try await client.from("users")
            .select()
            .eq("id", value: id)
            .execute()
            .value
    }

    /// Inserts a wishlist. Preserves the exact payload (owner/name/icon/color + optional family).
    func insertWishlist(_ list: WishlistModel) async {
        var payload: [String: AnyJSON] = [
            "owner_user_id": .string(list.ownerUserId),
            "name": .string(list.name),
            "icon": .string(list.icon),
            "color": list.color.map { AnyJSON.integer($0) } ?? .null,
        ]
        if let familyId = list.familyId { payload["family_id"] = .string(familyId) }
        _ = try? await client.from("wishlists").insert(payload).execute()
    }

    func setWishlistColor(id: String, color: Int?) async {
        _ = try? await client.from("wishlists")
            .update(["color": color.map { AnyJSON.integer($0) } ?? .null])
            .eq("id", value: id)
            .execute()
    }

    func setWishlistIcon(id: String, icon: String) async {
        _ = try? await client.from("wishlists")
            .update(["icon": AnyJSON.string(icon)])
            .eq("id", value: id)
            .execute()
    }

    func renameWishlist(id: String, name: String) async {
        _ = try? await client.from("wishlists")
            .update(["name": AnyJSON.string(name)])
            .eq("id", value: id)
            .execute()
    }

    func deleteWishlist(id: String) async {
        _ = try? await client.from("wishlists").delete().eq("id", value: id).execute()
    }

    /// Inserts one wish. Preserves the exact payload: wishlist_id/user_id/text always, plus
    /// link/price/image_url only when present and non-empty.
    func insertWish(_ wish: WishModel) async {
        var payload: [String: AnyJSON] = [
            "wishlist_id": .string(wish.wishlistId),
            "user_id": .string(wish.userId),
            "text": .string(wish.text),
        ]
        if let link = wish.link, !link.isEmpty { payload["link"] = .string(link) }
        if let price = wish.price, !price.isEmpty { payload["price"] = .string(price) }
        if let imageUrl = wish.imageUrl { payload["image_url"] = .string(imageUrl) }
        _ = try? await client.from("wishes").insert(payload).execute()
    }

    func setWishChecked(id: String, checked: Bool) async {
        _ = try? await client.from("wishes")
            .update(["checked": AnyJSON.bool(checked)])
            .eq("id", value: id)
            .execute()
    }

    func updateWish(id: String, text: String, link: String?, price: String?, imageUrl: String?) async {
        _ = try? await client.from("wishes")
            .update([
                "text": AnyJSON.string(text),
                "link": link.map { AnyJSON.string($0) } ?? .null,
                "price": price.map { AnyJSON.string($0) } ?? .null,
                "image_url": imageUrl.map { AnyJSON.string($0) } ?? .null,
            ])
            .eq("id", value: id)
            .execute()
    }

    func deleteWish(id: String) async {
        _ = try? await client.from("wishes").delete().eq("id", value: id).execute()
    }

    /// Reserves a wish (secret gift claim). Insert only — there is no update path.
    func insertWishReservation(wishId: String, reservedBy: String) async {
        _ = try? await client.from("wish_reservations")
            .insert(["wish_id": AnyJSON.string(wishId), "reserved_by": .string(reservedBy)])
            .execute()
    }

    /// Removes the current user's reservation on a wish. Delete only — there is no update path.
    func deleteWishReservation(wishId: String, reservedBy: String) async {
        _ = try? await client.from("wish_reservations")
            .delete()
            .eq("wish_id", value: wishId)
            .eq("reserved_by", value: reservedBy)
            .execute()
    }

    // MARK: - Share links (cross-family, single-user access)

    /// Wishlists shared TO the current user via a redeemed link (RLS returns them by grant).
    /// Two-step: read my grant rows, then load those wishlists by id.
    func fetchSharedWishlists(userId: String) async throws -> [WishlistModel] {
        let grants: [WishlistShareRow] = try await client.from("wishlist_shares")
            .select("wishlist_id")
            .eq("user_id", value: userId)
            .execute()
            .value
        let ids = grants.map(\.wishlistId)
        guard !ids.isEmpty else { return [] }
        return try await client.from("wishlists")
            .select()
            .in("id", values: ids)
            .execute()
            .value
    }

    /// Owner-only: mint (or return the existing) share token for a wishlist. Returns the
    /// token string, or nil if the caller isn't the owner / the wishlist is gone.
    func ensureWishlistShareToken(wishlistId: String) async throws -> String? {
        try await client
            .rpc("ensure_wishlist_share_token", params: ["p_wishlist_id": wishlistId])
            .execute()
            .value
    }

    /// Redeem a share token: grants the current user access and returns the wishlist id
    /// (nil for an invalid/revoked token).
    func acceptWishlistShare(token: String) async throws -> String? {
        try await client
            .rpc("accept_wishlist_share", params: ["p_token": token])
            .execute()
            .value
    }
}

/// Minimal decode shape for a `wishlist_shares` grant row (only the id is needed).
private struct WishlistShareRow: Decodable {
    let wishlistId: String
    enum CodingKeys: String, CodingKey { case wishlistId = "wishlist_id" }
}
