// Report/block UI for the open thread (Play UGC policy / App Store 1.2): the action bar
// under the reaction picker for others' messages, the 1:1 block menu item, and the
// report/block/notice dialogs. Logic lives in ChatViewModel+Moderation.
import SwiftUI

/// Pending report/block confirmations for the open thread.
struct ModerationState {
    var messageToReport: MessageModel?
    var userToBlock: UserModel?
}

/// Report / Block buttons under the reaction picker for another member's message.
struct ModerationBar: View {
    let message: MessageModel?
    let model: ChatViewModel
    /// The reaction picker's target; cleared to dismiss it.
    @Binding var picker: String?
    @Binding var state: ModerationState

    var body: some View {
        if let message, ChatModeration.canModerate(message, myId: model.currentUserId) {
            let sender = model.userProfiles[message.userFrom]
            HStack(spacing: Spacing.md) {
                Button(role: .destructive) {
                    dismissPicker()
                    state.messageToReport = message
                } label: {
                    Label(L("Report"), systemImage: "exclamationmark.bubble")
                        .font(.labelLarge)
                        .foregroundStyle(Palette.destructive)
                }
                if let sender {
                    Button(role: .destructive) {
                        dismissPicker()
                        state.userToBlock = sender
                    } label: {
                        Label(L("Block"), systemImage: "hand.raised")
                            .font(.labelLarge)
                            .foregroundStyle(Palette.destructive)
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
            .glassChrome(cornerRadius: Radius.menu)
        }
    }

    private func dismissPicker() {
        withAnimation(.easeOut(duration: 0.15)) { picker = nil }
    }
}

/// Options-menu item for a 1:1 chat (hidden for groups): block after confirmation, or unblock.
struct BlockMenuButton: View {
    let model: ChatViewModel
    @Binding var state: ModerationState

    var body: some View {
        let participants = model.currentParticipants
        if participants.count == 2, let user = participants.first(where: { $0.id != model.currentUserId }) {
            if model.blockedUserIds.contains(user.id) {
                Button {
                    Task { await model.unblockUser(user.id, name: user.name) }
                } label: {
                    Label(String(format: L("Unblock %@"), user.name), systemImage: "person.crop.circle.badge.checkmark")
                }
            } else {
                Button(role: .destructive) { state.userToBlock = user } label: {
                    Label(String(format: L("Block %@"), user.name), systemImage: "hand.raised")
                }
            }
        }
    }
}

private struct ModerationDialogs: ViewModifier {
    let viewModel: ChatViewModel
    @Binding var state: ModerationState

    private static let reportExplanation: String.LocalizationValue = """
    Tell us what is wrong. The message and your reason are sent for review; \
    the sender is not told who reported it.
    """
    private static let blockExplanation: String.LocalizationValue = """
    You will no longer see their messages or get notifications from them. \
    You can unblock them from this chat’s menu.
    """

    func body(content: Content) -> some View {
        content
            .confirmationDialog(
                L("Report message"),
                isPresented: presence($state.messageToReport),
                titleVisibility: .visible
            ) {
                ForEach(ReportReason.allCases) { reason in
                    Button(L(reason.label)) {
                        if let msg = state.messageToReport {
                            Task { await viewModel.reportMessage(msg, reason: reason) }
                        }
                        state.messageToReport = nil
                    }
                }
                Button(L("Cancel"), role: .cancel) { state.messageToReport = nil }
            } message: {
                Text(L(Self.reportExplanation))
            }
            .alert(
                String(format: L("Block %@?"), state.userToBlock?.name ?? ""),
                isPresented: presence($state.userToBlock)
            ) {
                Button(L("Block"), role: .destructive) {
                    if let user = state.userToBlock {
                        Task { await viewModel.blockUser(user.id, name: user.name) }
                    }
                    state.userToBlock = nil
                }
                Button(L("Cancel"), role: .cancel) { state.userToBlock = nil }
            } message: {
                Text(L(Self.blockExplanation))
            }
            .alert(viewModel.noticeMessage ?? viewModel.errorMessage ?? "", isPresented: Binding(
                get: { viewModel.noticeMessage != nil || viewModel.errorMessage != nil },
                set: {
                    if !$0 {
                        viewModel.noticeMessage = nil
                        viewModel.errorMessage = nil
                    }
                }
            )) {
                Button(L("OK"), role: .cancel) {}
            }
    }

    private func presence(_ item: Binding<(some Any)?>) -> Binding<Bool> {
        Binding(
            get: { item.wrappedValue != nil },
            set: {
                if !$0 {
                    item.wrappedValue = nil
                }
            }
        )
    }
}

extension View {
    func moderationDialogs(viewModel: ChatViewModel, state: Binding<ModerationState>) -> some View {
        modifier(ModerationDialogs(viewModel: viewModel, state: state))
    }
}
