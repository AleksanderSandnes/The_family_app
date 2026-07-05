// Support views extracted from ConversationScreen.swift: the typing-indicator row, the
// add/select-member sheet, and the full-screen pinch-zoom image viewer with its model.
import NukeUI
import SwiftUI

struct TypingIndicatorRow: View {
    @State private var phase = 0

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Color.appOnSurfaceVariant)
                    .frame(width: 7, height: 7)
                    .opacity(phase == index ? 1 : 0.35)
            }
            Text("typing…")
                .font(.labelMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 4)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(350))
                phase = (phase + 1) % 3
            }
        }
    }
}

// MARK: - Shared sheets / viewer

struct MemberListSheet: View {
    let title: String
    let members: [UserModel]
    var destructive = false
    let onPick: (UserModel) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Spacing.lg) {
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
            ScrollView {
                VStack(spacing: Spacing.sm) {
                    ForEach(members) { member in
                        Button {
                            onPick(member)
                        } label: {
                            HStack(spacing: Spacing.md) {
                                InitialAvatar(user: member, size: 40)
                                Text(member.name)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(destructive ? Color.appError : Color.appOnSurface)
                                Spacer()
                            }
                            .padding(.horizontal, Spacing.lg)
                            .padding(.vertical, 12)
                            .rowSurface(ghost: false, cornerRadius: Radius.row)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .glassSheet(detents: [.medium])
    }
}

/// Full-screen image viewer with pinch zoom — the iOS twin of ImageViewerDialog.
struct ImageViewer: View {
    let url: String
    let onClose: () -> Void

    @State private var scale: CGFloat = 1

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if let imageURL = URL(string: url) {
                LazyImage(url: imageURL) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFit()
                    } else {
                        ProgressView().tint(.white)
                    }
                }
                .scaleEffect(scale)
                .gesture(
                    MagnifyGesture()
                        .onChanged { value in scale = max(1, value.magnification) }
                        .onEnded { _ in withAnimation { scale = 1 } }
                )
            }
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .padding(Spacing.xl)
            .accessibilityLabel("Close")
        }
    }
}

struct ViewerImage: Identifiable {
    let url: String
    var id: String {
        url
    }
}
