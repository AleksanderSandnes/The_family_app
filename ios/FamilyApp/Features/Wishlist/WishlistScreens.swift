// Wishlist screens — the iOS twin of WishlistScreens.kt: wishlist cards with owner
// names, owner view (claim toggle, add-a-wish with link/price/photo) vs member view
// (secret Reserve / Reserved-by-you / Reserved states).
import NukeUI
import PhotosUI
import SwiftUI

struct WishlistScreen: View {
    let viewModel: WishlistViewModel
    let onOpen: (String) -> Void

    @State private var showAdd = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if viewModel.isLoading {
                    LoadingState().frame(maxHeight: .infinity, alignment: .top)
                } else if viewModel.wishlists.isEmpty {
                    EmptyState(
                        systemImage: "gift.fill",
                        title: L("No wishlists yet"),
                        subtitle: L("Create a wishlist to share with your family")
                    )
                } else {
                    List {
                        ForEach(viewModel.wishlists) { wishlist in
                            WishlistRow(wishlist: wishlist) { onOpen(wishlist.id) }
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(
                                    top: 6, leading: Spacing.screenEdge,
                                    bottom: 6, trailing: Spacing.screenEdge
                                ))
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.deleteWishlist(wishlist)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable { viewModel.refresh() }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            FloatingActionButton(text: L("New wishlist"), systemImage: "plus") { showAdd = true }
        }
        .ambientBackground()
        .featureTopBar(L("Wishlists"))
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            NewWishlistSheet { name, icon, color in
                viewModel.addWishlist(name: name, icon: icon, color: color)
                showAdd = false
            }
        }
    }
}

private struct WishlistRow: View {
    let wishlist: WishlistModel
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                FeatureBadge(
                    systemImage: IconKeyMap.wishlistSymbol(wishlist.icon),
                    feature: .wishlists,
                    size: 46,
                    cornerRadius: 23,
                    colorOverride: hexColor(wishlist.color)
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(wishlist.name)
                        .font(.system(size: 15.5, weight: .semibold))
                        .foregroundStyle(Color.appOnSurface)
                    if !wishlist.ownerName.isEmpty {
                        Text("By \(wishlist.ownerName)")
                            .font(.caption)
                            .foregroundStyle(Color.appCaption)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(Spacing.cardPadding)
            .glassCard(cornerRadius: Radius.overviewCard)
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

// MARK: - Detail

struct WishlistDetailScreen: View {
    let wishlistId: String
    let viewModel: WishlistViewModel

    @State private var showAddWish = false
    @State private var wishToEdit: WishModel?
    @State private var showRename = false
    @State private var showChangeIcon = false
    @State private var renameText = ""
    @State private var shareItem: ShareItem?

    private var isOwner: Bool {
        viewModel.currentUserId != nil
            && viewModel.selectedWishlist?.ownerUserId == viewModel.currentUserId
    }

    /// Builds a shareable PDF of the current wishlist and presents the system share sheet.
    private func exportPDF() {
        let name = viewModel.selectedWishlist?.name ?? L("Wishlist")
        let ownerName = viewModel.selectedWishlist?.ownerName ?? ""
        guard let url = WishlistPDF.make(name: name, ownerName: ownerName, wishes: sortedWishes) else { return }
        shareItem = ShareItem(items: [url])
    }

    private var sortedWishes: [WishModel] {
        viewModel.wishes.sorted { !$0.checked && $1.checked }
    }

    var body: some View {
        VStack(spacing: 0) {
            if !isOwner, let owner = viewModel.selectedWishlist?.ownerName, !owner.isEmpty {
                Text("\(L("Reservations are hidden from")) \(owner) 🤫")
                    .font(.caption)
                    .foregroundStyle(Color.appCaption)
                    .frame(maxWidth: .infinity)
                    .padding(.top, Spacing.sm)
                    .padding(.bottom, Spacing.xs)
            }
            if sortedWishes.isEmpty {
                EmptyState(
                    systemImage: "gift.fill",
                    title: L("No wishes yet"),
                    subtitle: L("Add wishes to this list")
                )
                .frame(maxHeight: .infinity)
            } else {
                List {
                    ForEach(sortedWishes) { wish in
                        Group {
                            if isOwner {
                                OwnerWishRow(
                                    wish: wish,
                                    onToggle: { viewModel.toggle(wish) },
                                    onEdit: { wishToEdit = wish }
                                )
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.deleteWish(wish)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            } else {
                                MemberWishRow(
                                    wish: wish,
                                    state: reservationState(
                                        reservation: viewModel.reservations[wish.id],
                                        currentUserId: viewModel.currentUserId
                                    ),
                                    onReserve: { viewModel.reserve(wish) },
                                    onUnreserve: { viewModel.unreserve(wish) }
                                )
                            }
                        }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(
                            top: 5, leading: Spacing.screenEdge, bottom: 5, trailing: Spacing.screenEdge
                        ))
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }

            // Only the wishlist owner adds wishes; family members view + reserve.
            if isOwner {
                PrimaryButton(text: L("Add a wish"), systemImage: "plus") { showAddWish = true }
                    .padding(Spacing.lg)
                    .background(.ultraThinMaterial)
            }
        }
        .ambientBackground()
        .featureTopBar(viewModel.selectedWishlist?.name ?? L("Wishlist"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    // Anyone viewing the list can export it to share with people off-app.
                    Button { exportPDF() } label: {
                        Label(L("Export PDF"), systemImage: "square.and.arrow.up")
                    }
                    // Only the wishlist's creator (owner) can rename it or change its icon.
                    if isOwner {
                        Button {
                            renameText = viewModel.selectedWishlist?.name ?? ""
                            showRename = true
                        } label: {
                            Label(L("Rename wishlist"), systemImage: "pencil")
                        }
                        Button { showChangeIcon = true } label: {
                            Label(L("Change icon"), systemImage: "star")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel("More options")
                }
            }
        }
        .sheet(item: $shareItem) { ShareSheet(items: $0.items) }
        .task(id: wishlistId) { viewModel.loadWishlistDetail(wishlistId) }
        .inputDialog(
            isPresented: $showRename,
            title: L("Rename wishlist"),
            label: L("Name"),
            text: $renameText
        ) {
            if !renameText.trimmingCharacters(in: .whitespaces).isEmpty {
                viewModel.renameWishlist(
                    wishlistId: wishlistId,
                    newName: renameText.trimmingCharacters(in: .whitespaces)
                )
            }
        }
        .sheet(isPresented: $showChangeIcon) {
            IconPickerSheet(
                title: L("Change icon"),
                options: IconOptions.wishlist,
                selected: viewModel.selectedWishlist?.icon ?? "card_giftcard",
                symbolFor: IconKeyMap.wishlistSymbol,
                onPick: { icon in
                    viewModel.changeWishlistIcon(wishlistId: wishlistId, newIcon: icon)
                    showChangeIcon = false
                },
                initialColor: viewModel.selectedWishlist?.color,
                onColorPick: { color in viewModel.changeWishlistColor(wishlistId: wishlistId, color: color) }
            )
        }
        .sheet(isPresented: $showAddWish) {
            AddWishSheet { draft in
                viewModel.addWish(wishlistId: wishlistId, draft: draft)
                showAddWish = false
            }
        }
        .sheet(item: $wishToEdit) { wish in
            AddWishSheet(initial: wish) { draft in
                viewModel.updateWish(wishId: wish.id, draft: draft)
                wishToEdit = nil
            }
        }
    }
}

// MARK: - Wish rows

private struct WishThumb: View {
    let url: String?

    var body: some View {
        if let url, !url.isEmpty, let imageURL = URL(string: url) {
            LazyImage(url: imageURL) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                }
            }
            .frame(width: 44, height: 44)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }
}

private struct WishLinkButton: View {
    let link: String?

    @Environment(\.openURL) private var openURL

    var body: some View {
        if let link, !link.isEmpty, let url = URL(string: link) {
            Button {
                openURL(url)
            } label: {
                Image(systemName: "arrow.up.right.square")
                    .foregroundStyle(Color.appPrimary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open link")
        }
    }
}

private struct OwnerWishRow: View {
    let wish: WishModel
    let onToggle: () -> Void
    let onEdit: () -> Void

    var body: some View {
        HStack(spacing: Spacing.md) {
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onToggle()
            } label: {
                if wish.checked {
                    Circle().fill(Color.appPrimary).frame(width: 24, height: 24)
                        .overlay(Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold)).foregroundStyle(.white))
                } else {
                    Circle().strokeBorder(Color(hex: 0x9CA2BC), lineWidth: 1.8)
                        .frame(width: 24, height: 24)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(wish.checked ? L("Unmark as claimed") : L("Mark as claimed"))
            // Tap the wish itself to edit it (owner only).
            Button(action: onEdit) {
                HStack(spacing: Spacing.md) {
                    WishThumb(url: wish.imageUrl)
                    Text(wishTitle(wish))
                        .font(.system(size: 15.5))
                        .foregroundStyle(wish.checked ? Color(hex: 0xA6ACC4) : Color.appOnSurface)
                        .strikethrough(wish.checked)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(L("Edit wish"))
            WishLinkButton(link: wish.link)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
        .rowSurface(ghost: wish.checked, cornerRadius: Radius.row)
        .accessibilityLabel("\(wish.text), \(wish.checked ? L("claimed") : L("unclaimed"))")
    }
}

private struct MemberWishRow: View {
    let wish: WishModel
    let state: WishReservationState
    let onReserve: () -> Void
    let onUnreserve: () -> Void

    var body: some View {
        HStack(spacing: Spacing.md) {
            WishThumb(url: wish.imageUrl)
            VStack(alignment: .leading, spacing: 2) {
                Text(wish.text)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(state == .reservedByOther ? Color(hex: 0x767E9C) : Color.appOnSurface)
                if let price = wish.price?.trimmingCharacters(in: .whitespaces), !price.isEmpty {
                    Text(price)
                        .font(.caption)
                        .foregroundStyle(Color.appCaption)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            WishLinkButton(link: wish.link)
            switch state {
            case .available:
                // Filled accent capsule (spec 2f).
                Button(action: onReserve) {
                    Text("Reserve")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 15)
                        .frame(height: 32)
                        .background(Color.appPrimary, in: Capsule())
                        .accentGlow(opacity: 0.3, radius: 8, y: 3)
                }
                .buttonStyle(.plain)
            case .reservedByMe:
                Button(action: onUnreserve) {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                        Text("Reserved by you")
                    }
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Color.appPrimary)
                }
                .buttonStyle(.plain)
            case .reservedByOther:
                HStack(spacing: 4) {
                    Image(systemName: "lock.fill").font(.system(size: 11))
                    Text("Reserved")
                }
                .font(.system(size: 12.5))
                .foregroundStyle(Color.appCaption)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
        .rowSurface(ghost: state == .reservedByOther, cornerRadius: Radius.row)
    }
}

// MARK: - Sheets

private struct NewWishlistSheet: View {
    let onCreate: (String, String, Int?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var selectedIcon = "card_giftcard"
    @State private var color: Int? = calendarEventColorPalette.first

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(title: L("New wishlist"), confirmTitle: L("Create"), confirmEnabled: canCreate) {
                dismiss()
            } onConfirm: {
                onCreate(name.trimmingCharacters(in: .whitespaces), selectedIcon, color)
                dismiss()
            }
            GlassField(systemImage: "gift", placeholder: L("Wishlist name"), text: $name)
            SectionHeader(text: L("Icon"))
            IconGrid(
                options: IconOptions.wishlist,
                selected: selectedIcon,
                symbolFor: IconKeyMap.wishlistSymbol
            ) { selectedIcon = $0 }
            SectionHeader(text: L("Color"))
            EventColorPicker(selection: $color)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
    }
}

/// Rich add-wish flow: title (required) + optional link, price and photo.
private struct AddWishSheet: View {
    /// When set, the sheet edits this wish instead of adding a new one.
    var initial: WishModel?
    let onConfirm: (WishDraft) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var link = ""
    @State private var price = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?

    private var canAdd: Bool {
        !text.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            SheetHeader(
                title: initial != nil ? L("Edit wish") : L("Add a wish"),
                confirmTitle: initial != nil ? L("Save") : L("Add"),
                confirmEnabled: canAdd
            ) {
                dismiss()
            } onConfirm: {
                onConfirm(WishDraft(
                    text: text.trimmingCharacters(in: .whitespaces),
                    link: link.isEmpty ? nil : link,
                    price: price.isEmpty ? nil : price,
                    imageData: imageData
                ))
                dismiss()
            }
            .padding(.bottom, Spacing.xs)
            GlassField(systemImage: "gift", placeholder: L("What do you wish for?"), text: $text)
            GlassField(systemImage: "link", placeholder: L("Link (optional)"), text: $link)
            GlassField(systemImage: "tag", placeholder: L("Price (optional)"), text: $price)

            PhotosPicker(selection: $photoItem, matching: .images) {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "photo")
                    Text(LocalizedStringKey(
                        imageData != nil ? "Photo selected"
                            : (initial?.imageUrl?.isEmpty == false ? "Change photo" : "Add photo (optional)")
                    ))
                }
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color.appPrimary)
                .frame(maxWidth: .infinity, minHeight: 52)
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                        .strokeBorder(
                            Color.appPrimary.opacity(0.4),
                            style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])
                        )
                )
            }
            if let imageData, let image = UIImage(data: imageData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(height: 140)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
        .onChange(of: photoItem) { _, item in
            Task {
                imageData = try? await item?.loadTransferable(type: Data.self)
            }
        }
        .onAppear {
            if let initial {
                text = initial.text
                link = initial.link ?? ""
                price = initial.price ?? ""
            }
        }
    }
}
