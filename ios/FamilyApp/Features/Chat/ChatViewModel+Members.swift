// ChatViewModel — conversation/member management: create/find 1:1, add/remove member,
// rename, group image upload/remove, and delete.
import Foundation

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
        let otherRows = await (try? repo.fetchParticipants(userId: otherId)) ?? []
        let sharedIds = otherRows.map(\.conversationId).filter { myConvIds.contains($0) }
        guard !sharedIds.isEmpty else { return nil }
        let allRows = await (try? repo.fetchParticipants(conversationIds: sharedIds)) ?? []
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
                let conv = try await repo.insertConversation(
                    userFrom: userId,
                    name: name.trimmingCharacters(in: .whitespaces),
                    familyId: user.familyId
                )
                for participantId in ([userId] + memberIds).uniqued() {
                    try? await repo.insertParticipant(conversationId: conv.id, userId: participantId)
                }
            } catch {
                errorMessage = L("Failed to create conversation")
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
                    let conv = try await repo.insertConversation(
                        userFrom: userId, name: "", familyId: user.familyId
                    )
                    for participantId in allIds {
                        try await repo.insertParticipant(
                            conversationId: conv.id, userId: participantId
                        )
                    }
                    navigateToConversation = conv.id
                } else {
                    try await repo.insertParticipant(
                        conversationId: conversationId, userId: newUserId
                    )
                    let addedName = userProfiles[newUserId]?.name ?? "A member"
                    await repo.insertSystemMessage(
                        conversationId: conversationId,
                        userFrom: userId,
                        text: "\(addedName) was added to the group"
                    )
                    loadConversation(conversationId)
                }
            } catch {
                errorMessage = L("Failed to add member")
            }
            await loadConversations()
        }
    }

    func removeMember(conversationId: String, targetUserId: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            await repo.deleteParticipant(conversationId: conversationId, userId: targetUserId)
            if targetUserId != userId {
                loadConversation(conversationId)
            }
            await loadConversations()
        }
    }

    func renameConversation(id: String, name: String) {
        Task {
            let trimmed = name.trimmingCharacters(in: .whitespaces)
            await repo.renameConversation(id: id, name: trimmed)
            conversation?.name = trimmed
            await loadConversations()
        }
    }

    func uploadGroupImage(conversationId: String, data: Data) {
        Task {
            do {
                let compressed = ImageUtils.compressWithOrientation(data) ?? data
                let url = try await repo.uploadGroupImage(
                    conversationId: conversationId, data: compressed
                )
                try await repo.setConversationImage(id: conversationId, url: url)
                conversation?.imageUri = url
                await loadConversations()
            } catch {
                errorMessage = L("Failed to update image")
            }
        }
    }

    func removeImage(conversationId: String) {
        Task {
            await repo.removeGroupImage(conversationId: conversationId)
            await repo.clearConversationImage(id: conversationId)
            conversation?.imageUri = nil
            await loadConversations()
        }
    }

    func deleteConversation(_ conversationId: String) {
        Task {
            do {
                await repo.removeGroupImage(conversationId: conversationId)
                try await repo.deleteConversation(id: conversationId)
                conversations.removeAll { $0.conversation.id == conversationId }
                conversationDeleted = true
                await loadConversations()
            } catch {
                errorMessage = L("Failed to delete conversation")
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
