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

    // `internal(set)` (plain `var`) so the `+Media`/`+Members` extensions in sibling
    // files can mutate it; still no external writers.
    var conversations: [ConversationWithPreview] = []
    private(set) var isLoading = false
    private(set) var familyMembers: [UserModel] = []
    private(set) var userProfiles: [String: UserModel] = [:]

    var totalUnread: Int {
        conversations.reduce(0) { $0 + $1.unreadCount }
    }

    // MARK: Thread state

    private(set) var currentConversationId: String?
    // `conversation`, `messages` and `replyTo` are mutated by the `+Media`/`+Members`
    // extensions in sibling files, so their setters must be internal (plain `var`).
    var conversation: ConversationModel?
    var messages: [MessageModel] = []
    var replyTo: MessageModel?
    private(set) var currentParticipants: [UserModel] = []
    /// Latest read timestamp among OTHER participants — drives "Seen".
    private(set) var otherLastRead: String?
    /// Other users currently typing in the open conversation.
    private(set) var typingUsers: Set<String> = []
    /// messageId → (emoji → [userId])
    /// `internal(set)` (plain `var`) so `toggleReaction` in the `+Reactions` sibling file
    /// can mutate it; still no external writers.
    var reactions: [String: [String: [String]]] = [:]

    /// One-shot navigation target (existing 1:1 found / group promoted).
    var navigateToConversation: String?
    /// Set when the open conversation was deleted — the thread should pop.
    var conversationDeleted = false
    var errorMessage: String?

    var currentUserId: String? {
        repo.session.currentUserId
    }

    // Internal (not private) so the `+Media`/`+Members` extensions in sibling files
    // can reach them.
    let repo = FamilyRepository.shared
    var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    private let messagesObserver = RealtimeObserver()
    private var reactionsChannel: RealtimeChannelV2?
    private var reactionTasks: [Task<Void, Never>] = []
    private let participantsObserver = RealtimeObserver()
    private var typingChannel: RealtimeChannelV2?
    private var typingTask: Task<Void, Never>?
    private var typingClearTasks: [String: Task<Void, Never>] = [:]
    private var lastTypingSent = Date.distantPast
    private var notifObservers: [String: RealtimeObserver] = [:]
    // List-level realtime so new conversations appear without a relaunch. (Family-member
    // changes are picked up by refreshFamilyMembers() when the new-conversation sheet opens —
    // the users table isn't in the realtime publication, and its heartbeat updates would spam.)
    private let conversationsListObserver = RealtimeObserver()
    private var listSubsStarted = false
    private var messagesTask: Task<Void, Never>?
    private var familyChangedTask: Task<Void, Never>?

    init() {
        Task { await loadConversations() }
        familyChangedTask = Task { [weak self] in
            guard let stream = self?.repo.familyChanged() else { return }
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

    /// Reloads just the family member list — called when the new-conversation sheet opens
    /// so a member who joined on another device is immediately selectable.
    func refreshFamilyMembers() async {
        guard let userId = repo.session.currentUserId,
              let user = await repo.getUser(userId), let familyId = user.familyId else { return }
        let members = await repo.getFamilyMembers(familyId: familyId)
        familyMembers = members
        for member in members {
            userProfiles[member.id] = member
        }
    }

    func loadConversations() async {
        guard let userId = repo.session.currentUserId else {
            conversations = []
            return
        }
        isLoading = true
        defer { isLoading = false }

        var familyId: String?
        if let user = await repo.getUser(userId) {
            familyId = user.familyId
            if let familyId {
                let members = await repo.getFamilyMembers(familyId: familyId)
                familyMembers = members
                for member in members {
                    userProfiles[member.id] = member
                }
            }
        }
        startListSubscriptions(userId: userId, familyId: familyId)

        // RLS scopes this to conversations the user participates in.
        let convs: [ConversationModel] = await (try? client.from("conversations")
            .select()
            .execute()
            .value) ?? []
        guard !convs.isEmpty else {
            conversations = []
            return
        }

        let participantsMap = await loadParticipantsMap(conversationIds: convs.map(\.id))
        // Authoritative unread counts from last_read_at — survives relaunch (unlike the
        // in-session increments), so the badge is correct on a cold start.
        let unreadMap = await unreadCounts(conversationIds: convs.map(\.id), userId: userId)
        var previews: [ConversationWithPreview] = []
        for conv in convs {
            let lastMessage = await repo.getLastMessage(conversationId: conv.id)
            // "You" is localized at render time (conversationPreviewText) so it follows a
            // live language switch; store only the other person's name here.
            let lastSenderName: String? = if let lastMessage, lastMessage.userFrom != userId {
                userProfiles[lastMessage.userFrom]?.name
                    ?? String(lastMessage.userFrom.prefix(userIdPreviewLength))
            } else {
                nil
            }
            previews.append(ConversationWithPreview(
                conversation: conv,
                lastMessage: lastMessage,
                lastSenderName: lastSenderName,
                unreadCount: currentConversationId == conv.id ? 0 : (unreadMap[conv.id] ?? 0),
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

    /// Starts the list-level realtime subscriptions once: a new conversation in my family
    /// (or a new family member) triggers a full reload so I see it without force-closing.
    private func startListSubscriptions(userId: String, familyId: String?) {
        guard !listSubsStarted, let familyId else { return }
        listSubsStarted = true
        conversationsListObserver.start(
            table: "conversations",
            scope: "list-convs-\(familyId)",
            filter: .eq("family_id", value: familyId)
        ) { [weak self] in
            await self?.loadConversations()
        }
    }

    /// Per-conversation unread = messages from others newer than my last_read_at.
    private func unreadCounts(conversationIds: [String], userId: String) async -> [String: Int] {
        // My participant rows carry my last_read_at per conversation.
        let myRows: [ConversationParticipantModel] = await (try? client
            .from("conversation_participants")
            .select()
            .eq("user_id", value: userId)
            .in("conversation_id", values: conversationIds)
            .execute()
            .value) ?? []
        let lastReadByConv = Dictionary(
            myRows.map { ($0.conversationId, $0.lastReadAt ?? "1970-01-01T00:00:00Z") },
            uniquingKeysWith: { first, _ in first }
        )
        var result: [String: Int] = [:]
        for convId in conversationIds {
            let lastRead = lastReadByConv[convId] ?? "1970-01-01T00:00:00Z"
            let count = try? await client.from("messages")
                .select("id", head: true, count: .exact)
                .eq("conversation_id", value: convId)
                .neq("user_from", value: userId)
                .gt("sent_at", value: lastRead)
                .execute()
                .count
            result[convId] = count ?? 0
        }
        return result
    }

    private func loadParticipantsMap(conversationIds: [String]) async -> [String: [UserModel]] {
        let allRows: [ConversationParticipantModel] = await (try? client
            .from("conversation_participants")
            .select()
            .in("conversation_id", values: conversationIds)
            .execute()
            .value) ?? []

        let missingIds = Set(allRows.map(\.userId)).subtracting(userProfiles.keys)
        if !missingIds.isEmpty {
            let fresh: [UserModel] = await (try? client.from("users")
                .select()
                .in("id", values: Array(missingIds))
                .execute()
                .value) ?? []
            for user in fresh {
                userProfiles[user.id] = user
            }
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
                filter: .eq("conversation_id", value: convId)
            ) { [weak self] in
                await self?.onConversationActivity(convId: convId, userId: userId)
            }
        }
    }

    private func onConversationActivity(convId: String, userId: String) async {
        guard let last = await repo.getLastMessage(conversationId: convId),
              last.userFrom != userId else { return }
        let senderName = userProfiles[last.userFrom]?.name ?? L("Family member")
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
            let rows: [ConversationModel] = await (try? client.from("conversations")
                .select()
                .eq("id", value: conversationId)
                .execute()
                .value) ?? []
            conversation = rows.first
            await loadMessages(conversationId)

            let participantRows: [ConversationParticipantModel] = await (try? client
                .from("conversation_participants")
                .select()
                .eq("conversation_id", value: conversationId)
                .execute()
                .value) ?? []
            let participantIds = participantRows.map(\.userId)

            let missing = Set(participantIds).subtracting(userProfiles.keys)
            if !missing.isEmpty {
                let fresh: [UserModel] = await (try? client.from("users")
                    .select()
                    .in("id", values: Array(missing))
                    .execute()
                    .value) ?? []
                for user in fresh {
                    userProfiles[user.id] = user
                }
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
                for member in members {
                    userProfiles[member.id] = member
                }
            }

            subscribeToMessages(conversationId)
            subscribeToParticipants(conversationId)
            await subscribeToTyping(conversationId)
            await loadReactions(conversationId)
            await subscribeToReactions(conversationId)
        }
    }

    func loadMessages(_ conversationId: String) async {
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
            filter: .eq("conversation_id", value: conversationId)
        ) { [weak self] in
            await self?.loadMessages(conversationId)
        }
    }

    /// Live-updates "Seen" when another participant's last_read_at changes.
    private func subscribeToParticipants(_ conversationId: String) {
        participantsObserver.start(
            table: "conversation_participants",
            scope: "participants-\(conversationId)",
            filter: .eq("conversation_id", value: conversationId)
        ) { [weak self] in
            guard let self else { return }
            let rows: [ConversationParticipantModel] = await (try? client
                .from("conversation_participants")
                .select()
                .eq("conversation_id", value: conversationId)
                .execute()
                .value) ?? []
            let myId = repo.session.currentUserId
            otherLastRead = rows
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
            do {
                try await channel.subscribeWithError()
            } catch {
                return
            }
            for await message in stream {
                guard let self else { break }
                guard let payload = message["payload"]?.objectValue,
                      let userId = payload["userId"]?.stringValue,
                      let typing = payload["typing"]?.boolValue,
                      userId != repo.session.currentUserId
                else { continue }
                applyTypingSignal(userId: userId, typing: typing)
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
}
