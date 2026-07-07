// Wishlist view model — the iOS twin of WishlistViewModel.kt, incl. secret gift
// reservations: RLS hides wish_reservations rows from the wishlist owner, so the
// owner's reservations map stays empty and the surprise is preserved.
import Foundation
import Observation
import Supabase

/// The user-entered details for a new wish (groups the optional rich fields).
struct WishDraft {
    var text: String
    var link: String?
    var price: String?
    var imageData: Data?
}

@Observable
@MainActor
final class WishlistViewModel {
    private static var cache: [WishlistModel] = []

    private(set) var wishlists: [WishlistModel] = WishlistViewModel.cache
    private(set) var isLoading = false
    private(set) var selectedWishlist: WishlistModel?
    private(set) var wishes: [WishModel] = []
    /// Reservations visible to the current user, keyed by wish id. Empty for the owner.
    private(set) var reservations: [String: WishReservationModel] = [:]

    var currentUserId: String? {
        repo.session.currentUserId
    }

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    private let wishlistsObserver = RealtimeObserver()
    private let wishesObserver = RealtimeObserver()
    private var subscribedFamilyId: String?
    private var subscribedWishesListId: String?
    private var ownerNameCache: [String: String] = [:]

    init() {
        Task { await loadWishlists() }
    }

    func refresh() {
        Task { await loadWishlists() }
    }

    private func resolveOwnerNames(_ lists: [WishlistModel]) async -> [WishlistModel] {
        for ownerId in Set(lists.map(\.ownerUserId)) where ownerNameCache[ownerId] == nil {
            ownerNameCache[ownerId] = await fetchUserName(ownerId) ?? ""
        }
        return lists.map { list in
            var list = list
            list.ownerName = ownerNameCache[list.ownerUserId] ?? ""
            return list
        }
    }

    /// Fetches any family member's name (repo.getUser only caches the current user).
    private func fetchUserName(_ userId: String) async -> String? {
        if userId == repo.session.currentUserId {
            return await repo.getUser(userId)?.name
        }
        let users: [UserModel] = await (try? client.from("users")
            .select()
            .eq("id", value: userId)
            .execute()
            .value) ?? []
        return users.first?.name
    }

    private func loadWishlists() async {
        guard let userId = repo.session.currentUserId else {
            wishlists = []
            return
        }
        if wishlists.isEmpty { isLoading = true }
        defer { isLoading = false }
        let user = await repo.getUser(userId)

        let result: [WishlistModel]
        if let familyId = user?.familyId {
            let fetched: [WishlistModel] = await (try? client.from("wishlists")
                .select()
                .or("owner_user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value) ?? wishlists
            result = fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        } else {
            let fetched: [WishlistModel] = await (try? client.from("wishlists")
                .select()
                .eq("owner_user_id", value: userId)
                .execute()
                .value) ?? wishlists
            result = fetched.filter { $0.familyId == nil }
        }
        let resolved = await resolveOwnerNames(result)
        Self.cache = resolved
        wishlists = resolved

        if let familyId = user?.familyId, subscribedFamilyId != familyId {
            subscribedFamilyId = familyId
            wishlistsObserver.start(
                table: "wishlists",
                scope: familyId,
                filter: .eq("family_id", value: familyId)
            ) { [weak self] in
                await self?.loadWishlists()
            }
        }
    }

    // MARK: - Detail

    func loadWishlistDetail(_ wishlistId: String) {
        Task {
            await reloadDetail(wishlistId)
            subscribeToWishesOnce(wishlistId: wishlistId)
        }
    }

    private func reloadDetail(_ wishlistId: String) async {
        async let listFetch: [WishlistModel] = (try? client.from("wishlists")
            .select()
            .eq("id", value: wishlistId)
            .execute()
            .value) ?? []
        async let wishesFetch: [WishModel] = (try? client.from("wishes")
            .select()
            .eq("wishlist_id", value: wishlistId)
            .execute()
            .value) ?? []
        if let list = await listFetch.first { selectedWishlist = list }
        // Enrich owner name (for the "reservations hidden from …" member-view subtitle).
        if let ownerId = selectedWishlist?.ownerUserId {
            if ownerNameCache[ownerId] == nil {
                ownerNameCache[ownerId] = await fetchUserName(ownerId) ?? ""
            }
            selectedWishlist?.ownerName = ownerNameCache[ownerId] ?? ""
        }
        wishes = await wishesFetch
        await loadReservations()
    }

    /// Loads reservations the current user is allowed to see (none on their own wishlists).
    private func loadReservations() async {
        let wishIds = wishes.map(\.id).filter { !$0.hasPrefix("temp-") }
        guard !wishIds.isEmpty else {
            reservations = [:]
            return
        }
        let rows: [WishReservationModel] = await (try? client.from("wish_reservations")
            .select()
            .in("wish_id", values: wishIds)
            .execute()
            .value) ?? []
        reservations = Dictionary(rows.map { ($0.wishId, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private func subscribeToWishesOnce(wishlistId: String) {
        guard subscribedWishesListId != wishlistId else { return }
        subscribedWishesListId = wishlistId
        wishesObserver.start(
            table: "wishes",
            scope: wishlistId,
            filter: .eq("wishlist_id", value: wishlistId)
        ) { [weak self] in
            await self?.reloadDetail(wishlistId)
        }
    }

    // MARK: - Reservations (secret gift claims)

    func reserve(_ wish: WishModel) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            var temp = WishReservationModel()
            temp.wishId = wish.id
            temp.reservedBy = userId
            reservations[wish.id] = temp
            _ = try? await client.from("wish_reservations")
                .insert(["wish_id": AnyJSON.string(wish.id), "reserved_by": .string(userId)])
                .execute()
            await loadReservations()
        }
    }

    func unreserve(_ wish: WishModel) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            reservations[wish.id] = nil
            _ = try? await client.from("wish_reservations")
                .delete()
                .eq("wish_id", value: wish.id)
                .eq("reserved_by", value: userId)
                .execute()
            await loadReservations()
        }
    }

    // MARK: - Wishlist mutations

    func addWishlist(name: String, icon: String = "card_giftcard", color: Int? = nil) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            let user = await repo.getUser(userId)
            var temp = WishlistModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.ownerUserId = userId
            temp.familyId = user?.familyId
            temp.name = name
            temp.icon = icon
            temp.color = color
            wishlists.append(temp)

            var payload: [String: AnyJSON] = [
                "owner_user_id": .string(userId),
                "name": .string(name),
                "icon": .string(icon),
                "color": color.map { AnyJSON.integer($0) } ?? .null,
            ]
            if let familyId = user?.familyId { payload["family_id"] = .string(familyId) }
            _ = try? await client.from("wishlists").insert(payload).execute()
            await loadWishlists()
        }
    }

    func changeWishlistColor(wishlistId: String, color: Int?) {
        Task {
            wishlists = wishlists.map { list in
                var list = list
                if list.id == wishlistId { list.color = color }
                return list
            }
            selectedWishlist?.color = color
            _ = try? await client.from("wishlists")
                .update(["color": color.map { AnyJSON.integer($0) } ?? .null])
                .eq("id", value: wishlistId)
                .execute()
            await reloadDetail(wishlistId)
        }
    }

    func deleteWishlist(_ wishlist: WishlistModel) {
        Task {
            wishlists.removeAll { $0.id == wishlist.id }
            _ = try? await client.from("wishlists").delete().eq("id", value: wishlist.id).execute()
            await loadWishlists()
        }
    }

    func renameWishlist(wishlistId: String, newName: String) {
        Task {
            wishlists = wishlists.map { list in
                var list = list
                if list.id == wishlistId { list.name = newName }
                return list
            }
            selectedWishlist?.name = newName
            _ = try? await client.from("wishlists")
                .update(["name": AnyJSON.string(newName)])
                .eq("id", value: wishlistId)
                .execute()
            await reloadDetail(wishlistId)
        }
    }

    func changeWishlistIcon(wishlistId: String, newIcon: String) {
        Task {
            wishlists = wishlists.map { list in
                var list = list
                if list.id == wishlistId { list.icon = newIcon }
                return list
            }
            selectedWishlist?.icon = newIcon
            _ = try? await client.from("wishlists")
                .update(["icon": AnyJSON.string(newIcon)])
                .eq("id", value: wishlistId)
                .execute()
            await reloadDetail(wishlistId)
        }
    }

    // MARK: - Wish mutations

    func addWish(wishlistId: String, draft: WishDraft) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            let cleanLink = draft.link?.trimmingCharacters(in: .whitespaces)
            let cleanPrice = draft.price?.trimmingCharacters(in: .whitespaces)

            var temp = WishModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.wishlistId = wishlistId
            temp.userId = userId
            temp.text = draft.text
            temp.link = cleanLink?.isEmpty == false ? cleanLink : nil
            temp.price = cleanPrice?.isEmpty == false ? cleanPrice : nil
            wishes.append(temp)

            var imageUrl: String?
            if let data = draft.imageData {
                let filename = "\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
                imageUrl = try? await StorageService.uploadWishImage(
                    data: data, appUserId: userId, filename: filename
                )
            }

            var payload: [String: AnyJSON] = [
                "wishlist_id": .string(wishlistId),
                "user_id": .string(userId),
                "text": .string(draft.text),
            ]
            if let cleanLink, !cleanLink.isEmpty { payload["link"] = .string(cleanLink) }
            if let cleanPrice, !cleanPrice.isEmpty { payload["price"] = .string(cleanPrice) }
            if let imageUrl { payload["image_url"] = .string(imageUrl) }
            _ = try? await client.from("wishes").insert(payload).execute()
            await reloadDetail(wishlistId)
        }
    }

    func toggle(_ wish: WishModel) {
        Task {
            wishes = wishes.map { existing in
                var existing = existing
                if existing.id == wish.id { existing.checked = !wish.checked }
                return existing
            }
            _ = try? await client.from("wishes")
                .update(["checked": AnyJSON.bool(!wish.checked)])
                .eq("id", value: wish.id)
                .execute()
            await reloadDetail(wish.wishlistId)
        }
    }

    func deleteWish(_ wish: WishModel) {
        Task {
            wishes.removeAll { $0.id == wish.id }
            _ = try? await client.from("wishes").delete().eq("id", value: wish.id).execute()
            await reloadDetail(wish.wishlistId)
        }
    }
}

// MARK: - Pure helpers (unit-tested)

/// Title with the price appended inline (e.g. "Lego set  ·  $50") — mirrors wishTitle.
func wishTitle(_ wish: WishModel) -> String {
    guard let price = wish.price?.trimmingCharacters(in: .whitespaces), !price.isEmpty else {
        return wish.text
    }
    return "\(wish.text)  ·  \(price)"
}

/// Member-view reservation state for a wish — mirrors the MemberWishCard branches.
enum WishReservationState: Equatable {
    case available
    case reservedByMe
    case reservedByOther
}

func reservationState(
    reservation: WishReservationModel?,
    currentUserId: String?
) -> WishReservationState {
    guard let reservation else { return .available }
    return reservation.reservedBy == currentUserId ? .reservedByMe : .reservedByOther
}
