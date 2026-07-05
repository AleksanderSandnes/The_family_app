// Home dashboard — the iOS twin of HomeScreen.kt: greeting header with avatar,
// gradient family card / no-family banner, glanceable summary cards, feature tile grid.
import NukeUI
import SwiftUI

private struct Feature: Identifiable {
    let title: String
    let subtitle: String
    let systemImage: String
    let color: Color
    /// nil = Calendar, which is a bottom tab rather than a pushed route.
    let route: Route?

    var id: String { title }
}

private let features: [Feature] = [
    Feature(title: "Shopping", subtitle: "Shared lists", systemImage: "cart.fill",
            color: Palette.indigo500, route: .shopping),
    Feature(title: "Meals", subtitle: "Plan the week", systemImage: "fork.knife",
            color: Palette.amber500, route: .meal),
    Feature(title: "Calendar", subtitle: "Family events", systemImage: "calendar",
            color: Palette.teal500, route: nil),
    Feature(title: "Birthdays", subtitle: "Never miss one", systemImage: "birthday.cake.fill",
            color: Palette.pink500, route: .birthday),
    Feature(title: "Wishlists", subtitle: "Gift ideas", systemImage: "gift.fill",
            color: Palette.violet500, route: .wishlist),
    Feature(title: "Family Map", subtitle: "See where everyone is", systemImage: "map.fill",
            color: Palette.emerald500, route: .familyMap),
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
                    ErrorBanner(message: "Couldn't load your data. Pull to refresh.")
                } else {
                    if viewModel.state.hasSummary {
                        summarySection
                        SectionHeader(text: "Quick access")
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
        .background(Color.appBackground)
        .refreshable { viewModel.refresh() }
        .resumeEffect { viewModel.refresh() }
    }

    @ViewBuilder private var summarySection: some View {
        let state = viewModel.state
        VStack(spacing: Spacing.cardGap) {
            if let tonight = state.tonightMeal {
                SummaryCard(systemImage: "fork.knife", accent: Palette.amber500,
                            label: "TONIGHT", value: tonight, detail: nil) { onOpen(.meal) }
            }
            if let eventTitle = state.nextEventTitle {
                SummaryCard(systemImage: "calendar", accent: Palette.teal500,
                            label: "NEXT EVENT", value: eventTitle, detail: state.nextEventWhen) {
                    onOpenCalendarTab()
                }
            }
            if state.shoppingRemaining > 0 {
                SummaryCard(systemImage: "cart.fill", accent: Palette.indigo500,
                            label: "SHOPPING", value: "\(state.shoppingRemaining) left to buy",
                            detail: nil) { onOpen(.shopping) }
            }
            if let birthdayName = state.nextBirthdayName {
                SummaryCard(systemImage: "birthday.cake.fill", accent: Palette.pink500,
                            label: "NEXT BIRTHDAY", value: birthdayName,
                            detail: state.nextBirthdayWhen) { onOpen(.birthday) }
            }
        }
    }
}

// MARK: - Pieces

private struct SummaryCard: View {
    let systemImage: String
    let accent: Color
    let label: String
    let value: String
    let detail: String?
    let onTap: () -> Void

    var body: some View {
        ListCard(onTap: onTap) {
            IconBadge(systemImage: systemImage, color: accent)
            Spacer().frame(width: 14)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                Text(value)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                    .lineLimit(1)
                if let detail {
                    Text(detail)
                        .font(.labelMedium)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                        .lineLimit(1)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
    }
}

/// 44pt gradient icon square used by summary cards and feature tiles.
struct IconBadge: View {
    let systemImage: String
    let color: Color
    var size: CGFloat = 44

    var body: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(LinearGradient(
                colors: [color, color.opacity(0.7)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ))
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: systemImage)
                    .font(.system(size: size * 0.5))
                    .foregroundStyle(.white)
            )
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
                        .font(.headlineMedium)
                        .foregroundStyle(Color.appOnBackground)
                    Text("Here's everything your family shares.")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let user = state.user {
                    InitialAvatar(user: user, size: 48)
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
        let greeting = timeBasedGreeting()
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
                        .font(.titleLarge.weight(.bold))
                        .foregroundStyle(.white)
                    Text("\(memberCount) member\(memberCount == 1 ? "" : "s")")
                        .font(.bodyMedium)
                        .foregroundStyle(.white.opacity(0.85))
                }
                Spacer()
            }
            .padding(Spacing.xl)
            .background(Gradients.hero(dark: dark))
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 4, y: 2)
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
                IconBadge(systemImage: feature.systemImage, color: feature.color)
                Spacer(minLength: Spacing.sm)
                Text(feature.title)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                    .lineLimit(1)
                Text(feature.subtitle)
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                    .lineLimit(1)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .aspectRatio(1.15, contentMode: .fit)
            .background(Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.medium, style: .continuous))
            .shadow(color: .black.opacity(0.06), radius: Elevation.resting, y: 1)
        }
        .buttonStyle(PressScaleButtonStyle())
        .accessibilityLabel("\(feature.title) feature")
    }
}
