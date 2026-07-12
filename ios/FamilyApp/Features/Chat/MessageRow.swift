// A single chat bubble row. Purely presentational: every input arrives via `let`
// properties and the only state is local (swipe-to-reply offset, tap-to-reveal timestamp).
import NukeUI
import SwiftUI
import UIKit

struct MessageRow: View {
    let message: MessageModel
    let isMine: Bool
    let isGroup: Bool
    let sender: UserModel?
    let quoted: MessageModel?
    let quotedSenderName: String?
    let reactions: [String: [String]]
    let seen: Bool
    let isReactionTarget: Bool
    let onReact: (String) -> Void
    let onReply: () -> Void
    let onOpenImage: (String) -> Void
    let onLongPress: () -> Void

    @State private var dragOffset: CGFloat = 0
    @State private var showTimestamp = false

    var body: some View {
        if message.messageType == "system" {
            Text(message.text)
                .font(.labelMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
                .padding(.vertical, 4)
        } else {
            HStack(alignment: .bottom, spacing: Spacing.sm) {
                if isMine { Spacer(minLength: 48) }
                if !isMine, isGroup {
                    InitialAvatar(
                        name: sender?.name ?? "?",
                        color: Color(argb: sender?.avatarColor ?? 0),
                        size: 28,
                        avatarUrl: sender?.avatarUrl
                    )
                }
                VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
                    if !isMine, isGroup, let name = sender?.name {
                        Text(name)
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                    bubble
                        .anchorPreference(key: BubbleBoundsKey.self, value: .bounds) { anchor in
                            isReactionTarget ? [message.id: anchor] : [:]
                        }
                    if !reactions.isEmpty {
                        HStack(spacing: 4) {
                            ForEach(reactions.sorted(by: { $0.key < $1.key }), id: \.key) { emoji, users in
                                Text(users.count > 1 ? "\(emoji) \(users.count)" : emoji)
                                    .font(.system(size: 16))
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 3)
                                    .background(Color(light: .white, dark: Palette.inkSurfaceVariant), in: Capsule())
                                    .shadow(color: Palette.shadowInk.opacity(0.15), radius: 5, y: 2)
                                    .onTapGesture { onReact(emoji) }
                            }
                        }
                    }
                    if seen {
                        Text("Seen")
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                    if showTimestamp {
                        Text(exactMessageTimestamp(message.sentAt, locale: appLocale))
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                            .padding(.top, 1)
                    }
                }
                if !isMine { Spacer(minLength: 48) }
            }
            .offset(x: dragOffset)
            .gesture(
                // Swipe-to-reply (drag towards the center).
                DragGesture(minimumDistance: 24)
                    .onChanged { value in
                        let translation = value.translation.width
                        dragOffset = isMine
                            ? min(0, max(translation, -60))
                            : max(0, min(translation, 60))
                    }
                    .onEnded { _ in
                        if abs(dragOffset) > 40 {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            onReply()
                        }
                        withAnimation(.spring(duration: 0.25)) { dragOffset = 0 }
                    }
            )
            .onTapGesture {
                withAnimation(.easeOut(duration: 0.15)) { showTimestamp.toggle() }
            }
            .onLongPressGesture(minimumDuration: 0.3) {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                onLongPress()
            }
        }
    }

    private var bubble: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let quoted {
                HStack(spacing: 6) {
                    Rectangle()
                        .fill(isMine ? Color.white.opacity(0.6) : Color.appPrimary)
                        .frame(width: 3)
                    VStack(alignment: .leading, spacing: 1) {
                        if let quotedSenderName {
                            Text(quotedSenderName)
                                .font(.labelMedium.weight(.semibold))
                                .foregroundStyle(isMine ? .white.opacity(0.95) : Color.appPrimary)
                        }
                        Text(quotedPreviewText(quoted))
                            .font(.labelMedium)
                            .foregroundStyle(isMine ? .white.opacity(0.85) : Color.appOnSurfaceVariant)
                            .lineLimit(2)
                    }
                }
                .padding(6)
                .background((isMine ? Color.white : Color.appOnSurface).opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            switch message.messageType {
            case "image":
                if let mediaUrl = message.mediaUrl, let url = URL(string: mediaUrl) {
                    LazyImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            Color.appSurfaceVariant
                        }
                    }
                    .frame(maxWidth: 220, maxHeight: 260)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .onTapGesture { onOpenImage(mediaUrl) }
                }
            case "voice":
                if let mediaUrl = message.mediaUrl {
                    VoiceNoteView(url: mediaUrl, tint: isMine ? .white : Color.appPrimary)
                }
            default:
                Text(message.text)
                    .font(.bodyLarge)
                    .foregroundStyle(isMine ? .white : Color.appOnSurface)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        // Outgoing = brand-gradient identity surface; incoming = frosted glass (spec F).
        .background {
            if isMine {
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Gradients.brand)
            }
        }
        .glassEffect(
            isMine ? .identity : .regular,
            in: RoundedRectangle(cornerRadius: 18, style: .continuous)
        )
        .shadow(
            color: (isMine ? Palette.indigo600 : Palette.shadowInk).opacity(isMine ? 0.3 : 0.07),
            radius: isMine ? 10 : 6, x: 0, y: isMine ? 4 : 3
        )
    }
}
