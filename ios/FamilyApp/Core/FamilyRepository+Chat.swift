// FamilyRepository — chat helpers shared across screens. Extracted from
// FamilyRepository.swift to keep the main type's body under the length limit. Last-message
// lookup, read receipts, and message/reaction writes. Behaviour is identical.
import Foundation
import Supabase

extension FamilyRepository {
    func getLastMessage(conversationId: String) async -> MessageModel? {
        let rows: [MessageModel] = await (try? client.from("messages")
            .select()
            .eq("conversation_id", value: conversationId)
            .order("sent_at", ascending: false)
            .limit(1)
            .execute()
            .value) ?? []
        return rows.first
    }

    func markConversationRead(conversationId: String) async {
        guard let userId = session.currentUserId else { return }
        _ = try? await client.from("conversation_participants")
            .update(["last_read_at": AnyJSON.string(isoNow())])
            .eq("conversation_id", value: conversationId)
            .eq("user_id", value: userId)
            .execute()
    }

    func sendMessage(conversationId: String, text: String) async throws {
        guard let userId = session.currentUserId else { throw RepositoryError.notAuthenticated }
        try await client.from("messages")
            .insert([
                "conversation_id": AnyJSON.string(conversationId),
                "user_from": .string(userId),
                "text": .string(text),
                "message_type": .string("text"),
            ])
            .execute()
    }

    func addReaction(messageId: String, conversationId: String, emoji: String) async throws {
        guard let userId = session.currentUserId else { throw RepositoryError.notAuthenticated }
        try await client.from("message_reactions")
            .upsert([
                "message_id": AnyJSON.string(messageId),
                "conversation_id": .string(conversationId),
                "user_id": .string(userId),
                "emoji": .string(emoji),
            ])
            .execute()
    }

    func removeReaction(messageId: String) async throws {
        guard let userId = session.currentUserId else { throw RepositoryError.notAuthenticated }
        try await client.from("message_reactions")
            .delete()
            .eq("message_id", value: messageId)
            .eq("user_id", value: userId)
            .execute()
    }
}
