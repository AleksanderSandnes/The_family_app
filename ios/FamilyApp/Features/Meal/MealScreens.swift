// Meal screens: plan overview with progress + swipe delete + create sheet
// (name/icon/date range), detail with per-day inline editing and rename/change-icon
// menu.
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
                        title: L("No meal plans yet"),
                        subtitle: L("Plan your family's meals for the week ahead.")
                    )
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

            FloatingActionButton(text: L("Create a meal plan"), systemImage: "plus") {
                showCreate = true
            }
        }
        .ambientBackground()
        .featureTopBar(L("Meal planner"))
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showCreate) {
            CreatePlanSheet { name, fromIso, toIso, icon, color in
                viewModel.createPlan(name: name, fromIso: fromIso, toIso: toIso, icon: icon, color: color)
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
        let name = plan.name.isEmpty ? L("Meal plan") : plan.name
        let dateRange = "\(formatMealDate(plan.fromDate, locale: appLocale)) – \(formatMealDate(plan.toDate, locale: appLocale))"
        Button(action: onTap) {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    FeatureBadge(
                        systemImage: IconKeyMap.mealSymbol(plan.icon),
                        feature: .meals,
                        size: 44,
                        cornerRadius: Radius.badgeLarge,
                        colorOverride: hexColor(plan.color)
                    )
                    VStack(alignment: .leading, spacing: 2) {
                        Text(name)
                            .font(.system(size: 15.5, weight: .semibold))
                            .foregroundStyle(Color.appOnSurface)
                        Text(
                            "\(dateRange) · \(mealPlanLabel(progress: progress, fromIso: plan.fromDate, toIso: plan.toDate, locale: appLocale))"
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
            // Meal plans are always shown active — never dashed/greyed, even when empty
            // or fully planned.
            .rowSurface(ghost: false, cornerRadius: Radius.overviewCard)
        }
        .buttonStyle(PressScaleButtonStyle())
        .accessibilityLabel(
            "\(name), \(dateRange), \(mealPlanLabel(progress: progress, fromIso: plan.fromDate, toIso: plan.toDate, locale: appLocale))"
        )
    }
}

// MARK: - Create plan

private struct CreatePlanSheet: View {
    let onCreate: (_ name: String, _ fromIso: String, _ toIso: String, _ icon: String, _ color: Int?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var fromDate: Date?
    @State private var toDate: Date?
    @State private var selectedIcon = "restaurant"
    @State private var color: Int? = calendarEventColorPalette.first

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
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(title: L("New plan"), confirmTitle: L("Create"), confirmEnabled: canConfirm) {
                dismiss()
            } onConfirm: {
                guard let fromDate, let toDate else { return }
                onCreate(
                    name.trimmingCharacters(in: .whitespaces),
                    Self.isoFormat.string(from: fromDate),
                    Self.isoFormat.string(from: toDate),
                    selectedIcon,
                    color
                )
                dismiss()
            }
            GlassField(
                systemImage: IconKeyMap.mealSymbol(selectedIcon),
                placeholder: L("Plan name"),
                text: $name
            )
            SectionHeader(text: L("Icon"))
            IconGrid(
                options: IconOptions.meal,
                selected: selectedIcon,
                symbolFor: IconKeyMap.mealSymbol
            ) { selectedIcon = $0 }
            SectionHeader(text: L("Color"))
            EventColorPicker(selection: $color)
            HStack(spacing: Spacing.sm) {
                PlanDatePicker(label: L("Starts"), selection: $fromDate) { picked in
                    if let to = toDate, to < picked { toDate = picked }
                }
                PlanDatePicker(label: L("Ends"), selection: $toDate) { picked in
                    if let from = fromDate, picked < from { toDate = from }
                }
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
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
            HStack(spacing: Spacing.sm) {
                Image(systemName: "calendar")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.appPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label.uppercased())
                        .font(.sectionLabel)
                        .tracking(0.5)
                        .foregroundStyle(Color.appCaption)
                    Text(selection.map { formatMealDate(isoString(from: $0), locale: appLocale) } ?? L("Select"))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(selection == nil ? Color.appOnSurfaceVariant : Color.appOnSurface)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .frame(maxWidth: .infinity, minHeight: 58, alignment: .leading)
            .background(Color.appPrimary.opacity(0.10))
            .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        }
        .sheet(isPresented: $showPicker) {
            VStack(spacing: Spacing.lg) {
                DatePicker(label, selection: $draft, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding(Spacing.lg)
                PrimaryButton(text: L("OK")) {
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
            .padding(.top, 8)
            .padding(.bottom, Spacing.xl)
        }
        .ambientBackground()
        .featureTopBar(planTitle)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        renameText = viewModel.selectedPlan?.name ?? ""
                        showRename = true
                    } label: {
                        Label(L("Rename plan"), systemImage: "pencil")
                    }
                    Button { showIconPicker = true } label: {
                        Label(L("Change icon"), systemImage: "star")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel("More options")
                }
            }
        }
        .task(id: planId) { viewModel.loadPlanDetail(planId) }
        .inputDialog(
            isPresented: $showRename,
            title: L("Rename plan"),
            label: L("Name"),
            text: $renameText
        ) {
            if let plan = viewModel.selectedPlan,
               !renameText.trimmingCharacters(in: .whitespaces).isEmpty {
                viewModel.renamePlan(plan, newName: renameText.trimmingCharacters(in: .whitespaces))
            }
        }
        .sheet(isPresented: $showIconPicker) {
            IconPickerSheet(
                title: L("Change icon"),
                options: IconOptions.meal,
                selected: viewModel.selectedPlan?.icon ?? "restaurant",
                symbolFor: IconKeyMap.mealSymbol,
                onPick: { icon in
                    if let plan = viewModel.selectedPlan {
                        viewModel.setPlanIcon(plan, newIcon: icon)
                    }
                    showIconPicker = false
                },
                initialColor: viewModel.selectedPlan?.color,
                onColorPick: { color in
                    if let plan = viewModel.selectedPlan {
                        viewModel.setPlanColor(plan, color: color)
                    }
                }
            )
        }
    }

    private var planTitle: String {
        let name = viewModel.selectedPlan?.name ?? ""
        return name.isEmpty ? L("Meal plan") : name
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

    private var isToday: Bool {
        LocalDate(iso: day.date) == LocalDate.today()
    }

    /// Highlighted (accent day-circle + ring) when being edited or when it's today.
    private var highlighted: Bool {
        isEditing || isToday
    }

    private var weekday: String {
        String(day.day.prefix(3)).uppercased()
    }

    private var dayNumber: String {
        if let localDate = LocalDate(iso: day.date) { return "\(localDate.day)" }
        return String(day.date.split(separator: "-").last ?? "")
    }

    var body: some View {
        HStack(alignment: isEditing ? .top : .center, spacing: Spacing.md) {
            dateBadge
            if isEditing {
                editor
            } else {
                Text(day.food.isEmpty ? L("No plan yet") : day.food)
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
        .background {
            let shape = RoundedRectangle(cornerRadius: Radius.row, style: .continuous)
            if empty {
                shape.fill(Color(light: .white.opacity(0.36), dark: .white.opacity(0.04)))
                    .overlay(shape.strokeBorder(
                        Color(light: Color(hex: 0x5F6780).opacity(0.28), dark: .white.opacity(0.12)),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 3])
                    ))
            }
        }
        .modifier(MealRowGlass(active: !empty, editing: isEditing))
    }

    private var dateBadge: some View {
        VStack(spacing: 3) {
            Text(weekday)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Color.appCaption)
            if highlighted {
                Text(dayNumber)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(Color.appPrimary, in: Circle())
            } else {
                Text(dayNumber)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(empty ? Color.appCaption : Color.appOnSurface)
                    .frame(width: 28, height: 28)
            }
        }
        .frame(width: 46)
    }

    private var editor: some View {
        VStack(alignment: .trailing, spacing: Spacing.sm) {
            TextField("What's for \(day.day)?", text: $draft)
                .font(.system(size: 15.5))
                .tint(Color.appPrimary)
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
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Glass surface for a meal day row; the edited row gets a stronger accent ring + lift.
private struct MealRowGlass: ViewModifier {
    let active: Bool
    let editing: Bool
    func body(content: Content) -> some View {
        if active {
            content
                .glassCard(cornerRadius: Radius.row)
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.row, style: .continuous)
                        .strokeBorder(Color.appPrimary.opacity(editing ? 0.55 : 0), lineWidth: 1.5)
                )
                .accentGlow(active: editing, opacity: 0.2, radius: 10, y: 6)
        } else {
            content
        }
    }
}
