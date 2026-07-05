// Meal screens — the iOS twin of MealScreens.kt: plan overview with progress + swipe
// delete + create sheet (name/icon/date range), detail with per-day inline editing and
// rename/change-icon menu.
import SwiftUI

struct MealScreen: View {
    let viewModel: MealViewModel
    let onOpen: (String) -> Void

    @State private var showCreate = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if viewModel.isLoading {
                    LoadingState()
                        .frame(maxHeight: .infinity, alignment: .top)
                } else if viewModel.plans.isEmpty {
                    EmptyState(
                        systemImage: "fork.knife",
                        title: "No meal plans yet",
                        subtitle: "Plan your family's meals for the week ahead.",
                        actionLabel: "Create a meal plan"
                    ) { showCreate = true }
                } else {
                    List {
                        ForEach(viewModel.plans) { plan in
                            MealPlanRow(
                                plan: plan,
                                progress: viewModel.planProgress[plan.id]
                            ) { onOpen(plan.id) }
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets(
                                top: 6, leading: Spacing.screenEdge,
                                bottom: 6, trailing: Spacing.screenEdge
                            ))
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewModel.deletePlan(plan)
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

            FloatingActionButton(text: "Create a meal plan", systemImage: "plus") {
                showCreate = true
            }
        }
        .background(Color.appBackground)
        .featureTopBar("Meal planner")
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showCreate) {
            CreatePlanSheet { name, fromIso, toIso, icon in
                viewModel.createPlan(name: name, fromIso: fromIso, toIso: toIso, icon: icon)
                showCreate = false
            }
        }
    }
}

private struct MealPlanRow: View {
    let plan: MealPlanModel
    let progress: MealProgress?
    let onTap: () -> Void

    var body: some View {
        let name = plan.name.isEmpty ? "Meal plan" : plan.name
        let dateRange = "\(formatMealDate(plan.fromDate)) – \(formatMealDate(plan.toDate))"
        ListCard(onTap: onTap) {
            Image(systemName: IconKeyMap.mealSymbol(plan.icon))
                .font(.system(size: 18))
                .foregroundStyle(Color.appOnPrimaryContainer)
                .frame(width: 40, height: 40)
                .background(Color.appPrimaryContainer)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            Spacer().frame(width: 14)
            VStack(alignment: .leading, spacing: 1) {
                Text(name)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text(dateRange)
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                Text(mealPlanLabel(progress: progress, fromIso: plan.fromDate, toIso: plan.toDate))
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
        .accessibilityLabel(
            "\(name), \(dateRange), \(mealPlanLabel(progress: progress, fromIso: plan.fromDate, toIso: plan.toDate))"
        )
    }
}

// MARK: - Create plan

private struct CreatePlanSheet: View {
    let onCreate: (_ name: String, _ fromIso: String, _ toIso: String, _ icon: String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var fromDate: Date?
    @State private var toDate: Date?
    @State private var selectedIcon = "restaurant"
    @State private var showIconPicker = false

    private static let isoFormat: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private var canConfirm: Bool {
        guard let fromDate, let toDate else { return false }
        return !name.trimmingCharacters(in: .whitespaces).isEmpty && toDate >= fromDate
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text("Create a meal plan")
                    .font(.titleLarge)
                    .foregroundStyle(Color.appOnSurface)
                    .padding(.top, Spacing.xxl)

                HStack(spacing: Spacing.md) {
                    Button {
                        withAnimation { showIconPicker.toggle() }
                    } label: {
                        Image(systemName: IconKeyMap.mealSymbol(selectedIcon))
                            .font(.system(size: 22))
                            .foregroundStyle(Color.appOnPrimaryContainer)
                            .frame(width: 48, height: 48)
                            .background(Color.appPrimaryContainer)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .accessibilityLabel("Change icon")
                    FamilyTextField(label: "Plan name", text: $name)
                }

                if showIconPicker {
                    let columns = Array(repeating: GridItem(.flexible()), count: 4)
                    LazyVGrid(columns: columns, spacing: Spacing.md) {
                        ForEach(IconOptions.meal, id: \.self) { key in
                            Button {
                                selectedIcon = key
                                withAnimation { showIconPicker = false }
                            } label: {
                                Image(systemName: IconKeyMap.mealSymbol(key))
                                    .font(.system(size: 22))
                                    .foregroundStyle(
                                        key == selectedIcon ? Color.appOnPrimary : Color.appPrimary
                                    )
                                    .frame(width: 56, height: 56)
                                    .background(
                                        key == selectedIcon
                                            ? Color.appPrimary : Color.appPrimaryContainer
                                    )
                                    .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
                            }
                        }
                    }
                }

                HStack(spacing: Spacing.sm) {
                    PlanDatePicker(label: "Start date", selection: $fromDate) { picked in
                        if let to = toDate, to < picked { toDate = picked }
                    }
                    PlanDatePicker(label: "End date", selection: $toDate) { picked in
                        if let from = fromDate, picked < from { toDate = from }
                    }
                }

                PrimaryButton(text: "Create", enabled: canConfirm) {
                    guard let fromDate, let toDate else { return }
                    onCreate(
                        name.trimmingCharacters(in: .whitespaces),
                        Self.isoFormat.string(from: fromDate),
                        Self.isoFormat.string(from: toDate),
                        selectedIcon
                    )
                    dismiss()
                }
            }
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.bottom, Spacing.lg)
        }
        .presentationDetents([.large])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
    }
}

/// Compact date button that opens a graphical picker sheet.
private struct PlanDatePicker: View {
    let label: String
    @Binding var selection: Date?
    var onPicked: (Date) -> Void = { _ in }

    @State private var showPicker = false
    @State private var draft = Date()

    var body: some View {
        Button {
            draft = selection ?? Date()
            showPicker = true
        } label: {
            Text(selection.map { formatMealDate(isoString(from: $0)) } ?? label)
                .font(.bodyMedium)
                .foregroundStyle(selection == nil ? Color.appOnSurfaceVariant : Color.appOnSurface)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Color.appSurfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .sheet(isPresented: $showPicker) {
            VStack(spacing: Spacing.lg) {
                DatePicker(label, selection: $draft, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding(Spacing.lg)
                PrimaryButton(text: "OK") {
                    selection = draft
                    onPicked(draft)
                    showPicker = false
                }
                .padding(.horizontal, Spacing.screenEdge)
            }
            .padding(.bottom, Spacing.lg)
            .presentationDetents([.medium, .large])
            .presentationCornerRadius(Radius.sheet)
        }
    }

    private func isoString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.string(from: date)
    }
}

// MARK: - Detail

struct MealDetailScreen: View {
    let planId: String
    let viewModel: MealViewModel

    @State private var editingDayId: String?
    @State private var draft = ""
    @State private var showRename = false
    @State private var showIconPicker = false
    @State private var renameText = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if let plan = viewModel.selectedPlan {
                    planHeader(plan)
                }
                ForEach(viewModel.days) { day in
                    MealDayRow(
                        day: day,
                        isEditing: editingDayId == day.id,
                        draft: $draft,
                        onStartEdit: {
                            editingDayId = day.id
                            draft = day.food
                        },
                        onSave: {
                            viewModel.setFood(day, food: draft)
                            editingDayId = nil
                        },
                        onCancel: { editingDayId = nil }
                    )
                    Divider().opacity(0.5)
                }
            }
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .featureTopBar(planTitle)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Rename") {
                        renameText = viewModel.selectedPlan?.name ?? ""
                        showRename = true
                    }
                    Button("Change icon") { showIconPicker = true }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel("More options")
                }
            }
        }
        .task(id: planId) { viewModel.loadPlanDetail(planId) }
        .inputDialog(
            isPresented: $showRename,
            title: "Rename plan",
            label: "Name",
            text: $renameText
        ) {
            if let plan = viewModel.selectedPlan,
               !renameText.trimmingCharacters(in: .whitespaces).isEmpty {
                viewModel.renamePlan(plan, newName: renameText.trimmingCharacters(in: .whitespaces))
            }
        }
        .sheet(isPresented: $showIconPicker) {
            IconPickerSheet(
                title: "Change icon",
                options: IconOptions.meal,
                selected: viewModel.selectedPlan?.icon ?? "restaurant",
                symbolFor: IconKeyMap.mealSymbol
            ) { icon in
                if let plan = viewModel.selectedPlan {
                    viewModel.setPlanIcon(plan, newIcon: icon)
                }
                showIconPicker = false
            }
        }
    }

    private var planTitle: String {
        let name = viewModel.selectedPlan?.name ?? ""
        return name.isEmpty ? "Meal plan" : name
    }

    private func planHeader(_ plan: MealPlanModel) -> some View {
        HStack(spacing: 14) {
            Image(systemName: IconKeyMap.mealSymbol(plan.icon))
                .font(.system(size: 18))
                .foregroundStyle(Color.appOnPrimaryContainer)
                .frame(width: 40, height: 40)
                .background(Color.appPrimaryContainer)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(planTitle)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text("\(formatMealDate(plan.fromDate)) – \(formatMealDate(plan.toDate))")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.vertical, Spacing.lg)
        .background(Color.appSurfaceVariant.opacity(0.5))
    }
}

private struct MealDayRow: View {
    let day: MealPlanDayModel
    let isEditing: Bool
    @Binding var draft: String
    let onStartEdit: () -> Void
    let onSave: () -> Void
    let onCancel: () -> Void

    @FocusState private var focused: Bool

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.sm) {
            VStack(alignment: .leading, spacing: 1) {
                Text(day.day)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text(formatMealDate(day.date))
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if isEditing {
                VStack(alignment: .trailing, spacing: Spacing.xs) {
                    TextField("What's for \(day.day)?", text: $draft)
                        .font(.bodyLarge)
                        .padding(.horizontal, Spacing.md)
                        .frame(minHeight: 44)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(Color.appPrimary, lineWidth: 1)
                        )
                        .focused($focused)
                        .onSubmit(onSave)
                        .onAppear { focused = true }
                    HStack {
                        Button("Cancel", action: onCancel)
                            .font(.labelLarge)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                        Button("Save", action: onSave)
                            .font(.labelLarge)
                            .foregroundStyle(Color.appPrimary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            } else {
                Text(day.food.isEmpty ? "Tap to add meal" : day.food)
                    .font(.bodyLarge)
                    .foregroundStyle(
                        day.food.isEmpty ? Color.appOnSurfaceVariant : Color.appOnSurface
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onStartEdit)
                Button(action: onStartEdit) {
                    Image(systemName: "pencil")
                        .foregroundStyle(Color.appOnSurfaceVariant)
                }
                .accessibilityLabel("Edit \(day.day) meal")
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.vertical, 14)
        .background(Color.appSurface)
    }
}
