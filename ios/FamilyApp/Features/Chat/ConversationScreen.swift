// Open thread — the iOS twin of ConversationScreen in ChatScreens.kt: gradient
// outgoing bubbles, quotes, reactions, swipe-to-reply, read receipts, typing
// indicator, time separators, media (image/voice/camera), group management menu.
import NukeUI
import PhotosUI
import SwiftUI

struct ConversationScreen: View {
    let conversationId: String
    let viewModel: ChatViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var draft = ""
    @State private var showRename = false
    @State private var renameText = ""
    @State private var showAddMember = false
    @State private var showRemoveMember = false
    @State private var showMembers = false
    @State private var showDeleteConfirm = false
    @State private var groupPhotoItem: PhotosPickerItem?
    @State private var messagePhotoItem: PhotosPickerItem?
    @State private var showMessageCamera = false
    @State private var viewerImage: ViewerImage?
    @State private var recorder = VoiceRecorder()
    @State private var showPhotoPicker = false
    @State private var showGroupPhotoPicker = false
    @State private var reactionTargetId: String?

    private var myId: String? {
        viewModel.currentUserId
    }

    private var isGroup: Bool {
        viewModel.currentParticipants.count > 2
    }

    private var title: String {
        guard let conversation = viewModel.conversation else { return L("Chat") }
        return conversationDisplayName(
            conversation: conversation,
            participants: viewModel.currentParticipants,
            currentUserId: myId ?? "",
            locale: appLocale
        )
    }

    private var presence: String? {
        guard !isGroup else { return nil }
        return presenceLabel(
            viewModel.currentParticipants.first { $0.id != myId }?.lastActiveAt,
            locale: appLocale
        )
    }

    private var availableToAdd: [UserModel] {
        let currentIds = Set(viewModel.currentParticipants.map(\.id))
        return viewModel.familyMembers.filter { !currentIds.contains($0.id) }
    }

    private func senderName(for userId: String) -> String {
        if userId == myId { return L("You") }
        return viewModel.userProfiles[userId]?.name
            ?? viewModel.currentParticipants.first { $0.id == userId }?.name
            ?? L("Unknown")
    }

    var body: some View {
        VStack(spacing: 0) {
            messagesList
            if !viewModel.typingUsers.isEmpty {
                TypingIndicatorRow()
            }
            inputBar
        }
        .ambientBackground()
        .featureTopBar(title)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(title)
                        .font(.titleMedium)
                        .foregroundStyle(Color.appOnSurface)
                    if let presence {
                        Text(presence)
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) { optionsMenu }
        }
        .task(id: conversationId) {
            viewModel.loadConversation(conversationId)
            viewModel.setCurrentConversation(conversationId)
            viewModel.markRead(conversationId)
        }
        .onDisappear { viewModel.setCurrentConversation(nil) }
        .onChange(of: viewModel.conversationDeleted) { _, deleted in
            if deleted {
                viewModel.conversationDeleted = false
                dismiss()
            }
        }
        .inputDialog(isPresented: $showRename, title: L("Rename group"), label: L("Name"), text: $renameText) {
            viewModel.renameConversation(id: conversationId, name: renameText)
        }
        .sheet(isPresented: $showAddMember) {
            MemberListSheet(title: L("Add member"), members: availableToAdd) { member in
                viewModel.addMember(conversationId: conversationId, newUserId: member.id)
                showAddMember = false
            }
        }
        .sheet(isPresented: $showRemoveMember) {
            MemberListSheet(
                title: L("Remove member"),
                members: viewModel.currentParticipants.filter { $0.id != myId },
                destructive: true
            ) { member in
                viewModel.removeMember(conversationId: conversationId, targetUserId: member.id)
                showRemoveMember = false
            }
        }
        .sheet(isPresented: $showMembers) {
            MemberListSheet(title: L("Members"), members: viewModel.currentParticipants) { _ in }
        }
        .alert("Delete conversation?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) { viewModel.deleteConversation(conversationId) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the conversation for everyone.")
        }
        .fullScreenCover(isPresented: $showMessageCamera) {
            CameraPicker { image in
                if let data = ImageUtils.compressWithOrientation(image) {
                    viewModel.sendImage(
                        conversationId: conversationId,
                        data: data,
                        filename: "cam_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
                    )
                }
            }
            .ignoresSafeArea()
        }
        .fullScreenCover(item: $viewerImage) { item in
            ImageViewer(url: item.url) { viewerImage = nil }
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $messagePhotoItem, matching: .images)
        .photosPicker(isPresented: $showGroupPhotoPicker, selection: $groupPhotoItem, matching: .images)
        .overlayPreferenceValue(BubbleBoundsKey.self) { anchors in
            reactionOverlay(anchors: anchors)
        }
        .onChange(of: groupPhotoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    viewModel.uploadGroupImage(conversationId: conversationId, data: data)
                }
                groupPhotoItem = nil
            }
        }
        .onChange(of: messagePhotoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let compressed = ImageUtils.compressWithOrientation(data) {
                    viewModel.sendImage(
                        conversationId: conversationId,
                        data: compressed,
                        filename: "img_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
                    )
                }
                messagePhotoItem = nil
            }
        }
    }

    private var optionsMenu: some View {
        Menu {
            if isGroup {
                Button {
                    renameText = viewModel.conversation?.name ?? ""
                    showRename = true
                } label: {
                    Label(L("Rename group"), systemImage: "pencil")
                }
            }
            Button { showGroupPhotoPicker = true } label: {
                Label(L("Change image"), systemImage: "photo")
            }
            if viewModel.conversation?.imageUri != nil {
                Button(role: .destructive) {
                    viewModel.removeImage(conversationId: conversationId)
                } label: {
                    Label(L("Remove image"), systemImage: "photo.badge.minus")
                }
            }
            if !availableToAdd.isEmpty {
                Button {
                    showAddMember = true
                } label: {
                    Label(
                        isGroup ? L("Add member") : L("Add member (creates group)"),
                        systemImage: "person.badge.plus"
                    )
                }
            }
            if isGroup {
                Button { showMembers = true } label: {
                    Label(L("Members"), systemImage: "person.2")
                }
            }
            Divider()
            if isGroup {
                Button(role: .destructive) { showRemoveMember = true } label: {
                    Label(L("Remove member"), systemImage: "person.badge.minus")
                }
            }
            Button(role: .destructive) { showDeleteConfirm = true } label: {
                Label(L("Delete conversation"), systemImage: "trash")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .accessibilityLabel("Options")
        }
    }
}

// MARK: - Message list, reaction overlay, and input bar

// In an extension (same file, so `private` state stays reachable) to keep the primary
// ConversationScreen body under the type-body length limit.

extension ConversationScreen {
    private var messagesList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(Array(viewModel.messages.enumerated()), id: \.element.id) { index, message in
                        let previous = index > 0 ? viewModel.messages[index - 1] : nil
                        let showTimeLabel = previous.map {
                            gapExceedsTenMinutes(earlierIso: $0.sentAt, laterIso: message.sentAt)
                        } ?? true
                        if showTimeLabel {
                            Text(messageTimeLabel(message.sentAt, locale: appLocale))
                                .font(.labelMedium)
                                .foregroundStyle(Color.appOnSurfaceVariant)
                                .padding(.vertical, Spacing.sm)
                        }
                        MessageRow(
                            message: message,
                            isMine: message.userFrom == myId,
                            isGroup: isGroup,
                            sender: viewModel.userProfiles[message.userFrom],
                            quoted: message.replyToId.flatMap { id in
                                viewModel.messages.first { $0.id == id }
                            },
                            quotedSenderName: message.replyToId.flatMap { id in
                                viewModel.messages.first { $0.id == id }
                            }.map { senderName(for: $0.userFrom) },
                            reactions: viewModel.reactions[message.id] ?? [:],
                            seen: message.userFrom == myId
                                && message.id == viewModel.messages.last(where: { $0.userFrom == myId })?.id
                                && messageSeen(otherLastRead: viewModel.otherLastRead, sentAt: message.sentAt),
                            isReactionTarget: reactionTargetId == message.id,
                            onReact: { emoji in
                                viewModel.toggleReaction(
                                    messageId: message.id,
                                    conversationId: conversationId,
                                    emoji: emoji
                                )
                            },
                            onReply: { viewModel.setReplyTo(message) },
                            onOpenImage: { viewerImage = ViewerImage(url: $0) },
                            onLongPress: {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    reactionTargetId = message.id
                                }
                            }
                        )
                        .id(message.id)
                    }
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
            }
            .defaultScrollAnchor(.bottom)
            .onChange(of: viewModel.messages.count) {
                if let lastId = viewModel.messages.last?.id {
                    proxy.scrollTo(lastId, anchor: .bottom)
                }
            }
            .onChange(of: conversationId) {
                if let lastId = viewModel.messages.last?.id {
                    proxy.scrollTo(lastId, anchor: .bottom)
                }
            }
        }
    }

    /// Full-screen reaction picker rendered above a tap-to-dismiss scrim.
    @ViewBuilder
    private func reactionOverlay(anchors: [String: Anchor<CGRect>]) -> some View {
        if let id = reactionTargetId, let anchor = anchors[id] {
            GeometryReader { geo in
                let rect = geo[anchor]
                let barY = max(60, rect.minY - 30)
                ZStack(alignment: .topLeading) {
                    Color.black.opacity(0.001)
                        .ignoresSafeArea()
                        .contentShape(Rectangle())
                        .onTapGesture {
                            withAnimation(.easeOut(duration: 0.15)) { reactionTargetId = nil }
                        }
                    ReactionBar { emoji in
                        viewModel.toggleReaction(messageId: id, conversationId: conversationId, emoji: emoji)
                        withAnimation(.easeOut(duration: 0.15)) { reactionTargetId = nil }
                    }
                    .fixedSize()
                    .position(x: min(max(rect.midX, 180), geo.size.width - 180), y: barY)
                    .transition(.scale(scale: 0.7).combined(with: .opacity))
                }
            }
        }
    }

    // MARK: - Input bar

    private var inputBar: some View {
        VStack(spacing: 0) {
            if let quoted = viewModel.replyTo {
                HStack(spacing: 10) {
                    Rectangle().fill(Color.appPrimary).frame(width: 3, height: 36)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("\(L("Replying to")) \(senderName(for: quoted.userFrom))")
                            .font(.labelMedium.weight(.semibold))
                            .foregroundStyle(Color.appPrimary)
                        Text(quotedPreviewText(quoted))
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                            .lineLimit(1)
                    }
                    Spacer()
                    Button {
                        viewModel.clearReplyTo()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
                .background(Color.appSurfaceVariant)
            }

            HStack(spacing: Spacing.sm) {
                if recorder.isRecording {
                    recordingBar
                } else {
                    Menu {
                        Button {
                            showMessageCamera = true
                        } label: {
                            Label(L("Camera"), systemImage: "camera")
                        }
                        Button {
                            showPhotoPicker = true
                        } label: {
                            Label(L("Photo library"), systemImage: "photo")
                        }
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 26))
                            .foregroundStyle(Color.appPrimary)
                    }
                    .accessibilityLabel("Attach")

                    TextField("Message…", text: $draft, axis: .vertical)
                        .font(.system(size: 16))
                        .tint(Color.appPrimary)
                        .lineLimit(1...4)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, 11)
                        .glassCard(cornerRadius: 22)
                        .onChange(of: draft) { _, text in
                            viewModel.setTyping(!text.isEmpty)
                        }

                    if draft.trimmingCharacters(in: .whitespaces).isEmpty {
                        Button {
                            Task { _ = await recorder.start() }
                        } label: {
                            Image(systemName: "mic.fill")
                                .font(.system(size: 20))
                                .foregroundStyle(Color.appPrimary)
                        }
                        .accessibilityLabel("Record voice message")
                    } else {
                        Button(action: sendText) {
                            Image(systemName: "paperplane.fill")
                                .font(.system(size: 20))
                                .foregroundStyle(Color.appPrimary)
                        }
                        .accessibilityLabel("Send")
                    }
                }
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(.ultraThinMaterial)
        }
    }

    private var recordingBar: some View {
        HStack(spacing: Spacing.md) {
            Button { _ = recorder.stop(send: false) } label: {
                Image(systemName: "trash")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.appError)
            }
            .accessibilityLabel("Cancel recording")

            HStack(spacing: Spacing.sm) {
                RecordingPulse()
                Text(recordingLabel(seconds: recorder.seconds))
                    .font(.system(size: 15, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Color.appOnSurface)
                LiveWaveform(levels: recorder.levels, tint: Color.appPrimary)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Spacing.lg)
            .frame(height: 52)
            .frame(maxWidth: .infinity)
            .background(Color.appSurfaceVariant, in: Capsule())

            Button {
                if let result = recorder.stop(send: true) {
                    viewModel.sendVoice(
                        conversationId: conversationId,
                        data: result.data,
                        filename: result.filename
                    )
                }
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 34))
                    .foregroundStyle(Color.appPrimary)
            }
            .accessibilityLabel("Send voice message")
        }
    }

    private func sendText() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        viewModel.send(conversationId: conversationId, text: text)
        draft = ""
        viewModel.setTyping(false)
    }
}

// MARK: - Reaction anchor

/// Reports the bounds of the bubble currently targeted for a reaction, so the
/// screen-level overlay can position the reaction bar above it. Internal (not private)
/// so MessageRow, extracted into its own file, can set the preference.
struct BubbleBoundsKey: PreferenceKey {
    static let defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { _, new in new }
    }
}
