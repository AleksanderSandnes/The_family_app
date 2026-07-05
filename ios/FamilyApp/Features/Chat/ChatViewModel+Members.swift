// ChatViewModel — conversation/member management. Extracted from ChatViewModel.swift to
// keep the main type's body under the length limit: create/find 1:1, add/remove member,
// rename, group image upload/remove, and delete. Behaviour is identical.
import Foundation
import Supabase

extension ChatViewModel {
    private func findExistingOneOnOne(userId: String, otherId: String) async -> String? {
        if let existing = conversations.first(where: { preview in
            guard let userTo = preview.conversation.userTo else { return false }
            let from = preview.conversation.userFrom
            return (from == userId && userTo == otherId) || (from == otherId && userTo == userId)
        }) {
            return existing.conversation.id
        }

        let myConvIds = Set(conversations.map(\.conversation.id))
        guard !myConvIds.isEmpty else { return nil }
        let otherRows: [ConversationParticipantModel] = await (try? client
            .from("conversation_participants")
            .select()
            .eq("user_id", value: otherId)
            .execute()
            .value) ?? []
        let sharedIds = otherRows.map(\.conversationId).filter { myConvIds.contains($0) }
        guard !sharedIds.isEmpty else { return nil }
        let allRows: [ConversationParticipantModel] = await (try? client
            .from("conversation_participants")
            .select()
            .in("conversation_id", values: sharedIds)
            .execute()
            .value) ?? []
        return Dictionary(grouping: allRows, by: \.conversationId)
            .first { $0.value.count == 2 }?
            .key
    }

    func createConversation(name: String, memberIds: [String]) {
        Task {
            guard let userId = repo.session.currentUserId,
                  let user = await repo.getUser(userId) else { return }

            if memberIds.count == 1,
               let existing = await findExistingOneOnOne(userId: userId, otherId: memberIds[0]) {
                navigateToConversation = existing
                return
            }

            do {
                var payload: [String: AnyJSON] = [
                    "user_from": .string(userId),
                    "name": .string(name.trimmingCharacters(in: .whitespaces)),
                ]
                if let familyId = user.familyId { payload["family_id"] = .string(familyId) }
                let conv: ConversationModel = try await client.from("conversations")
                    .insert(payload)
                    .select()
                    .single()
                    .execute()
                    .value

                for participantId in ([userId] + memberIds).uniqued() {
                    try? await client.from("conversation_participants").insert([
                        "conversation_id": AnyJSON.string(conv.id),
                        "user_id": .string(participantId),
                    ]).execute()
                }
            } catch {
                errorMessage = "Failed to create conversation"
            }
            await loadConversations()
        }
    }

    func addMember(conversationId: String, newUserId: String) {
        Task {
            guard let userId = repo.session.currentUserId,
                  let user = await repo.getUser(userId) else { return }
            let participants = currentParticipants

            do {
                if participants.count <= 2 {
                    // Promoting a 1:1 → create a fresh group with everyone.
                    let allIds = (participants.map(\.id) + [newUserId]).uniqued()
                    var payload: [String: AnyJSON] = [
                        "user_from": .string(userId),
                        "name": .string(""),
                    ]
                    if let familyId = user.familyId { payload["family_id"] = .string(familyId) }
                    let conv: ConversationModel = try await client.from("conversations")
                        .insert(payload)
                        .select()
                        .single()
                        .execute()
                        .value
                    for participantId in allIds {
                        try await client.from("conversation_participants").insert([
                            "conversation_id": AnyJSON.string(conv.id),
                            "user_id": .string(participantId),
                        ]).execute()
                    }
                    navigateToConversation = conv.id
                } else {
                    try await client.from("conversation_participants").insert([
                        "conversation_id": AnyJSON.string(conversationId),
                        "user_id": .string(newUserId),
                    ]).execute()
                    let addedName = userProfiles[newUserId]?.name ?? "A member"
                    try? await client.from("messages").insert([
                        "conversation_id": AnyJSON.string(conversationId),
                        "user_from": .string(userId),
                        "text": .string("\(addedName) was added to the group"),
                        "message_type": .string("system"),
                    ]).execute()
                    loadConversation(conversationId)
                }
            } catch {
                errorMessage = "Failed to add member"
            }
            await loadConversations()
        }
    }

    func removeMember(conversationId: String, targetUserId: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            try? await client.from("conversation_participants")
                .delete()
                .eq("conversation_id", value: conversationId)
                .eq("user_id", value: targetUserId)
                .execute()
            if targetUserId != userId {
                loadConversation(conversationId)
            }
            await loadConversations()
        }
    }

    func renameConversation(id: String, name: String) {
        Task {
            try? await client.from("conversations")
                .update(["name": AnyJSON.string(name.trimmingCharacters(in: .whitespaces))])
                .eq("id", value: id)
                .execute()
            conversation?.name = name.trimmingCharacters(in: .whitespaces)
            await loadConversations()
        }
    }

    func uploadGroupImage(conversationId: String, data: Data) {
        Task {
            do {
                let compressed = ImageUtils.compressWithOrientation(data) ?? data
                let bucket = client.storage.from("group-images")
                let path = "\(conversationId)/image.jpg"
                try await bucket.upload(path, data: compressed, options: FileOptions(upsert: true))
                let url = try bucket.getPublicURL(path: path).absoluteString
                    + "?t=\(Int(Date().timeIntervalSince1970 * 1000))"
                try await client.from("conversations")
                    .update(["image_uri": AnyJSON.string(url)])
                    .eq("id", value: conversationId)
                    .execute()
                conversation?.imageUri = url
                await loadConversations()
            } catch {
                errorMessage = "Failed to update image"
            }
        }
    }

    func removeImage(conversationId: String) {
        Task {
            _ = try? await client.storage.from("group-images")
                .remove(paths: ["\(conversationId)/image.jpg"])
            try? await client.from("conversations")
                .update(["image_uri": AnyJSON.null])
                .eq("id", value: conversationId)
                .execute()
            conversation?.imageUri = nil
            await loadConversations()
        }
    }

    func deleteConversation(_ conversationId: String) {
        Task {
            do {
                _ = try? await client.storage.from("group-images")
                    .remove(paths: ["\(conversationId)/image.jpg"])
                try await client.from("conversations")
                    .delete()
                    .eq("id", value: conversationId)
                    .execute()
                conversations.removeAll { $0.conversation.id == conversationId }
                conversationDeleted = true
                await loadConversations()
            } catch {
                errorMessage = "Failed to delete conversation"
            }
        }
    }
}

extension Array where Element: Hashable {
    fileprivate func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
