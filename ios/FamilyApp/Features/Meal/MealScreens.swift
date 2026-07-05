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
        .ambientBackground()
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

    private var fraction: Double {
        guard let progress, progress.total > 0 else { return 0 }
        return Double(progress.planned) / Double(progress.total)
    }

    private var nothingPlanned: Bool {
        (progress?.planned ?? 0) == 0
    }

    var body: some View {
        let name = plan.name.isEmpty ? "Meal plan" : plan.name
        let dateRange = "\(formatMealDate(plan.fromDate)) – \(formatMealDate(plan.toDate))"
        Button(action: onTap) {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    FeatureBadge(
                        systemImage: IconKeyMap.mealSymbol(plan.icon),
                        feature: .meals,
                        size: 44,
                        cornerRadius: Radius.badgeLarge
                    )
                    .opacity(nothingPlanned ? 0.7 : 1)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(name)
                            .font(.system(size: 15.5, weight: .semibold))
                            .foregroundStyle(nothingPlanned ? Color.appOnSurfaceVariant : Color.appOnSurface)
                        Text(
                            "\(dateRange) · \(mealPlanLabel(progress: progress, fromIso: plan.fromDate, toIso: plan.toDate))"
                        )
                        .font(.caption)
                        .foregroundStyle(Color.appCaption)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.appCaption)
                }
                if !nothingPlanned {
                    GlassProgressBar(value: fraction, tint: FeatureAccent.meals.stroke, height: 4)
                }
            }
            .padding(Spacing.cardPadding)
            .rowSurface(ghost: nothingPlanned, cornerRadius: Radius.overviewCard)
        }
        .buttonStyle(PressScaleButtonStyle())
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
        VStack(spacing: 0) {
            SheetHeader(title: "New plan", confirmTitle: "Create", confirmEnabled: canConfirm) {
                dismiss()
            } onConfirm: {
                guard let fromDate, let toDate else { return }
                onCreate(
                    name.trimmingCharacters(in: .whitespaces),
                    Self.isoFormat.string(from: fromDate),
                    Self.isoFormat.string(from: toDate),
                    selectedIcon
                )
                dismiss()
            }
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.vertical, Spacing.lg)

            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    GlassField(
                        systemImage: IconKeyMap.mealSymbol(selectedIcon),
                        placeholder: "Plan name",
                        text: $name
                    )
                    SectionHeader(text: "Icon")
                    IconGrid(
                        options: IconOptions.meal,
                        selected: selectedIcon,
                        symbolFor: IconKeyMap.mealSymbol
                    ) { selectedIcon = $0 }
                    HStack(spacing: Spacing.sm) {
                        PlanDatePicker(label: "Start date", selection: $fromDate) { picked in
                            if let to = toDate, to < picked { toDate = picked }
                        }
                        PlanDatePicker(label: "End date", selection: $toDate) { picked in
                            if let from = fromDate, picked < from { toDate = from }
                        }
                    }
                }
                .padding(.horizontal, Spacing.screenEdge)
                .padding(.bottom, Spacing.lg)
            }
        }
        .glassSheet(detents: [.medium, .large])
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
                VStack(spacing: 8) {
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
                    }
                }
                .padding(.horizontal, Spacing.screenEdge)
                .padding(.top, 6)
            }
            .padding(.bottom, Spacing.xl)
        }
        .ambientBackground()
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
            FeatureBadge(
                systemImage: IconKeyMap.mealSymbol(plan.icon),
                feature: .meals,
                size: 44,
                cornerRadius: Radius.badgeLarge
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(planTitle)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color.appOnSurface)
                Text("\(formatMealDate(plan.fromDate)) – \(formatMealDate(plan.toDate))")
                    .font(.caption)
                    .foregroundStyle(Color.appCaption)
            }
            Spacer()
        }
        .padding(Spacing.lg)
        .glassCard(cornerRadius: Radius.bigCard)
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, 8)
        .padding(.bottom, 6)
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

    private var empty: Bool {
        day.food.isEmpty && !isEditing
    }

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: 1) {
                Text(day.day)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(empty ? Color.appOnSurfaceVariant : Color.appOnSurface)
                Text(formatMealDate(day.date))
                    .font(.system(size: 11.5))
                    .foregroundStyle(Color.appCaption)
            }
            .frame(width: 96, alignment: .leading)

            if isEditing {
                VStack(alignment: .trailing, spacing: Spacing.xs) {
                    TextField("What's for \(day.day)?", text: $draft)
                        .font(.system(size: 15.5))
                        .tint(Color.appPrimary)
                        .padding(.horizontal, Spacing.md)
                        .frame(minHeight: 42)
                        .background(
                            Color.appSurface.opacity(0.75),
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(Color.appPrimary.opacity(0.55), lineWidth: 1.5)
                        )
                        .focused($focused)
                        .onSubmit(onSave)
                        .onAppear { focused = true }
                    HStack(spacing: Spacing.lg) {
                        Button("Cancel", action: onCancel)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appCaption)
                        Button("Save", action: onSave)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.appPrimary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            } else {
                Text(day.food.isEmpty ? "No plan yet" : day.food)
                    .font(.system(size: 15.5))
                    .foregroundStyle(day.food.isEmpty ? Color.appCaption : Color.appOnSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onStartEdit)
                Button(action: onStartEdit) {
                    Image(systemName: "pencil")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.appPrimary)
                }
                .accessibilityLabel("Edit \(day.day) meal")
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
        .rowSurface(ghost: empty, cornerRadius: Radius.row)
    }
}
