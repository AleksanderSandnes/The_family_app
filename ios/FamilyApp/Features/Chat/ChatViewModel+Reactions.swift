// ChatViewModel — reaction toggling. Extracted from ChatViewModel.swift to keep the main
// type's body under the length limit. Optimistic add/remove of the current user's emoji
// reaction, then the write-through to the repository. Behaviour is identical.
import Foundation
import Supabase

extension ChatViewModel {
    func toggleReaction(messageId: String, conversationId: String, emoji: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            let myCurrentEmoji = reactions[messageId]?.first { $0.value.contains(userId) }?.key

            // Optimistic update so the UI reflects the change immediately.
            var messageReactions = reactions[messageId] ?? [:]
            if let myCurrentEmoji {
                let remaining = (messageReactions[myCurrentEmoji] ?? []).filter { $0 != userId }
                if remaining.isEmpty {
                    messageReactions[myCurrentEmoji] = nil
                } else {
                    messageReactions[myCurrentEmoji] = remaining
                }
            }
            if myCurrentEmoji != emoji {
                messageReactions[emoji, default: []].append(userId)
            }
            if messageReactions.isEmpty {
                reactions[messageId] = nil
            } else {
                reactions[messageId] = messageReactions
            }

            if myCurrentEmoji == emoji {
                try? await repo.removeReaction(messageId: messageId)
            } else {
                try? await repo.addReaction(
                    messageId: messageId, conversationId: conversationId, emoji: emoji
                )
            }
        }
    }
}
