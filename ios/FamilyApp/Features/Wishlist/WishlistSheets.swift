// Add/edit sheets for the wishlist feature. NewWishlistSheet creates a list; AddWishSheet
// is the shared add/edit-a-wish flow (title required, optional link/price/photo).
import PhotosUI
import SwiftUI

struct NewWishlistSheet: View {
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
struct AddWishSheet: View {
    /// When set, the sheet edits this wish instead of adding a new one.
    var initial: WishModel?
    let onConfirm: (WishDraft) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var link = ""
    @State private var price = ""
    @State private var descriptionText = ""
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
                    imageData: imageData,
                    description: descriptionText.isEmpty ? nil : descriptionText
                ))
                dismiss()
            }
            .padding(.bottom, Spacing.xs)
            GlassField(systemImage: "gift", placeholder: L("What do you wish for?"), text: $text)
            GlassField(systemImage: "text.alignleft", placeholder: L("Description (optional)"), text: $descriptionText)
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
                descriptionText = initial.description ?? ""
            }
        }
    }
}
