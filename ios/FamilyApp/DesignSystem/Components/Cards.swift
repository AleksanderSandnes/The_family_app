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
            .glassCard(cornerRadius: Radius.row)
    }
}

/// Muted uppercase section label (settings groups, list sections). 12/700, tracked.
struct SectionHeader: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.sectionLabel)
            .tracking(0.6)
            .foregroundStyle(Color.appCaption)
            .padding(.horizontal, 6) // indents to ~22px with the 16px screen edge
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

/// Inline error banner — mirrors ErrorBanner in Components.kt. Renders nothing when nil.
struct ErrorBanner: View {
    let message: String?

    var body: some View {
        if let message {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.appError)
                Text(message)
                    .font(.bodyMedium)
                    .foregroundStyle(Color.appError)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(Spacing.md)
            .background(Color.appError.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
        }
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
