// Home dashboard — the iOS twin of HomeScreen.kt: greeting header with avatar,
// gradient family card / no-family banner, glanceable summary cards, feature tile grid.
import NukeUI
import SwiftUI

private struct Feature: Identifiable {
    let title: String
    let subtitle: String
    let systemImage: String
    let accent: FeatureAccent
    /// nil = Calendar, which is a bottom tab rather than a pushed route.
    let route: Route?

    var id: String {
        title
    }
}

private let features: [Feature] = [
    Feature(
        title: "Shopping",
        subtitle: "Shared lists",
        systemImage: "cart.fill",
        accent: .shopping,
        route: .shopping
    ),
    Feature(
        title: "Meals",
        subtitle: "Plan the week",
        systemImage: "fork.knife",
        accent: .meals,
        route: .meal
    ),
    Feature(
        title: "Calendar",
        subtitle: "Family events",
        systemImage: "calendar",
        accent: .calendar,
        route: nil
    ),
    Feature(
        title: "Birthdays",
        subtitle: "Never miss one",
        systemImage: "birthday.cake.fill",
        accent: .birthdays,
        route: .birthday
    ),
    Feature(
        title: "Wishlists",
        subtitle: "Gift ideas",
        systemImage: "gift.fill",
        accent: .wishlists,
        route: .wishlist
    ),
    Feature(
        title: "Family Map",
        subtitle: "See where everyone is",
        systemImage: "map.fill",
        accent: .map,
        route: .familyMap
    ),
]

struct HomeScreen: View {
    let viewModel: HomeViewModel
    let onOpen: (Route) -> Void
    let onOpenCalendarTab: () -> Void
    let onOpenFamily: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HomeHeader(state: viewModel.state, dark: colorScheme == .dark, onOpenFamily: onOpenFamily)

                if viewModel.state.isLoading {
                    LoadingState()
                } else if viewModel.state.loadError {
                    ErrorBanner(message: L("Couldn't load your data. Pull to refresh."))
                } else {
                    if viewModel.state.hasSummary {
                        summarySection
                        SectionHeader(text: L("Quick access"))
                    }
                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(features) { feature in
                            FeatureTile(feature: feature) {
                                if let route = feature.route {
                                    onOpen(route)
                                } else {
                                    onOpenCalendarTab()
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.vertical, Spacing.xl)
        }
        .ambientBackground()
        .refreshable { viewModel.refresh() }
        .resumeEffect { viewModel.refresh() }
    }

    @ViewBuilder private var summarySection: some View {
        let state = viewModel.state
        VStack(spacing: 9) {
            if let tonight = state.tonightMeal {
                SummaryCard(
                    systemImage: "fork.knife",
                    feature: .meals,
                    label: L("TODAY"),
                    value: tonight,
                    detail: nil
                ) { onOpen(.meal) }
            }
            if let event = state.nextEvent {
                EventSummaryCard(
                    event: event,
                    detail: state.nextEventWhen,
                    members: state.familyMembers
                ) {
                    onOpenCalendarTab()
                }
            }
            if state.shoppingRemaining > 0 {
                SummaryCard(
                    systemImage: "cart.fill",
                    feature: .shopping,
                    label: L("SHOPPING"),
                    value: L("\(state.shoppingRemaining) left to buy"),
                    detail: nil
                ) { onOpen(.shopping) }
            }
            if let birthdayName = state.nextBirthdayName {
                SummaryCard(
                    systemImage: "birthday.cake.fill",
                    feature: .birthdays,
                    label: L("NEXT BIRTHDAY"),
                    value: birthdayName,
                    detail: state.nextBirthdayWhen
                ) { onOpen(.birthday) }
            }
        }
    }
}

// MARK: - Pieces

private struct SummaryCard: View {
    let systemImage: String
    let feature: FeatureAccent
    let label: String
    let value: String
    let detail: String?
    let onTap: () -> Void

    var body: some View {
        ListCard(onTap: onTap) {
            FeatureBadge(systemImage: systemImage, feature: feature)
            Spacer().frame(width: 14)
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.eyebrow)
                    .tracking(0.7)
                    .foregroundStyle(feature.stroke)
                Text(value)
                    .font(.cardTitle)
                    .foregroundStyle(Color.appOnSurface)
                    .lineLimit(1)
                if let detail {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(Color.appCaption)
                        .lineLimit(1)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.appCaption)
        }
    }
}

/// NEXT EVENT card — mirrors the calendar event's own icon, colour and attendees so the
/// dashboard reflects the real event rather than a generic calendar glyph.
private struct EventSummaryCard: View {
    let event: CalendarEventModel
    let detail: String?
    let members: [UserModel]
    let onTap: () -> Void

    /// Creator first, then everyone tagged as "going with".
    private var people: [UserModel] {
        ([event.userId] + event.attendeeIds).compactMap { id in members.first { $0.id == id } }
    }

    private var accent: Color? {
        event.color.map { Color(hex: UInt32(truncatingIfNeeded: $0)) }
    }

    var body: some View {
        ListCard(onTap: onTap) {
            FeatureBadge(
                systemImage: IconKeyMap.calendarSymbol(event.icon),
                feature: .calendar,
                colorOverride: accent
            )
            Spacer().frame(width: 14)
            VStack(alignment: .leading, spacing: 2) {
                Text(L("NEXT EVENT"))
                    .font(.eyebrow)
                    .tracking(0.7)
                    .foregroundStyle(accent ?? FeatureAccent.calendar.stroke)
                Text(event.activity)
                    .font(.cardTitle)
                    .foregroundStyle(Color.appOnSurface)
                    .lineLimit(1)
                if let detail {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(Color.appCaption)
                        .lineLimit(1)
                }
            }
            Spacer()
            if !people.isEmpty {
                HStack(spacing: -10) {
                    ForEach(people.prefix(3)) { person in
                        InitialAvatar(user: person, size: 36)
                            .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 2))
                    }
                }
                .padding(.leading, 4)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.appCaption)
        }
    }
}

private struct HomeHeader: View {
    let state: HomeUiState
    let dark: Bool
    let onOpenFamily: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            HStack(alignment: .center, spacing: Spacing.md) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(greetingLine)
                        .font(.greeting)
                        .foregroundStyle(Color.appOnBackground)
                    Text("Here's everything your family shares.")
                        .font(.caption)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let user = state.user {
                    InitialAvatar(user: user, size: 44)
                }
            }

            if state.user != nil {
                if let family = state.family {
                    FamilyCard(
                        family: family,
                        memberCount: state.memberCount,
                        dark: dark,
                        onTap: onOpenFamily
                    )
                } else {
                    NoFamilyBanner(onOpenFamily: onOpenFamily)
                }
            }
        }
    }

    private var greetingLine: String {
        let greeting = timeBasedGreeting(locale: appLocale)
        let firstName = state.user?.name.split(separator: " ").first.map(String.init) ?? ""
        return firstName.isEmpty ? greeting : "\(greeting), \(firstName)"
    }
}

private struct FamilyCard: View {
    let family: FamilyModel
    let memberCount: Int
    let dark: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(Color.white.opacity(0.2))
                    if let photoUrl = family.photoUrl, let url = URL(string: photoUrl) {
                        LazyImage(url: url) { phase in
                            if let image = phase.image {
                                image.resizable().scaledToFill()
                            }
                        }
                        .clipShape(Circle())
                    } else {
                        Image(systemName: "person.3.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 52, height: 52)

                VStack(alignment: .leading, spacing: 2) {
                    Text(family.name)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                    Text(memberCount == 1 ? L("1 member") : L("\(memberCount) members"))
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.85))
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .padding(Spacing.xl)
            .background(Gradients.hero(dark: dark))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            // Identity surface — inset shine + hairline + coloured drop shadow (spec 2a).
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5)
            )
            .shadow(
                color: (dark ? Color.black : Palette.indigo600).opacity(dark ? 0.4 : 0.28),
                radius: dark ? 17 : 15,
                x: 0,
                y: dark ? 12 : 10
            )
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

private struct NoFamilyBanner: View {
    let onOpenFamily: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("No family yet")
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnPrimaryContainer)
                Text("Join or create a family to get started")
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnPrimaryContainer.opacity(0.8))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button("Get started", action: onOpenFamily)
                .font(.bodyMedium.weight(.semibold))
                .foregroundStyle(Color.appPrimary)
        }
        .padding(Spacing.lg)
        .background(Color.appPrimaryContainer)
        .clipShape(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous))
    }
}

private struct FeatureTile: View {
    let feature: Feature
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                FeatureBadge(systemImage: feature.systemImage, feature: feature.accent, size: 36)
                Spacer(minLength: Spacing.sm)
                Text(LocalizedStringKey(feature.title))
                    .font(.cardTitle)
                    .foregroundStyle(Color.appOnSurface)
                    .lineLimit(1)
                Text(LocalizedStringKey(feature.subtitle))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appCaption)
                    .lineLimit(1)
            }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 112, alignment: .leading)
            .glassCard(cornerRadius: Radius.row)
            // Pin the hit region to the visible tile — the glass effect inside a
            // LazyVGrid otherwise leaves the Button's tap target misaligned.
            .contentShape(RoundedRectangle(cornerRadius: Radius.row, style: .continuous))
        }
        .buttonStyle(PressScaleButtonStyle())
        .accessibilityLabel(Text("\(Text(LocalizedStringKey(feature.title))) feature"))
    }
}
