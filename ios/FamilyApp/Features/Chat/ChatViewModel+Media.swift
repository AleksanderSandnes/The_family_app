// ChatViewModel — sending & media. Extracted from ChatViewModel.swift to keep the main
// type's body under the length limit. Optimistic text send, reply state, and image/voice
// uploads. Behaviour is identical; these were plain methods on the class.
import Foundation
import Supabase

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

            var payload: [String: AnyJSON] = [
                "conversation_id": .string(conversationId),
                "user_from": .string(userId),
                "text": .string(text),
            ]
            if let pendingReplyTo { payload["reply_to_id"] = .string(pendingReplyTo.id) }
            do {
                try await client.from("messages").insert(payload).execute()
            } catch {
                errorMessage = "Failed to send message"
                messages.removeAll { $0.id == temp.id }
            }
            await loadMessages(conversationId)

            // Update the list preview for our own sent message.
            if let last = messages.last {
                conversations = conversations.map { preview in
                    guard preview.conversation.id == conversationId else { return preview }
                    var preview = preview
                    preview.lastMessage = last
                    preview.lastSenderName = "You"
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
                try await client.from("messages").insert([
                    "conversation_id": AnyJSON.string(conversationId),
                    "user_from": .string(userId),
                    "text": .string(""),
                    "message_type": .string("image"),
                    "media_url": .string(url),
                ]).execute()
                await loadMessages(conversationId)
            } catch {
                errorMessage = "Failed to send image"
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
                try await client.from("messages").insert([
                    "conversation_id": AnyJSON.string(conversationId),
                    "user_from": .string(userId),
                    "text": .string(""),
                    "message_type": .string("voice"),
                    "media_url": .string(url),
                ]).execute()
                await loadMessages(conversationId)
            } catch {
                errorMessage = "Failed to send voice message"
            }
        }
    }
}
