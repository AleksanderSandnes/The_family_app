// Conversation list — the iOS twin of ChatScreen/ConversationRow in ChatScreens.kt:
// unread badges, relative times, media previews, new-conversation sheet.
import SwiftUI

struct ChatScreen: View {
    let viewModel: ChatViewModel
    let onOpen: (String) -> Void

    @State private var showMemberPicker = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if viewModel.isLoading, viewModel.conversations.isEmpty {
                    LoadingState().frame(maxHeight: .infinity, alignment: .top)
                } else if viewModel.conversations.isEmpty {
                    EmptyState(
                        systemImage: "bubble.left.and.bubble.right.fill",
                        title: "No conversations yet",
                        subtitle: "No conversations yet. Start chatting with your family!"
                    )
                } else {
                    List {
                        ForEach(viewModel.conversations) { preview in
                            ConversationRow(
                                preview: preview,
                                currentUserId: viewModel.currentUserId ?? ""
                            ) { onOpen(preview.conversation.id) }
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable { await viewModel.refreshConversations() }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .ambientBackground()
        .navigationTitle("Chats")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showMemberPicker = true
                } label: {
                    Image(systemName: "square.and.pencil")
                        .accessibilityLabel("New conversation")
                }
            }
        }
        .onChange(of: viewModel.navigateToConversation) { _, newId in
            guard let newId else { return }
            viewModel.navigateToConversation = nil
            onOpen(newId)
        }
        .sheet(isPresented: $showMemberPicker) {
            NewConversationSheet(
                familyMembers: viewModel.familyMembers,
                myId: viewModel.currentUserId
            ) { name, memberIds in
                viewModel.createConversation(name: name, memberIds: memberIds)
                showMemberPicker = false
            }
        }
    }
}

private struct ConversationRow: View {
    let preview: ConversationWithPreview
    let currentUserId: String
    let onTap: () -> Void

    var body: some View {
        let conv = preview.conversation
        let isUnread = preview.unreadCount > 0
        let isOneOnOne = preview.participants.count == 2 || conv.userTo != nil
        let other = preview.participants.first { $0.id != currentUserId }
        let displayName = conversationDisplayName(
            conversation: conv, participants: preview.participants, currentUserId: currentUserId
        )
        let avatarUrl = conv.imageUri ?? (isOneOnOne ? other?.avatarUrl : nil)
        let avatarColor = Color(argb: (isOneOnOne ? other?.avatarColor : nil).flatMap {
            $0 != 0 ? $0 : nil
        } ?? Int(Int32(bitPattern: 0xFF6366F1)))

        Button(action: onTap) {
            HStack(spacing: Spacing.md) {
                InitialAvatar(name: displayName, color: avatarColor, size: 56, avatarUrl: avatarUrl)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(displayName)
                            .font(.system(size: 15.5, weight: isUnread ? .bold : .medium))
                            .foregroundStyle(Color.appOnSurface)
                            .lineLimit(1)
                        Spacer()
                        if let last = preview.lastMessage {
                            Text(relativeTime(last.sentAt))
                                .font(.system(size: 12, weight: isUnread ? .semibold : .regular))
                                .foregroundStyle(isUnread ? Color.appPrimary : Color.appCaption)
                        }
                    }
                    HStack {
                        Text(conversationPreviewText(
                            lastMessage: preview.lastMessage,
                            lastSenderName: preview.lastSenderName
                        ))
                        .font(.system(size: 13, weight: isUnread ? .medium : .regular))
                        .foregroundStyle(isUnread ? Color.appOnSurface : Color.appCaption)
                        .lineLimit(1)
                        Spacer()
                        if isUnread {
                            Text(preview.unreadCount > 99 ? "99+" : "\(preview.unreadCount)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(minWidth: 20)
                                .padding(.horizontal, 6)
                                .frame(height: 20)
                                .background(Color.appPrimary, in: Capsule())
                                .accessibilityLabel("\(preview.unreadCount) unread messages")
                        }
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .glassCard(cornerRadius: Radius.row)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - New conversation

struct NewConversationSheet: View {
    let familyMembers: [UserModel]
    let myId: String?
    let onCreate: (String, [String]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var selected: Set<String> = []

    private var candidates: [UserModel] {
        familyMembers.filter { $0.id != myId }
    }

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(
                title: "New conversation",
                confirmTitle: selected.count > 1 ? "Create group" : "Start chat",
                confirmEnabled: !selected.isEmpty,
                onCancel: { dismiss() },
                onConfirm: {
                    onCreate(name, Array(selected))
                    dismiss()
                }
            )
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.vertical, Spacing.lg)

            if candidates.isEmpty {
                EmptyState(
                    systemImage: "person.2",
                    title: "No family members",
                    subtitle: "Invite your family first to start chatting."
                )
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: Spacing.sm) {
                        if selected.count > 1 {
                            GlassField(placeholder: "Group name (optional)", text: $name)
                                .padding(.bottom, Spacing.xs)
                        }
                        ForEach(candidates) { member in
                            MemberSelectRow(member: member, selected: selected.contains(member.id)) {
                                if selected.contains(member.id) {
                                    selected.remove(member.id)
                                } else {
                                    selected.insert(member.id)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.screenEdge)
                    .padding(.bottom, Spacing.lg)
                }
            }
        }
        .glassSheet(detents: [.large, .medium])
    }
}

struct MemberSelectRow: View {
    let member: UserModel
    let selected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: Spacing.md) {
                InitialAvatar(user: member, size: 40)
                Text(member.name)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.appOnSurface)
                Spacer()
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(selected ? Color.appPrimary : Color.appCaption)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, 12)
            .rowSurface(ghost: false, cornerRadius: Radius.row)
        }
        .buttonStyle(.plain)
    }
}
