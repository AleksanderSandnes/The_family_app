// ChatViewModel — sending & media: optimistic text send, reply state, and image/voice
// uploads.
import Foundation

extension ChatViewModel {
    func setReplyTo(_ message: MessageModel) {
        replyTo = message
    }

    func clearReplyTo() {
        replyTo = nil
    }

    func send(conversationId: String, text: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            let pendingReplyTo = replyTo
            replyTo = nil

            var temp = MessageModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.conversationId = conversationId
            temp.userFrom = userId
            temp.text = text
            temp.replyToId = pendingReplyTo?.id
            temp.sentAt = isoNow()
            messages.append(temp)

            do {
                try await repo.insertTextMessage(
                    conversationId: conversationId,
                    userFrom: userId,
                    text: text,
                    replyToId: pendingReplyTo?.id
                )
            } catch {
                errorMessage = L("Failed to send message")
                messages.removeAll { $0.id == temp.id }
            }
            await loadMessages(conversationId)

            // Update the list preview for our own sent message.
            if let last = messages.last {
                conversations = conversations.map { preview in
                    guard preview.conversation.id == conversationId else { return preview }
                    var preview = preview
                    preview.lastMessage = last
                    preview.lastSenderName = L("You")
                    return preview
                }
            }
        }
    }

    func sendImage(conversationId: String, data: Data, filename: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            do {
                let url = try await StorageService.uploadChatMedia(
                    conversationId: conversationId, data: data, filename: filename
                )
                try await repo.insertImageMessage(
                    conversationId: conversationId, userFrom: userId, mediaUrl: url
                )
                await loadMessages(conversationId)
            } catch {
                errorMessage = L("Failed to send image")
            }
        }
    }

    func sendVoice(conversationId: String, data: Data, filename: String) {
        Task {
            guard let userId = repo.session.currentUserId else { return }
            do {
                let url = try await StorageService.uploadChatMedia(
                    conversationId: conversationId, data: data, filename: filename
                )
                try await repo.insertVoiceMessage(
                    conversationId: conversationId, userFrom: userId, mediaUrl: url
                )
                await loadMessages(conversationId)
            } catch {
                errorMessage = L("Failed to send voice message")
            }
        }
    }
}
