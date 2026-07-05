// Card + section building blocks — mirror ListCard / SectionHeader / EmptyState /
// LoadingState from Components.kt.
import SwiftUI

/// Standard surface card for list rows. One radius, one elevation, one padding.
struct ListCard<Content: View>: View {
    var onTap: (() -> Void)?
    @ViewBuilder let content: () -> Content

    var body: some View {
        Group {
            if let onTap {
                Button(action: onTap) { inner }
                    .buttonStyle(.plain)
            } else {
                inner
            }
        }
    }

    private var inner: some View {
        HStack(spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.cardPadding)
            .background(Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .shadow(color: .black.opacity(0.06), radius: Elevation.resting, y: 1)
    }
}

/// Muted uppercase section label (settings groups, list sections).
struct SectionHeader: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.labelMedium.weight(.semibold))
            .foregroundStyle(Color.appOnSurfaceVariant)
            .padding(.horizontal, Spacing.xs)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Centered icon-badge empty state with optional call to action.
struct EmptyState: View {
    let systemImage: String
    let title: String
    let subtitle: String
    var actionLabel: String?
    var onAction: (() -> Void)?

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(Color.appPrimaryContainer)
                    .frame(width: 76, height: 76)
                Image(systemName: systemImage)
                    .font(.system(size: 32))
                    .foregroundStyle(Color.appPrimary)
            }
            Text(title)
                .font(.titleMedium)
                .foregroundStyle(Color.appOnBackground)
            Text(subtitle)
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
                .multilineTextAlignment(.center)
            if let actionLabel, let onAction {
                PrimaryButton(text: actionLabel, action: onAction)
                    .padding(.top, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.xxxl)
    }
}

/// Full-width centered loading spinner.
struct LoadingState: View {
    var body: some View {
        HStack {
            Spacer()
            ProgressView()
                .tint(Color.appPrimary)
            Spacer()
        }
        .padding(Spacing.xxxl)
    }
}
