// ChatViewModel — reactions. Extracted from ChatViewModel.swift to keep the main type's
// body under the length limit. Optimistic add/remove of the current user's emoji reaction
// (write-through to the repository), plus the reactions load + the raw realtime presence
// channel. Behaviour is identical. The presence channel is opened only from
// loadConversation, so a test-built VM never touches a live client.
import Foundation
import Supabase

extension ChatViewModel {
    func loadReactions(_ conversationId: String) async {
        guard let rows = try? await repo.fetchReactions(conversationId: conversationId)
        else { return }
        reactions = Dictionary(grouping: rows, by: \.messageId).mapValues { messageRows in
            Dictionary(grouping: messageRows, by: \.emoji).mapValues { $0.map(\.userId) }
        }
    }

    func subscribeToReactions(_ conversationId: String) async {
        reactionTasks.forEach { $0.cancel() }
        reactionTasks = []
        if let reactionsChannel {
            await client.removeChannel(reactionsChannel)
        }
        let channel = client.channel("reactions-\(conversationId)")
        reactionsChannel = channel
        let changes = channel.postgresChange(
            AnyAction.self,
            schema: "public",
            table: "message_reactions",
            filter: .eq("conversation_id", value: conversationId)
        )
        reactionTasks.append(Task { [weak self] in
            do {
                try await channel.subscribeWithError()
            } catch {
                return
            }
            for await _ in changes {
                await self?.loadReactions(conversationId)
            }
        })
    }

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
