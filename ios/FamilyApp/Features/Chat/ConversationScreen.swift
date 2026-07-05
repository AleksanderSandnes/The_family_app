// Open thread — the iOS twin of ConversationScreen in ChatScreens.kt: gradient
// outgoing bubbles, quotes, reactions, swipe-to-reply, read receipts, typing
// indicator, time separators, media (image/voice/camera), group management menu.
import NukeUI
import PhotosUI
import SwiftUI

private let quickReactions = ["❤️", "😂", "👍", "😮", "😢", "🙏"]

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

    private var myId: String? { viewModel.currentUserId }
    private var isGroup: Bool { viewModel.currentParticipants.count > 2 }

    private var title: String {
        guard let conversation = viewModel.conversation else { return "Chat" }
        return conversationDisplayName(
            conversation: conversation,
            participants: viewModel.currentParticipants,
            currentUserId: myId ?? ""
        )
    }

    private var presence: String? {
        guard !isGroup else { return nil }
        return presenceLabel(
            viewModel.currentParticipants.first { $0.id != myId }?.lastActiveAt
        )
    }

    private var availableToAdd: [UserModel] {
        let currentIds = Set(viewModel.currentParticipants.map(\.id))
        return viewModel.familyMembers.filter { !currentIds.contains($0.id) }
    }

    var body: some View {
        VStack(spacing: 0) {
            messagesList
            if !viewModel.typingUsers.isEmpty {
                TypingIndicatorRow()
            }
            inputBar
        }
        .background(Color.appBackground)
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
        .inputDialog(isPresented: $showRename, title: "Rename group", label: "Name", text: $renameText) {
            viewModel.renameConversation(id: conversationId, name: renameText)
        }
        .sheet(isPresented: $showAddMember) {
            MemberListSheet(title: "Add member", members: availableToAdd) { member in
                viewModel.addMember(conversationId: conversationId, newUserId: member.id)
                showAddMember = false
            }
        }
        .sheet(isPresented: $showRemoveMember) {
            MemberListSheet(
                title: "Remove member",
                members: viewModel.currentParticipants.filter { $0.id != myId },
                destructive: true
            ) { member in
                viewModel.removeMember(conversationId: conversationId, targetUserId: member.id)
                showRemoveMember = false
            }
        }
        .sheet(isPresented: $showMembers) {
            MemberListSheet(title: "Members", members: viewModel.currentParticipants) { _ in }
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
                Button("Rename") {
                    renameText = viewModel.conversation?.name ?? ""
                    showRename = true
                }
            }
            PhotosPicker(selection: $groupPhotoItem, matching: .images) {
                Text("Change image")
            }
            if viewModel.conversation?.imageUri != nil {
                Button("Remove image", role: .destructive) {
                    viewModel.removeImage(conversationId: conversationId)
                }
            }
            if !availableToAdd.isEmpty {
                Button(isGroup ? "Add member" : "Add member (creates group)") {
                    showAddMember = true
                }
            }
            if isGroup {
                Button("Remove member", role: .destructive) { showRemoveMember = true }
                Button("Members") { showMembers = true }
            }
            Divider()
            Button("Delete conversation", role: .destructive) { showDeleteConfirm = true }
        } label: {
            Image(systemName: "ellipsis.circle")
                .accessibilityLabel("Options")
        }
    }

    // MARK: - Messages

    private var messagesList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(Array(viewModel.messages.enumerated()), id: \.element.id) { index, message in
                        let previous = index > 0 ? viewModel.messages[index - 1] : nil
                        if previous == nil || gapExceedsTenMinutes(
                            earlierIso: previous!.sentAt, laterIso: message.sentAt
                        ) {
                            Text(messageTimeLabel(message.sentAt))
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
                            reactions: viewModel.reactions[message.id] ?? [:],
                            seen: message.userFrom == myId
                                && message.id == viewModel.messages.last(where: { $0.userFrom == myId })?.id
                                && messageSeen(otherLastRead: viewModel.otherLastRead, sentAt: message.sentAt),
                            onReact: { emoji in
                                viewModel.toggleReaction(
                                    messageId: message.id,
                                    conversationId: conversationId,
                                    emoji: emoji
                                )
                            },
                            onReply: { viewModel.setReplyTo(message) },
                            onOpenImage: { viewerImage = ViewerImage(url: $0) }
                        )
                        .id(message.id)
                    }
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
            }
            .onChange(of: viewModel.messages.count) {
                if let lastId = viewModel.messages.last?.id {
                    proxy.scrollTo(lastId, anchor: .bottom)
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
                        Text("Replying")
                            .font(.labelMedium.weight(.semibold))
                            .foregroundStyle(Color.appPrimary)
                        Text(quoted.messageType == "text" ? quoted.text : quoted.messageType.capitalized)
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
                            Label("Camera", systemImage: "camera")
                        }
                        PhotosPicker(selection: $messagePhotoItem, matching: .images) {
                            Label("Photo library", systemImage: "photo")
                        }
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 26))
                            .foregroundStyle(Color.appPrimary)
                    }
                    .accessibilityLabel("Attach")

                    TextField("Message…", text: $draft, axis: .vertical)
                        .font(.bodyLarge)
                        .lineLimit(1...4)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, 10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .strokeBorder(Color.appOnSurface.opacity(0.3), lineWidth: 1)
                        )
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
            .background(Color.appSurface)
        }
    }

    private var recordingBar: some View {
        HStack(spacing: Spacing.md) {
            Circle().fill(Color.appError).frame(width: 10, height: 10)
            Text(String(format: "Recording %d:%02d", recorder.seconds / 60, recorder.seconds % 60))
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurface)
            Spacer()
            Button("Cancel") { _ = recorder.stop(send: false) }
                .foregroundStyle(Color.appOnSurfaceVariant)
            Button {
                if let result = recorder.stop(send: true) {
                    viewModel.sendVoice(
                        conversationId: conversationId,
                        data: result.data,
                        filename: result.filename
                    )
                }
            } label: {
                Image(systemName: "paperplane.fill")
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

// MARK: - Message row

private struct MessageRow: View {
    let message: MessageModel
    let isMine: Bool
    let isGroup: Bool
    let sender: UserModel?
    let quoted: MessageModel?
    let reactions: [String: [String]]
    let seen: Bool
    let onReact: (String) -> Void
    let onReply: () -> Void
    let onOpenImage: (String) -> Void

    @State private var dragOffset: CGFloat = 0

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
                    if !reactions.isEmpty {
                        HStack(spacing: 4) {
                            ForEach(reactions.sorted(by: { $0.key < $1.key }), id: \.key) { emoji, users in
                                Text(users.count > 1 ? "\(emoji)\(users.count)" : emoji)
                                    .font(.labelMedium)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.appSurfaceVariant)
                                    .clipShape(Capsule())
                                    .onTapGesture { onReact(emoji) }
                            }
                        }
                    }
                    if seen {
                        Text("Seen")
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
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
            .contextMenu {
                ForEach(quickReactions, id: \.self) { emoji in
                    Button(emoji) { onReact(emoji) }
                }
                Divider()
                Button {
                    onReply()
                } label: {
                    Label("Reply", systemImage: "arrowshape.turn.up.left")
                }
            }
        }
    }

    @ViewBuilder private var bubble: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let quoted {
                HStack(spacing: 6) {
                    Rectangle()
                        .fill(isMine ? Color.white.opacity(0.6) : Color.appPrimary)
                        .frame(width: 3)
                    Text(quoted.messageType == "text" ? quoted.text : quoted.messageType.capitalized)
                        .font(.labelMedium)
                        .foregroundStyle(isMine ? .white.opacity(0.85) : Color.appOnSurfaceVariant)
                        .lineLimit(2)
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
        .padding(.vertical, 10)
        .background {
            if isMine {
                // BrandGradient outgoing bubbles — identity surface (design rule).
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Gradients.brand)
            } else {
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.appSurface)
            }
        }
    }
}

private struct TypingIndicatorRow: View {
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

private struct MemberListSheet: View {
    let title: String
    let members: [UserModel]
    var destructive = false
    let onPick: (UserModel) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title)
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            ScrollView {
                VStack(spacing: Spacing.xs) {
                    ForEach(members) { member in
                        Button {
                            onPick(member)
                        } label: {
                            HStack(spacing: Spacing.md) {
                                InitialAvatar(user: member, size: 40)
                                Text(member.name)
                                    .font(.bodyLarge)
                                    .foregroundStyle(destructive ? Color.appError : Color.appOnSurface)
                                Spacer()
                            }
                            .padding(.vertical, Spacing.sm)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.screenEdge)
        .presentationDetents([.medium])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
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
    var id: String { url }
}
