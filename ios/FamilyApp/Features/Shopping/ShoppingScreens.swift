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
                        title: "No lists yet",
                        subtitle: "Create a shared shopping list for your family.",
                        actionLabel: "New list"
                    ) { showAdd = true }
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

            FloatingActionButton(text: "New list", systemImage: "plus") { showAdd = true }
        }
        .background(Color.appBackground)
        .featureTopBar("Shopping lists")
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            NewListSheet { title, icon in
                viewModel.addList(title: title, icon: icon)
                showAdd = false
            }
        }
    }
}

private struct ShoppingListRow: View {
    let list: ShoppingListModel
    let progress: ListProgress?
    let onTap: () -> Void

    var body: some View {
        ListCard(onTap: onTap) {
            Image(systemName: IconKeyMap.shoppingSymbol(list.icon))
                .font(.system(size: 20))
                .foregroundStyle(Color.appPrimary)
                .frame(width: 44, height: 44)
            Spacer().frame(width: Spacing.sm)
            VStack(alignment: .leading, spacing: 1) {
                Text(list.title)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text(shoppingProgressLabel(progress))
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(Color.appOnSurfaceVariant)
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

    private var active: [ShoppingItemModel] { viewModel.items.filter { !$0.checked } }
    private var completed: [ShoppingItemModel] { viewModel.items.filter(\.checked) }
    private var remaining: Int { active.count }

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.items.isEmpty {
                EmptyState(
                    systemImage: "cart.fill",
                    title: "Empty list",
                    subtitle: "Tap the field below to add your first item."
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
        .background(Color.appBackground)
        .featureTopBar(viewModel.selectedList?.title ?? "List")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !viewModel.items.isEmpty {
                    Text("\(remaining) left")
                        .font(.labelMedium.weight(.semibold))
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, 4)
                        .background(Color.appPrimaryContainer)
                        .foregroundStyle(Color.appOnPrimaryContainer)
                        .clipShape(Capsule())
                }
                Menu {
                    Button("Rename list") {
                        renameText = viewModel.selectedList?.title ?? ""
                        showRename = true
                    }
                    Button("Change icon") { showChangeIcon = true }
                    if !completed.isEmpty {
                        Button("Clear completed", role: .destructive) {
                            viewModel.clearCompleted(listId: listId)
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
            title: "Rename list",
            label: "List name",
            text: $renameText
        ) {
            if !renameText.trimmingCharacters(in: .whitespaces).isEmpty {
                viewModel.renameList(listId: listId, newTitle: renameText.trimmingCharacters(in: .whitespaces))
            }
        }
        .sheet(isPresented: $showChangeIcon) {
            IconPickerSheet(
                title: "Change icon",
                options: IconOptions.shopping,
                selected: viewModel.selectedList?.icon ?? "shopping_cart",
                symbolFor: IconKeyMap.shoppingSymbol
            ) { icon in
                viewModel.changeListIcon(listId: listId, icon: icon)
                showChangeIcon = false
            }
        }
    }

    private var addItemBar: some View {
        HStack(spacing: Spacing.sm) {
            TextField("Add item…", text: $newItemText)
                .font(.bodyLarge)
                .padding(.horizontal, Spacing.lg)
                .frame(minHeight: 48)
                .overlay(
                    Capsule().strokeBorder(Color.appOnSurface.opacity(0.45), lineWidth: 1)
                )
                .onSubmit(addItem)
            Button(action: addItem) {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(
                        newItemText.isEmpty ? Color.appOnSurfaceVariant : Color.appPrimary
                    )
            }
            .disabled(newItemText.isEmpty)
            .accessibilityLabel("Add item")
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .background(Color.appSurface)
    }

    private func addItem() {
        let text = newItemText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        viewModel.addItem(listId: listId, item: text)
        newItemText = ""
    }
}

private extension View {
    func shoppingRowStyle() -> some View {
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
        HStack(spacing: Spacing.sm) {
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                viewModel.toggle(item)
            } label: {
                Image(systemName: item.checked ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(item.checked ? Color.appPrimary : Color.appOnSurfaceVariant)
            }
            .buttonStyle(.plain)

            if isEditing {
                TextField("Item", text: $editText)
                    .font(.bodyLarge)
                    .focused($editFocused)
                    .onSubmit(commitEdit)
                    .onChange(of: editFocused) { _, focused in
                        if !focused { commitEdit() }
                    }
            } else {
                Text(item.item)
                    .font(.bodyLarge)
                    .foregroundStyle(item.checked ? Color.appOnSurfaceVariant : Color.appOnSurface)
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
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, 10)
        .background(Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
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
                Text("Completed (\(count))")
                    .font(.labelLarge)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                Spacer()
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(expanded ? "Hide completed items" : "Show completed items")
    }
}

// MARK: - Shared pieces (also used by meals)

/// Extended floating action button — parity with Android's AppFab.
struct FloatingActionButton: View {
    let text: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: systemImage)
                Text(text)
                    .font(.titleMedium)
            }
            .foregroundStyle(Color.appOnPrimary)
            .padding(.horizontal, Spacing.xl)
            .frame(minHeight: 56)
            .background(Color.appPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
            .shadow(color: .black.opacity(0.2), radius: Elevation.raised, y: 3)
        }
        .padding(Spacing.screenEdge)
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

    private let columns = Array(repeating: GridItem(.flexible()), count: 4)

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text(title)
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            LazyVGrid(columns: columns, spacing: Spacing.md) {
                ForEach(options, id: \.self) { key in
                    Button {
                        onPick(key)
                    } label: {
                        Image(systemName: symbolFor(key))
                            .font(.system(size: 22))
                            .foregroundStyle(
                                key == selected ? Color.appOnPrimary : Color.appPrimary
                            )
                            .frame(width: 56, height: 56)
                            .background(
                                key == selected ? Color.appPrimary : Color.appPrimaryContainer
                            )
                            .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
                    }
                }
            }
            .padding(.horizontal, Spacing.screenEdge)
            Spacer()
        }
        .presentationDetents([.medium])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
    }
}

/// New-list sheet with name + icon picker — mirrors NewListDialog.
private struct NewListSheet: View {
    let onCreate: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var selectedIcon = "shopping_cart"

    private let columns = Array(repeating: GridItem(.flexible()), count: 4)

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("New shopping list")
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            FamilyTextField(label: "List name", text: $title, systemImage: "cart")
            SectionHeader(text: "Icon")
            LazyVGrid(columns: columns, spacing: Spacing.md) {
                ForEach(IconOptions.shopping, id: \.self) { key in
                    Button {
                        selectedIcon = key
                    } label: {
                        Image(systemName: IconKeyMap.shoppingSymbol(key))
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
            PrimaryButton(text: "Create", enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                onCreate(title.trimmingCharacters(in: .whitespaces), selectedIcon)
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
