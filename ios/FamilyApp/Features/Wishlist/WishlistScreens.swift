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
                        title: "No wishlists yet",
                        subtitle: "Create a wishlist to share with your family",
                        actionLabel: "New wishlist"
                    ) { showAdd = true }
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

            FloatingActionButton(text: "New wishlist", systemImage: "plus") { showAdd = true }
        }
        .background(Color.appBackground)
        .featureTopBar("Wishlists")
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            NewWishlistSheet { name, icon in
                viewModel.addWishlist(name: name, icon: icon)
                showAdd = false
            }
        }
    }
}

private struct WishlistRow: View {
    let wishlist: WishlistModel
    let onTap: () -> Void

    var body: some View {
        ListCard(onTap: onTap) {
            Image(systemName: IconKeyMap.wishlistSymbol(wishlist.icon))
                .font(.system(size: 24))
                .foregroundStyle(Color.appOnPrimaryContainer)
                .frame(width: 52, height: 52)
                .background(Color.appPrimaryContainer)
                .clipShape(Circle())
            Spacer().frame(width: 14)
            VStack(alignment: .leading, spacing: 1) {
                Text(wishlist.name)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                if !wishlist.ownerName.isEmpty {
                    Text("By \(wishlist.ownerName)")
                        .font(.labelMedium)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
    }
}

// MARK: - Detail

struct WishlistDetailScreen: View {
    let wishlistId: String
    let viewModel: WishlistViewModel

    @State private var showAddWish = false
    @State private var showRename = false
    @State private var showChangeIcon = false
    @State private var renameText = ""

    private var isOwner: Bool {
        viewModel.currentUserId != nil
            && viewModel.selectedWishlist?.ownerUserId == viewModel.currentUserId
    }

    private var sortedWishes: [WishModel] {
        viewModel.wishes.sorted { !$0.checked && $1.checked }
    }

    var body: some View {
        VStack(spacing: 0) {
            if sortedWishes.isEmpty {
                EmptyState(
                    systemImage: "gift.fill",
                    title: "No wishes yet",
                    subtitle: "Add wishes to this list"
                )
                .frame(maxHeight: .infinity)
            } else {
                List {
                    ForEach(sortedWishes) { wish in
                        Group {
                            if isOwner {
                                OwnerWishRow(wish: wish) { viewModel.toggle(wish) }
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
                PrimaryButton(text: "Add a wish", systemImage: "plus") { showAddWish = true }
                    .padding(Spacing.lg)
                    .background(Color.appSurface)
            }
        }
        .background(Color.appBackground)
        .featureTopBar(viewModel.selectedWishlist?.name ?? "Wishlist")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Rename wishlist") {
                        renameText = viewModel.selectedWishlist?.name ?? ""
                        showRename = true
                    }
                    Button("Change icon") { showChangeIcon = true }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel("More options")
                }
            }
        }
        .task(id: wishlistId) { viewModel.loadWishlistDetail(wishlistId) }
        .inputDialog(
            isPresented: $showRename,
            title: "Rename wishlist",
            label: "Name",
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
                title: "Change icon",
                options: IconOptions.wishlist,
                selected: viewModel.selectedWishlist?.icon ?? "card_giftcard",
                symbolFor: IconKeyMap.wishlistSymbol
            ) { icon in
                viewModel.changeWishlistIcon(wishlistId: wishlistId, newIcon: icon)
                showChangeIcon = false
            }
        }
        .sheet(isPresented: $showAddWish) {
            AddWishSheet { draft in
                viewModel.addWish(wishlistId: wishlistId, draft: draft)
                showAddWish = false
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

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onToggle()
            } label: {
                Image(systemName: wish.checked ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(wish.checked ? Color.appPrimary : Color.appOnSurfaceVariant)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(wish.checked ? "Unmark as claimed" : "Mark as claimed")
            WishThumb(url: wish.imageUrl)
            Text(wishTitle(wish))
                .font(.bodyLarge)
                .foregroundStyle(wish.checked ? Color.appOnSurfaceVariant : Color.appOnSurface)
                .strikethrough(wish.checked)
                .frame(maxWidth: .infinity, alignment: .leading)
            WishLinkButton(link: wish.link)
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, 10)
        .background(Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        .accessibilityLabel("\(wish.text), \(wish.checked ? "claimed" : "unclaimed")")
    }
}

private struct MemberWishRow: View {
    let wish: WishModel
    let state: WishReservationState
    let onReserve: () -> Void
    let onUnreserve: () -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            WishThumb(url: wish.imageUrl)
            Text(wishTitle(wish))
                .font(.bodyLarge)
                .foregroundStyle(Color.appOnSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
            WishLinkButton(link: wish.link)
            switch state {
            case .available:
                Button("Reserve", action: onReserve)
                    .font(.labelLarge)
                    .foregroundStyle(Color.appPrimary)
            case .reservedByMe:
                Button(action: onUnreserve) {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                        Text("Reserved by you")
                    }
                    .font(.labelLarge)
                    .foregroundStyle(Color.appPrimary)
                }
                .buttonStyle(.plain)
            case .reservedByOther:
                Text("Reserved")
                    .font(.labelLarge)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 10)
        .background(Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
    }
}

// MARK: - Sheets

private struct NewWishlistSheet: View {
    let onCreate: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var selectedIcon = "card_giftcard"

    private let columns = Array(repeating: GridItem(.flexible()), count: 4)

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("New wishlist")
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            FamilyTextField(label: "Wishlist name", text: $name, systemImage: "gift")
            SectionHeader(text: "Icon")
            LazyVGrid(columns: columns, spacing: Spacing.md) {
                ForEach(IconOptions.wishlist, id: \.self) { key in
                    Button {
                        selectedIcon = key
                    } label: {
                        Image(systemName: IconKeyMap.wishlistSymbol(key))
                            .font(.system(size: 22))
                            .foregroundStyle(
                                key == selectedIcon ? Color.appOnPrimary : Color.appPrimary
                            )
                            .frame(width: 56, height: 56)
                            .background(
                                key == selectedIcon ? Color.appPrimary : Color.appPrimaryContainer
                            )
                            .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
                    }
                }
            }
            Spacer()
            PrimaryButton(text: "Create", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                onCreate(name.trimmingCharacters(in: .whitespaces), selectedIcon)
                dismiss()
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.bottom, Spacing.lg)
        .presentationDetents([.large])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
    }
}

/// Rich add-wish flow: title (required) + optional link, price and photo.
private struct AddWishSheet: View {
    let onConfirm: (WishDraft) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var link = ""
    @State private var price = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text("Add a wish")
                    .font(.titleLarge)
                    .foregroundStyle(Color.appOnSurface)
                    .padding(.top, Spacing.xxl)
                FamilyTextField(label: "What do you wish for?", text: $text, systemImage: "gift")
                FamilyTextField(
                    label: "Link (optional)",
                    text: $link,
                    systemImage: "link",
                    keyboardType: .URL,
                    autocapitalization: .never
                )
                FamilyTextField(label: "Price (optional)", text: $price, systemImage: "tag")

                PhotosPicker(selection: $photoItem, matching: .images) {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "photo")
                        Text(imageData == nil ? "Add photo (optional)" : "Photo selected")
                    }
                    .font(.bodyMedium)
                    .foregroundStyle(Color.appPrimary)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                            .strokeBorder(Color.appPrimary.opacity(0.4), lineWidth: 1)
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

                PrimaryButton(text: "Add", enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty) {
                    onConfirm(WishDraft(
                        text: text.trimmingCharacters(in: .whitespaces),
                        link: link.isEmpty ? nil : link,
                        price: price.isEmpty ? nil : price,
                        imageData: imageData
                    ))
                    dismiss()
                }
                .padding(.top, Spacing.sm)
            }
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.bottom, Spacing.lg)
        }
        .presentationDetents([.large])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
        .onChange(of: photoItem) { _, item in
            Task {
                imageData = try? await item?.loadTransferable(type: Data.self)
            }
        }
    }
}
