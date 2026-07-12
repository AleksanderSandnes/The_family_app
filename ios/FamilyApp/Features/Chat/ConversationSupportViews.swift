// Chat support views: the typing-indicator row, the add/select-member sheet, and the
// full-screen pinch-zoom image viewer with its model.
import NukeUI
import Photos
import SwiftUI
import UIKit

private let quickReactions = ["❤️", "😂", "👍", "😮", "😢", "🙏"]

/// Horizontal quick-reaction bar shown above a message bubble on long-press.
struct ReactionBar: View {
    let onPick: (String) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(quickReactions, id: \.self) { emoji in
                Button {
                    onPick(emoji)
                } label: {
                    Text(emoji).font(.system(size: 26))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .glassEffect(.regular, in: Capsule())
        .shadow(color: Palette.shadowInk.opacity(0.2), radius: 12, y: 6)
    }
}

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
        VStack(spacing: Spacing.sm) {
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
            .padding(.bottom, Spacing.sm)
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
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
    }
}

/// Full-screen image viewer with pinch zoom. Image is vertically centred; a download
/// button saves the full-size image to Photos.
struct ImageViewer: View {
    let url: String
    let onClose: () -> Void

    @State private var scale: CGFloat = 1
    @State private var saveState: SaveState = .idle

    private enum SaveState { case idle, saving, done, failed }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let imageURL = URL(string: url) {
                LazyImage(url: imageURL) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFit()
                    } else {
                        ProgressView().tint(.white)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .scaleEffect(scale)
                .gesture(
                    MagnifyGesture()
                        .onChanged { value in scale = max(1, value.magnification) }
                        .onEnded { _ in withAnimation { scale = 1 } }
                )
            }
        }
        .overlay(alignment: .topTrailing) {
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .padding(Spacing.xl)
            .accessibilityLabel("Close")
        }
        .overlay(alignment: .bottom) {
            Button(action: downloadImage) {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: downloadIcon)
                    Text(downloadTitle)
                }
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, Spacing.xl)
                .frame(height: 46)
                .glassEffect(.regular, in: Capsule())
            }
            .disabled(saveState == .saving || saveState == .done)
            .padding(.bottom, Spacing.xxl)
            .accessibilityLabel("Save image")
        }
    }

    private var downloadIcon: String {
        switch saveState {
        case .done: "checkmark"
        case .failed: "exclamationmark.triangle"
        default: "square.and.arrow.down"
        }
    }

    private var downloadTitle: String {
        switch saveState {
        case .saving: L("Saving…")
        case .done: L("Saved")
        case .failed: L("Couldn’t save")
        default: L("Save image")
        }
    }

    private func downloadImage() {
        guard let imageURL = URL(string: url), saveState != .saving else { return }
        saveState = .saving
        Task {
            let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            guard status == .authorized || status == .limited else { saveState = .failed
                return
            }
            do {
                let (data, _) = try await URLSession.shared.data(from: imageURL)
                guard let image = UIImage(data: data) else { saveState = .failed
                    return
                }
                try await PHPhotoLibrary.shared().performChanges {
                    PHAssetChangeRequest.creationRequestForAsset(from: image)
                }
                saveState = .done
            } catch {
                saveState = .failed
            }
        }
    }
}

struct ViewerImage: Identifiable {
    let url: String
    var id: String {
        url
    }
}

// MARK: - Localized helpers (shared with ConversationScreen)

/// Reply/quote preview label: shows the message text, or a localized kind for media.
@MainActor
func quotedPreviewText(_ message: MessageModel) -> String {
    switch message.messageType {
    case "text": message.text
    case "image": L("Image")
    case "voice": L("Voice")
    default: message.messageType.capitalized
    }
}

/// Recording timer label, e.g. "Recording 0:07".
@MainActor
func recordingLabel(seconds: Int) -> String {
    let time = String(format: "%d:%02d", seconds / 60, seconds % 60)
    return L("Recording \(time)", locale: appLocale)
}
