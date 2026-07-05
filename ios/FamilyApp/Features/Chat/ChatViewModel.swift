// Chat view model — the iOS twin of ChatViewModel.kt. Intentionally ONE class shared
// by the chat list and the open thread (see CLAUDE.md): detail-screen deletes must
// reflect in the list on pop-back.
import Foundation
import Observation
import Supabase

struct TypingSignal: Codable {
    let userId: String
    let typing: Bool
}

private let typingAutoclearSeconds: Double = 5
private let typingThrottleSeconds: Double = 2
private let userIdPreviewLength = 8

@Observable
@MainActor
final class ChatViewModel {
    // MARK: List state

    private(set) var conversations: [ConversationWithPreview] = []
    private(set) var isLoading = false
    private(set) var familyMembers: [UserModel] = []
    private(set) var userProfiles: [String: UserModel] = [:]

    var totalUnread: Int { conversations.reduce(0) { $0 + $1.unreadCount } }

    // MARK: Thread state

    private(set) var currentConversationId: String?
    private(set) var conversation: ConversationModel?
    private(set) var messages: [MessageModel] = []
    private(set) var replyTo: MessageModel?
    private(set) var currentParticipants: [UserModel] = []
    /// Latest read timestamp among OTHER participants — drives "Seen".
    private(set) var otherLastRead: String?
    /// Other users currently typing in the open conversation.
    private(set) var typingUsers: Set<String> = []
    /// messageId → (emoji → [userId])
    private(set) var reactions: [String: [String: [String]]] = [:]

    /// One-shot navigation target (existing 1:1 found / group promoted).
    var navigateToConversation: String?
    /// Set when the open conversation was deleted — the thread should pop.
    var conversationDeleted = false
    var errorMessage: String?

    var currentUserId: String? { repo.session.currentUserId }

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient { SupabaseClientProvider.client }

    private let messagesObserver = RealtimeObserver()
    private var reactionsChannel: RealtimeChannelV2?
    private var reactionTasks: [Task<Void, Never>] = []
    private let participantsObserver = RealtimeObserver()
    private var typingChannel: RealtimeChannelV2?
    private var typingTask: Task<Void, Never>?
    private var typingClearTasks: [String: Task<Void, Never>] = [:]
    private var lastTypingSent = Date.distantPast
    private var notifObservers: [String: RealtimeObserver] = [:]
    private var messagesTask: Task<Void, Never>?
    private var familyChangedTask: Task<Void, Never>?

    init() {
        Task { await loadConversations() }
        familyChangedTask = Task { [weak self] in
            guard let stream = await self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.loadConversations()
            }
        }
    }

    isolated deinit {
        familyChangedTask?.cancel()
        typingTask?.cancel()
        messagesTask?.cancel()
        reactionTasks.forEach { $0.cancel() }
    }

    func setCurrentConversation(_ id: String?) {
        currentConversationId = id
        // Lets the notification delegate suppress pushes for the open chat.
        ActiveChat.conversationId = id
    }

    // MARK: - Conversation list

    func refreshConversations() async {
        await loadConversations()
    }

    private func loadConversations() async {
        guard let userId = repo.session.currentUserId else {
            conversations = []
            return
        }
        isLoading = true
        defer { isLoading = false }

        if let user = await repo.getUser(userId), let familyId = user.familyId {
            let members = await repo.getFamilyMembers(familyId: familyId)
            familyMembers = members
            for member in members { userProfiles[member.id] = member }
        }

        // RLS scopes this to conversations the user participates in.
        let convs: [ConversationModel] = (try? await client.from("conversations")
            .select()
            .execute()
            .value) ?? []
        guard !convs.isEmpty else {
            conversations = []
            return
        }

        let participantsMap = await loadParticipantsMap(conversationIds: convs.map(\.id))
        var previews: [ConversationWithPreview] = []
        for conv in convs {
            let lastMessage = await repo.getLastMessage(conversationId: conv.id)
            let lastSenderName: String? = if let lastMessage {
                lastMessage.userFrom == userId
                    ? "You"
                    : (userProfiles[lastMessage.userFrom]?.name
                        ?? String(lastMessage.userFrom.prefix(userIdPreviewLength)))
            } else {
                nil
            }
            let existingUnread = conversations
                .first { $0.conversation.id == conv.id }?.unreadCount ?? 0
            previews.append(ConversationWithPreview(
                conversation: conv,
                lastMessage: lastMessage,
                lastSenderName: lastSenderName,
                unreadCount: existingUnread,
                participants: participantsMap[conv.id] ?? []
            ))
        }
        // Most recent activity first.
        conversations = previews.sorted {
            (parseInstantMs($0.lastMessage?.sentAt ?? "") ?? 0)
                > (parseInstantMs($1.lastMessage?.sentAt ?? "") ?? 0)
        }
        subscribeAllForNotifications(userId: userId)
    }

    private func loadParticipantsMap(conversationIds: [String]) async -> [String: [UserModel]] {
        let allRows: [ConversationParticipantModel] = (try? await client
            .from("conversation_participants")
            .select()
            .in("conversation_id", values: conversationIds)
            .execute()
            .value) ?? []

        let missingIds = Set(allRows.map(\.userId)).subtracting(userProfiles.keys)
        if !missingIds.isEmpty {
            let fresh: [UserModel] = (try? await client.from("users")
                .select()
                .in("id", values: Array(missingIds))
                .execute()
                .value) ?? []
            for user in fresh { userProfiles[user.id] = user }
        }

        return Dictionary(grouping: allRows, by: \.conversationId)
            .mapValues { rows in rows.compactMap { userProfiles[$0.userId] } }
    }

    /// Keeps unread counts + previews live for every conversation (one channel each).
    private func subscribeAllForNotifications(userId: String) {
        for preview in conversations {
            let convId = preview.conversation.id
            guard notifObservers[convId] == nil else { continue }
            let observer = RealtimeObserver()
            notifObservers[convId] = observer
            observer.start(
                table: "messages",
                scope: "notify-\(convId)",
                filter: "conversation_id=eq.\(convId)"
            ) { [weak self] in
                await self?.onConversationActivity(convId: convId, userId: userId)
            }
        }
    }

    private func onConversationActivity(convId: String, userId: String) async {
        guard let last = await repo.getLastMessage(conversationId: convId),
              last.userFrom != userId else { return }
        let senderName = userProfiles[last.userFrom]?.name ?? "Family member"
        conversations = conversations.map { preview in
            guard preview.conversation.id == convId else { return preview }
            var preview = preview
            preview.lastMessage = last
            preview.lastSenderName = senderName
            if currentConversationId != convId {
                preview.unreadCount += 1
            }
            return preview
        }
    }

    func markRead(_ conversationId: String) {
        Task {
            await repo.markConversationRead(conversationId: conversationId)
            conversations = conversations.map { preview in
                guard preview.conversation.id == conversationId else { return preview }
                var preview = preview
                preview.unreadCount = 0
                return preview
            }
        }
    }

    // MARK: - Open thread

    func loadConversation(_ conversationId: String) {
        Task {
            let rows: [ConversationModel] = (try? await client.from("conversations")
                .select()
                .eq("id", value: conversationId)
                .execute()
                .value) ?? []
            conversation = rows.first
            await loadMessages(conversationId)

            let participantRows: [ConversationParticipantModel] = (try? await client
                .from("conversation_participants")
                .select()
                .eq("conversation_id", value: conversationId)
                .execute()
                .value) ?? []
            let participantIds = participantRows.map(\.userId)

            let missing = Set(participantIds).subtracting(userProfiles.keys)
            if !missing.isEmpty {
                let fresh: [UserModel] = (try? await client.from("users")
                    .select()
                    .in("id", values: Array(missing))
                    .execute()
                    .value) ?? []
                for user in fresh { userProfiles[user.id] = user }
            }
            currentParticipants = participantIds.compactMap { userProfiles[$0] }

            let myId = repo.session.currentUserId
            otherLastRead = participantRows
                .filter { $0.userId != myId }
                .compactMap(\.lastReadAt)
                .max()

            if let myId, let user = await repo.getUser(myId),
               let familyId = user.familyId, familyMembers.isEmpty {
                let members = await repo.getFamilyMembers(familyId: familyId)
                familyMembers = members
                for member in members { userProfiles[member.id] = member }
            }

            subscribeToMessages(conversationId)
            subscribeToParticipants(conversationId)
            await subscribeToTyping(conversationId)
            await loadReactions(conversationId)
            await subscribeToReactions(conversationId)
        }
    }

    private func loadMessages(_ conversationId: String) async {
        if let fetched: [MessageModel] = try? await client.from("messages")
            .select()
            .eq("conversation_id", value: conversationId)
            .order("sent_at", ascending: true)
            .execute()
            .value {
            messages = fetched
        }
    }

    private func subscribeToMessages(_ conversationId: String) {
        messagesObserver.start(
            table: "messages",
            scope: "messages-\(conversationId)",
            filter: "conversation_id=eq.\(conversationId)"
        ) { [weak self] in
            await self?.loadMessages(conversationId)
        }
    }

    /// Live-updates "Seen" when another participant's last_read_at changes.
    private func subscribeToParticipants(_ conversationId: String) {
        participantsObserver.start(
            table: "conversation_participants",
            scope: "participants-\(conversationId)",
            filter: "conversation_id=eq.\(conversationId)"
        ) { [weak self] in
            guard let self else { return }
            let rows: [ConversationParticipantModel] = (try? await self.client
                .from("conversation_participants")
                .select()
                .eq("conversation_id", value: conversationId)
                .execute()
                .value) ?? []
            let myId = self.repo.session.currentUserId
            self.otherLastRead = rows
                .filter { $0.userId != myId }
                .compactMap(\.lastReadAt)
                .max()
        }
    }

    // MARK: - Typing (Realtime broadcast)

    private func subscribeToTyping(_ conversationId: String) async {
        typingTask?.cancel()
        if let typingChannel {
            await client.removeChannel(typingChannel)
        }
        typingUsers = []
        let channel = client.channel("typing-\(conversationId)")
        typingChannel = channel
        let stream = channel.broadcastStream(event: "typing")
        typingTask = Task { [weak self] in
            await channel.subscribe()
            for await message in stream {
                guard let self else { break }
                guard
                    let payload = message["payload"]?.objectValue,
                    let userId = payload["userId"]?.stringValue,
                    let typing = payload["typing"]?.boolValue,
                    userId != self.repo.session.currentUserId
                else { continue }
                self.applyTypingSignal(userId: userId, typing: typing)
            }
        }
    }

    private func applyTypingSignal(userId: String, typing: Bool) {
        typingClearTasks.removeValue(forKey: userId)?.cancel()
        if typing {
            typingUsers.insert(userId)
            // Auto-clear in case the "stopped typing" signal is missed.
            typingClearTasks[userId] = Task { [weak self] in
                try? await Task.sleep(for: .seconds(typingAutoclearSeconds))
                guard !Task.isCancelled else { return }
                self?.typingUsers.remove(userId)
            }
        } else {
            typingUsers.remove(userId)
        }
    }

    /// Broadcasts the current user's typing state (throttled to once every 2 s).
    func setTyping(_ typing: Bool) {
        guard let channel = typingChannel, let myId = repo.session.currentUserId else { return }
        let now = Date()
        if typing, now.timeIntervalSince(lastTypingSent) < typingThrottleSeconds { return }
        if typing { lastTypingSent = now }
        Task {
            try? await channel.broadcast(
                event: "typing",
                message: TypingSignal(userId: myId, typing: typing)
            )
        }
    }

    // MARK: - Reactions

    private func loadReactions(_ conversationId: String) async {
        guard let rows: [MessageReactionModel] = try? await client.from("message_reactions")
            .select()
            .eq("conversation_id", value: conversationId)
            .execute()
            .value
        else { return }
        reactions = Dictionary(grouping: rows, by: \.messageId).mapValues { messageRows in
            Dictionary(grouping: messageRows, by: \.emoji).mapValues { $0.map(\.userId) }
        }
    }

    private func subscribeToReactions(_ conversationId: String) async {
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
            filter: "conversation_id=eq.\(conversationId)"
        )
        reactionTasks.append(Task { [weak self] in
            await channel.subscribe()
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

    // MARK: - Sending

    func setReplyTo(_ message: MessageModel) { replyTo = message }

    func clearReplyTo() { replyTo = nil }

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

    // MARK: - Conversation management

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
        let otherRows: [ConversationParticipantModel] = (try? await client
            .from("conversation_participants")
            .select()
            .eq("user_id", value: otherId)
            .execute()
            .value) ?? []
        let sharedIds = otherRows.map(\.conversationId).filter { myConvIds.contains($0) }
        guard !sharedIds.isEmpty else { return nil }
        let allRows: [ConversationParticipantModel] = (try? await client
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

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
