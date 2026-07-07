// Shopping screens — the iOS twin of ShoppingScreens.kt: list overview with progress
// labels + swipe-to-delete, detail with active/completed sections, inline rename,
// bottom add-item bar, rename/change-icon menu.
import SwiftUI

struct ShoppingScreen: View {
    let viewModel: ShoppingViewModel
    let onOpenList: (String) -> Void

    @State private var showAdd = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if viewModel.isLoading {
                    LoadingState()
                        .frame(maxHeight: .infinity, alignment: .top)
                } else if viewModel.lists.isEmpty {
                    EmptyState(
                        systemImage: "cart.fill",
                        title: L("No lists yet"),
                        subtitle: L("Create a shared shopping list for your family.")
                    )
                } else {
                    List {
                        ForEach(viewModel.lists) { list in
                            ShoppingListRow(
                                list: list,
                                progress: viewModel.listProgress[list.id]
                            ) { onOpenList(list.id) }
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(
                                    top: 6, leading: Spacing.screenEdge,
                                    bottom: 6, trailing: Spacing.screenEdge
                                ))
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.deleteList(list)
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

            FloatingActionButton(text: L("New list"), systemImage: "plus") { showAdd = true }
        }
        .ambientBackground()
        .featureTopBar(L("Shopping lists"))
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            NewListSheet { title, icon, color in
                viewModel.addList(title: title, icon: icon, color: color)
                showAdd = false
            }
        }
    }
}

private struct ShoppingListRow: View {
    let list: ShoppingListModel
    let progress: ListProgress?
    let onTap: () -> Void

    private var fraction: Double {
        guard let progress, progress.total > 0 else { return 0 }
        return Double(progress.bought) / Double(progress.total)
    }

    private var allDone: Bool {
        guard let progress else { return false }
        return progress.total > 0 && progress.bought == progress.total
    }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    if allDone {
                        RoundedRectangle(cornerRadius: Radius.badgeLarge, style: .continuous)
                            .fill(Color.appSuccess.opacity(0.13))
                            .frame(width: 44, height: 44)
                            .overlay(Image(systemName: "checkmark")
                                .font(.system(size: 19, weight: .bold))
                                .foregroundStyle(Color.appSuccess))
                    } else {
                        FeatureBadge(
                            systemImage: IconKeyMap.shoppingSymbol(list.icon),
                            feature: .shopping,
                            size: 44,
                            cornerRadius: Radius.badgeLarge,
                            colorOverride: hexColor(list.color)
                        )
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(list.title)
                            .font(.system(size: 15.5, weight: .semibold))
                            .foregroundStyle(allDone ? Color.appOnSurfaceVariant : Color.appOnSurface)
                        Text(shoppingProgressLabel(progress, locale: appLocale))
                            .font(.caption)
                            .foregroundStyle(allDone ? Color.appSuccess : Color.appCaption)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.appCaption)
                }
                if !allDone, progress != nil {
                    GlassProgressBar(value: fraction, tint: .appPrimary, height: 4)
                }
            }
            .padding(Spacing.cardPadding)
            .modifier(ListRowSurface(ghost: allDone))
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

/// Glass for active surfaces, dashed ghost for completed ones (spec 2d).
private struct ListRowSurface: ViewModifier {
    let ghost: Bool
    var cornerRadius: CGFloat = Radius.overviewCard
    func body(content: Content) -> some View {
        if ghost {
            content.ghostSurface(cornerRadius: cornerRadius)
        } else {
            content.glassCard(cornerRadius: cornerRadius)
        }
    }
}

// MARK: - Detail

struct ShoppingDetailScreen: View {
    let listId: String
    let viewModel: ShoppingViewModel

    @State private var newItemText = ""
    @State private var showRename = false
    @State private var showChangeIcon = false
    @State private var showCompleted = true
    @State private var renameText = ""

    private var active: [ShoppingItemModel] {
        viewModel.items.filter { !$0.checked }
    }

    private var completed: [ShoppingItemModel] {
        viewModel.items.filter(\.checked)
    }

    private var remaining: Int {
        active.count
    }

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.items.isEmpty {
                EmptyState(
                    systemImage: "cart.fill",
                    title: L("Empty list"),
                    subtitle: L("Tap the field below to add your first item.")
                )
                .frame(maxHeight: .infinity)
            } else {
                List {
                    ForEach(active) { item in
                        ShoppingItemRow(item: item, viewModel: viewModel)
                            .shoppingRowStyle()
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewModel.deleteItem(item)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                    }
                    if !completed.isEmpty {
                        CompletedHeader(count: completed.count, expanded: showCompleted) {
                            withAnimation { showCompleted.toggle() }
                        }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        if showCompleted {
                            ForEach(completed) { item in
                                ShoppingItemRow(item: item, viewModel: viewModel)
                                    .shoppingRowStyle()
                                    .swipeActions(edge: .trailing) {
                                        Button(role: .destructive) {
                                            viewModel.deleteItem(item)
                                        } label: {
                                            Label("Delete", systemImage: "trash")
                                        }
                                    }
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }

            addItemBar
        }
        .ambientBackground()
        .featureTopBar(viewModel.selectedList?.title ?? L("List"))
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !viewModel.items.isEmpty {
                    Text("\(remaining) left")
                        .font(.system(size: 12, weight: .semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.appPrimary.opacity(0.12), in: Capsule())
                        .foregroundStyle(Color.appPrimary)
                        .padding(.leading, 8)
                }
                Menu {
                    Button {
                        renameText = viewModel.selectedList?.title ?? ""
                        showRename = true
                    } label: {
                        Label(L("Rename list"), systemImage: "pencil")
                    }
                    Button { showChangeIcon = true } label: {
                        Label(L("Change icon"), systemImage: "star")
                    }
                    if !completed.isEmpty {
                        Button(role: .destructive) {
                            viewModel.clearCompleted(listId: listId)
                        } label: {
                            Label("\(L("Clear completed")) (\(completed.count))", systemImage: "trash")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel("More options")
                }
            }
        }
        .task(id: listId) { viewModel.loadListDetail(listId) }
        .inputDialog(
            isPresented: $showRename,
            title: L("Rename list"),
            label: L("List name"),
            text: $renameText
        ) {
            if !renameText.trimmingCharacters(in: .whitespaces).isEmpty {
                viewModel.renameList(listId: listId, newTitle: renameText.trimmingCharacters(in: .whitespaces))
            }
        }
        .sheet(isPresented: $showChangeIcon) {
            IconPickerSheet(
                title: L("Change icon"),
                options: IconOptions.shopping,
                selected: viewModel.selectedList?.icon ?? "shopping_cart",
                symbolFor: IconKeyMap.shoppingSymbol,
                onPick: { icon in
                    viewModel.changeListIcon(listId: listId, icon: icon)
                    showChangeIcon = false
                },
                initialColor: viewModel.selectedList?.color,
                onColorPick: { color in viewModel.changeListColor(listId: listId, color: color) }
            )
        }
    }

    private var addItemBar: some View {
        HStack(spacing: 10) {
            TextField("Add item…", text: $newItemText)
                .font(.system(size: 16))
                .tint(Color.appPrimary)
                .padding(.horizontal, Spacing.lg)
                .frame(height: 46)
                .glassCard(cornerRadius: 23)
                .onSubmit(addItem)
            Button(action: addItem) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 46, height: 46)
                    .background(Color.appPrimary.opacity(newItemText.isEmpty ? 0.4 : 1), in: Circle())
                    .accentGlow(active: !newItemText.isEmpty, opacity: 0.35, radius: 8, y: 3)
            }
            .disabled(newItemText.isEmpty)
            .accessibilityLabel("Add item")
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .background(.ultraThinMaterial)
    }

    private func addItem() {
        let text = newItemText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        viewModel.addItem(listId: listId, item: text)
        newItemText = ""
    }
}

extension View {
    fileprivate func shoppingRowStyle() -> some View {
        listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(
                top: 5, leading: Spacing.screenEdge, bottom: 5, trailing: Spacing.screenEdge
            ))
    }
}

private struct ShoppingItemRow: View {
    let item: ShoppingItemModel
    let viewModel: ShoppingViewModel

    @State private var isEditing = false
    @State private var editText = ""
    @FocusState private var editFocused: Bool

    var body: some View {
        HStack(spacing: Spacing.md) {
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                viewModel.toggle(item)
            } label: {
                if item.checked {
                    Circle().fill(Color.appPrimary)
                        .frame(width: 24, height: 24)
                        .overlay(Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold)).foregroundStyle(.white))
                } else {
                    Circle().strokeBorder(Color(hex: 0x9CA2BC), lineWidth: 1.8)
                        .frame(width: 24, height: 24)
                }
            }
            .buttonStyle(.plain)

            if isEditing {
                TextField("Item", text: $editText)
                    .font(.system(size: 15.5))
                    .tint(Color.appPrimary)
                    .focused($editFocused)
                    .onSubmit(commitEdit)
                    .onChange(of: editFocused) { _, focused in
                        if !focused { commitEdit() }
                    }
            } else {
                Text(item.item)
                    .font(.system(size: 15.5))
                    .foregroundStyle(item.checked ? Color(hex: 0xA6ACC4) : Color.appOnSurface)
                    .strikethrough(item.checked)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        editText = item.item
                        isEditing = true
                        editFocused = true
                    }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
        .modifier(ListRowSurface(ghost: item.checked, cornerRadius: Radius.row))
    }

    private func commitEdit() {
        guard isEditing else { return }
        isEditing = false
        let trimmed = editText.trimmingCharacters(in: .whitespaces)
        if !trimmed.isEmpty, trimmed != item.item {
            viewModel.renameItem(item, newName: trimmed)
        }
    }
}

private struct CompletedHeader: View {
    let count: Int
    let expanded: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack {
                Text(L("Completed (\(count))").uppercased())
                    .font(.sectionLabel)
                    .tracking(0.6)
                    .foregroundStyle(Color.appCaption)
                Spacer()
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(expanded ? L("Hide completed items") : L("Show completed items"))
    }
}

// MARK: - Shared pieces (also used by meals)

/// Extended floating action button — Liquid Glass FAB (solid accent capsule + glow),
/// positioned above the floating tab bar. Shared by shopping, meals, etc.
struct FloatingActionButton: View {
    let text: String
    var systemImage = "plus"
    let action: () -> Void

    var body: some View {
        GlassFAB(label: text, systemImage: systemImage, action: action)
            .padding(.trailing, 16)
            .padding(.bottom, 24)
            .accessibilityLabel(text)
    }
}

/// Icon-key picker grid presented in a sheet — used by shopping, meals, wishlists.
struct IconPickerSheet: View {
    let title: String
    let options: [String]
    let selected: String
    let symbolFor: (String) -> String
    let onPick: (String) -> Void
    /// When `onColorPick` is provided, a colour picker is shown too and applied live.
    var initialColor: Int?
    var onColorPick: ((Int?) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var color: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            ZStack {
                Text(title)
                    .font(.pushedTitle)
                    .foregroundStyle(Color.appOnSurface)
                HStack {
                    Button("Cancel") { dismiss() }
                        .font(.system(size: 17))
                        .foregroundStyle(Color.appPrimary)
                    Spacer()
                }
            }
            .frame(maxWidth: .infinity)
            IconGrid(options: options, selected: selected, symbolFor: symbolFor, onPick: onPick)
            if onColorPick != nil {
                SectionHeader(text: L("Color"))
                EventColorPicker(selection: Binding(
                    get: { color },
                    set: { color = $0; onColorPick?($0) }
                ))
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
        .onAppear { color = initialColor }
    }
}

/// New-list sheet with name + icon picker — mirrors NewListDialog.
private struct NewListSheet: View {
    let onCreate: (String, String, Int?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var selectedIcon = "shopping_cart"
    @State private var color: Int? = calendarEventColorPalette.first

    private var canCreate: Bool {
        !title.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(title: L("New list"), confirmTitle: L("Create"), confirmEnabled: canCreate) {
                dismiss()
            } onConfirm: {
                onCreate(title.trimmingCharacters(in: .whitespaces), selectedIcon, color)
                dismiss()
            }
            GlassField(systemImage: "cart", placeholder: L("List name"), text: $title)
            SectionHeader(text: L("Icon"))
            IconGrid(
                options: IconOptions.shopping,
                selected: selectedIcon,
                symbolFor: IconKeyMap.shoppingSymbol
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
