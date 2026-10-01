// Report/block moderation for ChatViewModel (Play UGC policy / App Store 1.2).
// Backend contract: supabase/security/moderation.sql.
import Foundation

extension ChatViewModel {
    /// `messages` without those from blocked users — what the thread shows.
    var visibleMessages: [MessageModel] {
        ChatModeration.visibleMessages(messages, blocked: blockedUserIds)
    }

    func loadBlocks() async {
        if let ids = try? await repo.fetchBlockedUserIds() {
            blockedUserIds = ids
        }
    }

    func reportMessage(_ msg: MessageModel, reason: ReportReason, details: String = "") async {
        do {
            try await repo.reportMessage(messageId: msg.id, reason: reason, details: details)
            noticeMessage = L("Report sent. Thank you.")
        } catch {
            errorMessage = L("Couldn’t save")
        }
    }

    /// Hides the user's messages immediately; rolls back if the block cannot be saved.
    func blockUser(_ userId: String, name: String) async {
        blockedUserIds.insert(userId)
        do {
            try await repo.blockUser(userId: userId)
            noticeMessage = String(format: L("%@ is blocked"), name)
        } catch {
            blockedUserIds.remove(userId)
            errorMessage = L("Couldn’t save")
        }
    }

    func unblockUser(_ userId: String, name: String) async {
        blockedUserIds.remove(userId)
        do {
            try await repo.unblockUser(userId: userId)
            noticeMessage = String(format: L("%@ is unblocked"), name)
        } catch {
            blockedUserIds.insert(userId)
            errorMessage = L("Couldn’t save")
        }
    }
}
