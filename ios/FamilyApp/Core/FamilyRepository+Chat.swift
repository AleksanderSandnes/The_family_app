// FamilyRepository — chat helpers shared across screens: last-message lookup, read
// receipts, and message/reaction writes.
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

    /// Updates the sender's own message text and stamps edited_at (RLS enforces ownership).
    func editMessage(messageId: String, newText: String) async throws {
        try await client.from("messages")
            .update([
                "text": AnyJSON.string(newText),
                "edited_at": .string(ISO8601DateFormatter().string(from: Date())),
            ])
            .eq("id", value: messageId)
            .execute()
    }

    /// Deletes the sender's own message (RLS enforces ownership).
    func deleteMessage(messageId: String) async throws {
        try await client.from("messages")
            .delete()
            .eq("id", value: messageId)
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

    // MARK: - Conversation / message reads (moved out of ChatViewModel)

    /// All conversations the current user participates in. RLS scopes the result, so there
    /// is no explicit filter here.
    func fetchConversations() async throws -> [ConversationModel] {
        try await client.from("conversations")
            .select()
            .execute()
            .value
    }

    /// A single conversation by id (open-thread load picks `.first`).
    func fetchConversation(id: String) async throws -> [ConversationModel] {
        try await client.from("conversations")
            .select()
            .eq("id", value: id)
            .execute()
            .value
    }

    /// All messages in a conversation, oldest-first (open-thread order).
    func fetchMessages(conversationId: String) async throws -> [MessageModel] {
        try await client.from("messages")
            .select()
            .eq("conversation_id", value: conversationId)
            .order("sent_at", ascending: true)
            .execute()
            .value
    }

    /// My participant rows for the given conversations — each carries my last_read_at.
    func fetchMyParticipants(
        userId: String, conversationIds: [String]
    ) async throws -> [ConversationParticipantModel] {
        guard !conversationIds.isEmpty else { return [] }
        return try await client.from("conversation_participants")
            .select()
            .eq("user_id", value: userId)
            .in("conversation_id", values: conversationIds)
            .execute()
            .value
    }

    /// Unread count for one conversation: messages from someone OTHER than me, newer than
    /// `after` (my last_read_at). Swallows errors to 0.
    func countUnreadMessages(conversationId: String, userId: String, after: String) async -> Int {
        let count = try? await client.from("messages")
            .select("id", head: true, count: .exact)
            .eq("conversation_id", value: conversationId)
            .neq("user_from", value: userId)
            .gt("sent_at", value: after)
            .execute()
            .count
        return count ?? 0
    }

    /// Participant rows across many conversations (list participant map).
    func fetchParticipants(conversationIds: [String]) async throws -> [ConversationParticipantModel] {
        guard !conversationIds.isEmpty else { return [] }
        return try await client.from("conversation_participants")
            .select()
            .in("conversation_id", values: conversationIds)
            .execute()
            .value
    }

    /// Participant rows for one conversation.
    func fetchParticipants(conversationId: String) async throws -> [ConversationParticipantModel] {
        try await client.from("conversation_participants")
            .select()
            .eq("conversation_id", value: conversationId)
            .execute()
            .value
    }

    /// Every conversation the given user participates in (used to find a shared 1:1).
    func fetchParticipants(userId: String) async throws -> [ConversationParticipantModel] {
        try await client.from("conversation_participants")
            .select()
            .eq("user_id", value: userId)
            .execute()
            .value
    }

    /// Fetches user rows by id — fills the participant/sender profile cache.
    func fetchUsers(ids: [String]) async throws -> [UserModel] {
        guard !ids.isEmpty else { return [] }
        return try await client.from("users")
            .select()
            .in("id", values: ids)
            .execute()
            .value
    }

    /// All reactions in a conversation (grouped client-side by message then emoji).
    func fetchReactions(conversationId: String) async throws -> [MessageReactionModel] {
        try await client.from("message_reactions")
            .select()
            .eq("conversation_id", value: conversationId)
            .execute()
            .value
    }

    // MARK: - Conversation / participant writes

    /// Inserts a conversation and returns the created row (payload: user_from, name, optional
    /// family_id). Throws so a create/promote failure surfaces to the caller.
    func insertConversation(
        userFrom: String, name: String, familyId: String?
    ) async throws -> ConversationModel {
        var payload: [String: AnyJSON] = [
            "user_from": .string(userFrom),
            "name": .string(name),
        ]
        if let familyId { payload["family_id"] = .string(familyId) }
        return try await client.from("conversations")
            .insert(payload)
            .select()
            .single()
            .execute()
            .value
    }

    /// Adds a participant. Throws so callers can handle failure.
    func insertParticipant(conversationId: String, userId: String) async throws {
        try await client.from("conversation_participants")
            .insert([
                "conversation_id": AnyJSON.string(conversationId),
                "user_id": .string(userId),
            ])
            .execute()
    }

    /// Removes a participant (best-effort).
    func deleteParticipant(conversationId: String, userId: String) async {
        _ = try? await client.from("conversation_participants")
            .delete()
            .eq("conversation_id", value: conversationId)
            .eq("user_id", value: userId)
            .execute()
    }

    /// Renames a conversation (best-effort).
    func renameConversation(id: String, name: String) async {
        _ = try? await client.from("conversations")
            .update(["name": AnyJSON.string(name)])
            .eq("id", value: id)
            .execute()
    }

    /// Sets the group image URL. Throws — the upload flow surfaces failures.
    func setConversationImage(id: String, url: String) async throws {
        try await client.from("conversations")
            .update(["image_uri": AnyJSON.string(url)])
            .eq("id", value: id)
            .execute()
    }

    /// Clears the group image URL (best-effort).
    func clearConversationImage(id: String) async {
        _ = try? await client.from("conversations")
            .update(["image_uri": AnyJSON.null])
            .eq("id", value: id)
            .execute()
    }

    /// Deletes a conversation. Throws so the caller can show an error and skip local removal.
    func deleteConversation(id: String) async throws {
        try await client.from("conversations")
            .delete()
            .eq("id", value: id)
            .execute()
    }

    // MARK: - Message writes

    /// Inserts a text message (optional reply). No message_type key, so the DB default
    /// ('text') applies.
    func insertTextMessage(
        conversationId: String, userFrom: String, text: String, replyToId: String?
    ) async throws {
        var payload: [String: AnyJSON] = [
            "conversation_id": .string(conversationId),
            "user_from": .string(userFrom),
            "text": .string(text),
        ]
        if let replyToId { payload["reply_to_id"] = .string(replyToId) }
        try await client.from("messages").insert(payload).execute()
    }

    /// Inserts a system message (member add/remove notices). Best-effort (try?).
    func insertSystemMessage(conversationId: String, userFrom: String, text: String) async {
        _ = try? await client.from("messages").insert([
            "conversation_id": AnyJSON.string(conversationId),
            "user_from": .string(userFrom),
            "text": .string(text),
            "message_type": .string("system"),
        ]).execute()
    }

    /// Inserts an image message. Throws (the send flow surfaces failures).
    func insertImageMessage(conversationId: String, userFrom: String, mediaUrl: String) async throws {
        try await client.from("messages").insert([
            "conversation_id": AnyJSON.string(conversationId),
            "user_from": .string(userFrom),
            "text": .string(""),
            "message_type": .string("image"),
            "media_url": .string(mediaUrl),
        ]).execute()
    }

    /// Inserts a voice message. Throws (the send flow surfaces failures).
    func insertVoiceMessage(conversationId: String, userFrom: String, mediaUrl: String) async throws {
        try await client.from("messages").insert([
            "conversation_id": AnyJSON.string(conversationId),
            "user_from": .string(userFrom),
            "text": .string(""),
            "message_type": .string("voice"),
            "media_url": .string(mediaUrl),
        ]).execute()
    }

    // MARK: - Group image storage

    /// Uploads a group image (already compressed by the caller) to
    /// group-images/{conversationId}/image.jpg and returns a cache-busted public URL. This
    /// path convention differs from StorageService's auth-uid rule, so it lives here.
    func uploadGroupImage(conversationId: String, data: Data) async throws -> String {
        let bucket = client.storage.from("group-images")
        let path = "\(conversationId)/image.jpg"
        try await bucket.upload(path, data: data, options: FileOptions(upsert: true))
        return try bucket.getPublicURL(path: path).absoluteString
            + "?t=\(Int(Date().timeIntervalSince1970 * 1000))"
    }

    /// Removes a conversation's group image file (best-effort).
    func removeGroupImage(conversationId: String) async {
        _ = try? await client.storage.from("group-images")
            .remove(paths: ["\(conversationId)/image.jpg"])
    }
}
