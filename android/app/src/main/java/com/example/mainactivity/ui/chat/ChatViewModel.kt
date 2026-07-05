package com.example.mainactivity.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.ConversationModel
import com.example.mainactivity.data.ConversationParticipantModel
import com.example.mainactivity.data.ConversationWithPreview
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.MessageReactionModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject

@Serializable
data class TypingSignal(
    val userId: String,
    val typing: Boolean,
)

private const val TYPING_AUTOCLEAR_MS = 5000L
private const val TYPING_THROTTLE_MS = 2000L
private const val USER_ID_PREVIEW_LENGTH = 8

// Intentionally one class shared by the chat list and chat detail screens (see CLAUDE.md),
// so detail-screen deletes reflect in the list on pop-back. Splitting it into separate
// ViewModels would break that documented contract, so LargeClass is suppressed by design.
@Suppress("LargeClass")
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val app: Application,
        internal val repo: FamilyRepository,
    ) : AndroidViewModel(app) {
        private val db get() = SupabaseManager.client.postgrest

        private val _conversations = MutableStateFlow<List<ConversationWithPreview>>(emptyList())
        val conversations: StateFlow<List<ConversationWithPreview>> = _conversations.asStateFlow()

        val totalUnread: StateFlow<Int> =
            _conversations
                .map { list -> list.sumOf { it.unreadCount } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        private val _currentConversationId = MutableStateFlow<String?>(null)
        val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

        fun setCurrentConversation(id: String?) {
            _currentConversationId.value = id
            // Let FamilyMessagingService suppress push notifications for the open chat.
            com.example.mainactivity.notifications.ActiveChat.conversationId = id
        }

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        val currentUserId: Flow<String?> = repo.currentUserId

        private val _conversation = MutableStateFlow<ConversationModel?>(null)
        val conversation: StateFlow<ConversationModel?> = _conversation.asStateFlow()

        private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
        val messages: StateFlow<List<MessageModel>> = _messages.asStateFlow()

        private val _replyTo = MutableStateFlow<MessageModel?>(null)
        val replyTo: StateFlow<MessageModel?> = _replyTo.asStateFlow()

        private val _userProfiles = MutableStateFlow<Map<String, UserModel>>(emptyMap())
        val userProfiles: StateFlow<Map<String, UserModel>> = _userProfiles.asStateFlow()

        private val _currentParticipants = MutableStateFlow<List<UserModel>>(emptyList())
        val currentParticipants: StateFlow<List<UserModel>> = _currentParticipants.asStateFlow()

        // Latest read timestamp among OTHER participants in the open conversation, for "Seen".
        private val _otherLastRead = MutableStateFlow<String?>(null)
        val otherLastRead: StateFlow<String?> = _otherLastRead.asStateFlow()

        // Other users currently typing in the open conversation.
        private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
        val typingUsers: StateFlow<Set<String>> = _typingUsers.asStateFlow()

        private val _familyMembers = MutableStateFlow<List<UserModel>>(emptyList())
        val familyMembers: StateFlow<List<UserModel>> = _familyMembers.asStateFlow()

        private val _conversationParticipants = MutableStateFlow<Map<String, List<UserModel>>>(emptyMap())
        val conversationParticipants: StateFlow<Map<String, List<UserModel>>> = _conversationParticipants.asStateFlow()

        private val _navigateToConversation = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val navigateToConversation: SharedFlow<String> = _navigateToConversation.asSharedFlow()

        private val _conversationDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val conversationDeleted: SharedFlow<Unit> = _conversationDeleted.asSharedFlow()

        private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

        // Reactions: messageId -> (emoji -> list of userId)
        private val _reactions = MutableStateFlow<Map<String, Map<String, List<String>>>>(emptyMap())
        val reactions: StateFlow<Map<String, Map<String, List<String>>>> = _reactions.asStateFlow()

        private var realtimeChannel: RealtimeChannel? = null
        private var reactionsChannel: RealtimeChannel? = null
        private var participantsChannel: RealtimeChannel? = null
        private var typingChannel: RealtimeChannel? = null
        private var myUserId: String? = null
        private val typingClearJobs = mutableMapOf<String, Job>()
        private var lastTypingSentMs = 0L
        private val notifChannels = mutableMapOf<String, RealtimeChannel>()
        private var pendingCameraConversationId: String = ""
        private var pendingCameraFile: File? = null

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    if (userId != null) loadConversations(userId) else _conversations.value = emptyList()
                }
            }
            viewModelScope.launch {
                repo.familyChanged.collect {
                    val userId = repo.currentUserId.first() ?: return@collect
                    loadConversations(userId)
                }
            }
            viewModelScope.launch {
                var seenDisconnect = false
                runCatching { SupabaseManager.client.realtime.status }.getOrNull()?.collect { status ->
                    when (status) {
                        Realtime.Status.DISCONNECTED -> seenDisconnect = true
                        Realtime.Status.CONNECTED ->
                            if (seenDisconnect) {
                                seenDisconnect = false
                                val userId = repo.currentUserId.first() ?: return@collect
                                loadConversations(userId)
                                _currentConversationId.value?.let { loadMessages(it) }
                            }
                        else -> {}
                    }
                }
            }
        }

        private suspend fun loadConversations(userId: String) {
            _isLoading.value = true
            runCatching {
                val user = repo.getUser(userId)
                if (user?.familyId != null) {
                    val members = repo.getFamilyMembers(user.familyId)
                    _familyMembers.value = members
                    _userProfiles.value = members.associateBy { it.id }
                }

                val convs = db.from("conversations").select().decodeList<ConversationModel>()
                if (convs.isEmpty()) {
                    _conversations.value = emptyList()
                    _conversationParticipants.value = emptyMap()
                    return@runCatching
                }

                val participantsMap = loadParticipantsMap(convs.map { it.id })
                _conversationParticipants.value = participantsMap
                _conversations.value = buildConversationPreviews(convs, userId, participantsMap)
                subscribeAllForNotifications(userId)
            }
            _isLoading.value = false
        }

        /** Loads participant rows for the given conversations, fetching any unknown user
         *  profiles, and returns conversationId → participant profiles. */
        private suspend fun loadParticipantsMap(conversationIds: List<String>): Map<String, List<UserModel>> {
            val allRows =
                runCatching {
                    db
                        .from("conversation_participants")
                        .select { filter { isIn("conversation_id", conversationIds) } }
                        .decodeList<ConversationParticipantModel>()
                }.getOrDefault(emptyList())

            val knownIds = _userProfiles.value.keys
            val missingIds = allRows.map { it.userId }.distinct().filter { it !in knownIds }
            if (missingIds.isNotEmpty()) {
                val fresh =
                    db
                        .from("users")
                        .select { filter { isIn("id", missingIds) } }
                        .decodeList<UserModel>()
                _userProfiles.update { it + fresh.associateBy { u -> u.id } }
            }

            return allRows
                .groupBy { it.conversationId }
                .mapValues { (_, rows) -> rows.mapNotNull { _userProfiles.value[it.userId] } }
        }

        /** Builds list previews (last message + sender label) for each conversation in parallel. */
        private suspend fun buildConversationPreviews(
            convs: List<ConversationModel>,
            userId: String,
            participantsMap: Map<String, List<UserModel>>,
        ): List<ConversationWithPreview> =
            coroutineScope {
                convs
                    .map { conv ->
                        async {
                            val lastMsg = repo.getLastMessage(conv.id)
                            val lastSenderName =
                                when {
                                    lastMsg == null -> null
                                    lastMsg.userFrom == userId -> "You"
                                    else ->
                                        _userProfiles.value[lastMsg.userFrom]?.name
                                            ?: lastMsg.userFrom.take(USER_ID_PREVIEW_LENGTH)
                                }
                            ConversationWithPreview(
                                conversation = conv,
                                lastMessage = lastMsg,
                                lastSenderName = lastSenderName,
                                unreadCount = 0,
                                participants = participantsMap[conv.id] ?: emptyList(),
                            )
                        }
                    }.awaitAll()
            }

        private suspend fun subscribeAllForNotifications(userId: String) {
            _conversations.value.forEach { preview ->
                val convId = preview.conversation.id
                if (notifChannels.containsKey(convId)) return@forEach
                val channel = runCatching { SupabaseManager.client.channel("notify-$convId") }.getOrNull() ?: return
                notifChannels[convId] = channel
                val insertFlow =
                    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "messages"
                        filter("conversation_id", FilterOperator.EQ, convId)
                    }
                channel.subscribe()
                viewModelScope.launch {
                    insertFlow.collect { change ->
                        val msg = change.decodeRecord<MessageModel>()
                        if (msg.userFrom != userId) {
                            val isCurrentConv = _currentConversationId.value == convId
                            // Increment unread if not viewing this conversation
                            if (!isCurrentConv) {
                                _conversations.update { list ->
                                    list.map { p ->
                                        if (p.conversation.id == convId) {
                                            p.copy(unreadCount = p.unreadCount + 1)
                                        } else {
                                            p
                                        }
                                    }
                                }
                            }
                            // Update last-message preview
                            val senderName = _userProfiles.value[msg.userFrom]?.name ?: "Family member"
                            _conversations.update { list ->
                                list.map { p ->
                                    if (p.conversation.id == convId) {
                                        p.copy(lastMessage = msg, lastSenderName = senderName)
                                    } else {
                                        p
                                    }
                                }
                            }
                            // Note: message notifications are delivered via FCM
                            // (FamilyMessagingService) so they also arrive when the app is
                            // killed. This Realtime path only keeps the in-app list live.
                        }
                    }
                }
            }
        }

        /** Public entry-point for pull-to-refresh; delegates to the private loader. */
        suspend fun refreshConversations(userId: String) = loadConversations(userId)

        fun markRead(conversationId: String) {
            viewModelScope.launch {
                repo.markConversationRead(conversationId)
                _conversations.update { list ->
                    list.map { if (it.conversation.id == conversationId) it.copy(unreadCount = 0) else it }
                }
            }
        }

        fun loadConversation(conversationId: String) =
            viewModelScope.launch {
                runCatching {
                    _conversation.value =
                        db
                            .from("conversations")
                            .select { filter { eq("id", conversationId) } }
                            .decodeList<ConversationModel>()
                            .firstOrNull()
                    loadMessages(conversationId)

                    val participantRows =
                        db
                            .from("conversation_participants")
                            .select { filter { eq("conversation_id", conversationId) } }
                            .decodeList<ConversationParticipantModel>()
                    val participantUserIds = participantRows.map { it.userId }

                    val knownIds = _userProfiles.value.keys
                    val missingIds = participantUserIds.filter { it !in knownIds }
                    if (missingIds.isNotEmpty()) {
                        val fresh =
                            db
                                .from("users")
                                .select { filter { isIn("id", missingIds) } }
                                .decodeList<UserModel>()
                        _userProfiles.update { it + fresh.associateBy { u -> u.id } }
                    }
                    _currentParticipants.value = participantUserIds.mapNotNull { _userProfiles.value[it] }

                    val userId = repo.currentUserId.first()
                    myUserId = userId
                    _otherLastRead.value =
                        participantRows.filter { it.userId != userId }.mapNotNull { it.lastReadAt }.maxOrNull()
                    val user = if (userId != null) repo.getUser(userId) else null
                    if (user?.familyId != null && _familyMembers.value.isEmpty()) {
                        val members = repo.getFamilyMembers(user.familyId)
                        _familyMembers.value = members
                        _userProfiles.update { existing -> existing + members.associateBy { it.id } }
                    }

                    subscribeToMessages(conversationId)
                    subscribeToParticipants(conversationId)
                    subscribeToTyping(conversationId)
                    loadReactions(conversationId)
                    subscribeToReactions(conversationId)
                }
            }

        /** Live-updates the "Seen" receipt when another participant's last_read_at changes. */
        private suspend fun subscribeToParticipants(conversationId: String) {
            participantsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            val channel = runCatching { SupabaseManager.client.channel("participants-$conversationId") }.getOrNull() ?: return
            participantsChannel = channel
            val flow =
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "conversation_participants"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }
            channel.subscribe()
            viewModelScope.launch {
                val myId = repo.currentUserId.first()
                flow.collect {
                    val rows =
                        db
                            .from("conversation_participants")
                            .select { filter { eq("conversation_id", conversationId) } }
                            .decodeList<ConversationParticipantModel>()
                    _otherLastRead.value =
                        rows.filter { it.userId != myId }.mapNotNull { it.lastReadAt }.maxOrNull()
                }
            }
        }

        /** Subscribes to ephemeral "typing" broadcasts on the conversation. */
        private suspend fun subscribeToTyping(conversationId: String) {
            typingChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            _typingUsers.value = emptySet()
            val channel = runCatching { SupabaseManager.client.channel("typing-$conversationId") }.getOrNull() ?: return
            typingChannel = channel
            val flow = channel.broadcastFlow<TypingSignal>("typing")
            channel.subscribe()
            viewModelScope.launch {
                flow.collect { signal ->
                    if (signal.userId == myUserId) return@collect
                    typingClearJobs.remove(signal.userId)?.cancel()
                    if (signal.typing) {
                        _typingUsers.update { it + signal.userId }
                        // Auto-clear in case the "stopped typing" signal is missed.
                        typingClearJobs[signal.userId] =
                            viewModelScope.launch {
                                delay(TYPING_AUTOCLEAR_MS)
                                _typingUsers.update { it - signal.userId }
                            }
                    } else {
                        _typingUsers.update { it - signal.userId }
                    }
                }
            }
        }

        /** Broadcasts the current user's typing state (throttled to once every 2s for "typing"). */
        fun setTyping(typing: Boolean) {
            val channel = typingChannel ?: return
            val me = myUserId ?: return
            val now = System.currentTimeMillis()
            if (typing && now - lastTypingSentMs < TYPING_THROTTLE_MS) return
            if (typing) lastTypingSentMs = now
            viewModelScope.launch {
                runCatching { channel.broadcast("typing", TypingSignal(me, typing)) }
            }
        }

        private suspend fun loadMessages(conversationId: String) {
            _messages.value =
                db
                    .from("messages")
                    .select {
                        filter { eq("conversation_id", conversationId) }
                        order("sent_at", Order.ASCENDING)
                    }.decodeList<MessageModel>()
        }

        private suspend fun subscribeToMessages(conversationId: String) {
            realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            val channel = runCatching { SupabaseManager.client.channel("messages-$conversationId") }.getOrNull() ?: return
            realtimeChannel = channel
            val insertFlow =
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }
            channel.subscribe()
            viewModelScope.launch {
                val myId = repo.currentUserId.first()
                insertFlow.collect { change ->
                    val msg = change.decodeRecord<MessageModel>()
                    if (msg.userFrom != myId) {
                        _messages.update { it + msg }
                    }
                }
            }
        }

        // ── Reactions ────────────────────────────────────────────────────────────

        fun loadReactions(conversationId: String) {
            viewModelScope.launch {
                val result =
                    runCatching {
                        SupabaseManager.client.postgrest["message_reactions"]
                            .select { filter { eq("conversation_id", conversationId) } }
                            .decodeList<MessageReactionModel>()
                    }.getOrNull() ?: return@launch
                _reactions.value =
                    result
                        .groupBy { it.messageId }
                        .mapValues { (_, rs) ->
                            rs
                                .groupBy { it.emoji }
                                .mapValues { (_, users) -> users.map { it.userId } }
                        }
            }
        }

        private suspend fun subscribeToReactions(conversationId: String) {
            reactionsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            val channel = runCatching { SupabaseManager.client.channel("reactions-$conversationId") }.getOrNull() ?: return
            reactionsChannel = channel
            val insertFlow =
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "message_reactions"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }
            val deleteFlow =
                channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                    table = "message_reactions"
                }
            channel.subscribe()
            viewModelScope.launch {
                insertFlow.collect { change ->
                    val reaction = change.decodeRecord<MessageReactionModel>()
                    _reactions.update { current ->
                        val msgReactions = current[reaction.messageId]?.toMutableMap() ?: mutableMapOf()
                        val users = msgReactions[reaction.emoji]?.toMutableList() ?: mutableListOf()
                        if (!users.contains(reaction.userId)) users.add(reaction.userId)
                        msgReactions[reaction.emoji] = users
                        current + (reaction.messageId to msgReactions)
                    }
                }
            }
            viewModelScope.launch {
                deleteFlow.collect {
                    loadReactions(conversationId)
                }
            }
        }

        fun toggleReaction(
            messageId: String,
            conversationId: String,
            emoji: String,
        ) {
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val myCurrentEmoji =
                    _reactions.value[messageId]
                        ?.entries
                        ?.find { userId in it.value }
                        ?.key

                // Optimistic update so the UI reflects the change immediately
                _reactions.update { current ->
                    val msgs = current[messageId]?.toMutableMap() ?: mutableMapOf()
                    if (myCurrentEmoji != null) {
                        val remaining = msgs[myCurrentEmoji].orEmpty().filter { it != userId }
                        if (remaining.isEmpty()) msgs.remove(myCurrentEmoji) else msgs[myCurrentEmoji] = remaining
                    }
                    if (myCurrentEmoji != emoji) {
                        msgs[emoji] = msgs[emoji].orEmpty() + userId
                    }
                    if (msgs.isEmpty()) current - messageId else current + (messageId to msgs)
                }

                if (myCurrentEmoji == emoji) {
                    repo.removeReaction(messageId)
                } else {
                    repo.addReaction(messageId, conversationId, emoji)
                }
            }
        }

        // ── Media send ───────────────────────────────────────────────────────────

        fun sendImage(
            conversationId: String,
            bytes: ByteArray,
            filename: String,
        ) {
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                runCatching {
                    val url = repo.uploadChatMedia(conversationId, bytes, filename)
                    db.from("messages").insert(
                        buildJsonObject {
                            put("conversation_id", conversationId)
                            put("user_from", userId)
                            put("text", "")
                            put("message_type", "image")
                            put("media_url", url)
                        },
                    )
                    loadMessages(conversationId)
                }.onFailure { e -> Log.e("ChatVM", "sendImage failed", e) }
            }
        }

        fun sendVoice(
            conversationId: String,
            bytes: ByteArray,
            filename: String,
        ) {
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                runCatching {
                    val url = repo.uploadChatMedia(conversationId, bytes, filename)
                    db.from("messages").insert(
                        buildJsonObject {
                            put("conversation_id", conversationId)
                            put("user_from", userId)
                            put("text", "")
                            put("message_type", "voice")
                            put("media_url", url)
                        },
                    )
                    loadMessages(conversationId)
                }.onFailure { e -> Log.e("ChatVM", "sendVoice failed", e) }
            }
        }

        // ── Existing methods (preserved, updated for ConversationWithPreview) ────

        private suspend fun findExistingOneOnOne(
            userId: String,
            otherId: String,
        ): String? {
            _conversations.value
                .firstOrNull { preview ->
                    preview.conversation.userTo != null &&
                        (
                            (preview.conversation.userFrom == userId && preview.conversation.userTo == otherId) ||
                                (preview.conversation.userFrom == otherId && preview.conversation.userTo == userId)
                        )
                }?.conversation
                ?.id
                ?.let { return it }

            return runCatching {
                val myConvIds = _conversations.value.map { it.conversation.id }
                if (myConvIds.isEmpty()) return@runCatching null

                val otherRows =
                    db
                        .from("conversation_participants")
                        .select { filter { eq("user_id", otherId) } }
                        .decodeList<ConversationParticipantModel>()
                val sharedIds = otherRows.map { it.conversationId }.filter { it in myConvIds.toSet() }
                if (sharedIds.isEmpty()) return@runCatching null

                val allRows =
                    db
                        .from("conversation_participants")
                        .select { filter { isIn("conversation_id", sharedIds) } }
                        .decodeList<ConversationParticipantModel>()

                allRows
                    .groupBy { it.conversationId }
                    .entries
                    .firstOrNull { (_, rows) -> rows.size == 2 }
                    ?.key
            }.getOrNull()
        }

        fun createConversation(
            name: String,
            memberIds: List<String>,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val user = repo.getUser(userId) ?: return@launch

                if (memberIds.size == 1) {
                    val existing = findExistingOneOnOne(userId, memberIds[0])
                    if (existing != null) {
                        _navigateToConversation.emit(existing)
                        return@launch
                    }
                }

                runCatching {
                    val conv =
                        db
                            .from("conversations")
                            .insert(
                                buildJsonObject {
                                    put("user_from", userId)
                                    put("name", name.trim())
                                    if (user.familyId != null) put("family_id", user.familyId)
                                },
                            ) { select() }
                            .decodeList<ConversationModel>()
                            .first()

                    val allParticipants = (listOf(userId) + memberIds).distinct()
                    allParticipants.forEach { participantId ->
                        runCatching {
                            db.from("conversation_participants").insert(
                                buildJsonObject {
                                    put("conversation_id", conv.id)
                                    put("user_id", participantId)
                                },
                            )
                        }.onFailure { e -> Log.w("ChatVM", "Failed to add participant $participantId: ${e.message}") }
                    }
                }.onFailure { e -> Log.e("ChatVM", "createConversation failed", e) }
                loadConversations(userId)
            }

        fun addMember(
            conversationId: String,
            newUserId: String,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val user = repo.getUser(userId) ?: return@launch
                val participants = _currentParticipants.value

                runCatching {
                    if (participants.size <= 2) {
                        val allIds = (participants.map { it.id } + newUserId).distinct()
                        val conv =
                            db
                                .from("conversations")
                                .insert(
                                    buildJsonObject {
                                        put("user_from", userId)
                                        put("name", "")
                                        if (user.familyId != null) put("family_id", user.familyId)
                                    },
                                ) { select() }
                                .decodeList<ConversationModel>()
                                .first()

                        allIds.forEach { participantId ->
                            db.from("conversation_participants").insert(
                                buildJsonObject {
                                    put("conversation_id", conv.id)
                                    put("user_id", participantId)
                                },
                            )
                        }
                        _navigateToConversation.emit(conv.id)
                    } else {
                        db.from("conversation_participants").insert(
                            buildJsonObject {
                                put("conversation_id", conversationId)
                                put("user_id", newUserId)
                            },
                        )
                        val addedUser = _userProfiles.value[newUserId]
                        val systemText = "${addedUser?.name ?: "A member"} was added to the group"
                        runCatching {
                            db.from("messages").insert(
                                buildJsonObject {
                                    put("conversation_id", conversationId)
                                    put("user_from", userId)
                                    put("text", systemText)
                                    put("message_type", "system")
                                },
                            )
                        }
                        loadConversation(conversationId)
                    }
                }
                loadConversations(userId)
            }

        fun removeMember(
            conversationId: String,
            targetUserId: String,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                runCatching {
                    db.from("conversation_participants").delete {
                        filter {
                            eq("conversation_id", conversationId)
                            eq("user_id", targetUserId)
                        }
                    }
                }
                if (targetUserId == userId) {
                    loadConversations(userId)
                } else {
                    loadConversation(conversationId)
                    loadConversations(userId)
                }
            }

        fun setReplyTo(msg: MessageModel) {
            _replyTo.value = msg
        }

        fun clearReplyTo() {
            _replyTo.value = null
        }

        fun send(
            conversationId: String,
            text: String,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val pendingReplyTo = _replyTo.value
                _replyTo.value = null

                val tempId = "temp-${java.util.UUID.randomUUID()}"
                _messages.update {
                    it +
                        MessageModel(
                            id = tempId,
                            conversationId = conversationId,
                            userFrom = userId,
                            text = text,
                            replyToId = pendingReplyTo?.id,
                        )
                }

                val insertOk =
                    runCatching {
                        db.from("messages").insert(
                            buildJsonObject {
                                put("conversation_id", conversationId)
                                put("user_from", userId)
                                put("text", text)
                                if (pendingReplyTo != null) put("reply_to_id", pendingReplyTo.id)
                            },
                        )
                    }.onFailure { e ->
                        Log.e("ChatVM", "send failed", e)
                        _errorEvent.emit("Failed to send message")
                    }.isSuccess

                runCatching {
                    loadMessages(conversationId)
                }.onFailure {
                    if (!insertOk) _messages.update { it.filter { msg -> msg.id != tempId } }
                }
                // Update conversation list preview for own sent message
                _conversations.update { list ->
                    list.map { p ->
                        if (p.conversation.id == conversationId) {
                            val lastMsg = _messages.value.lastOrNull { it.conversationId == conversationId }
                            if (lastMsg != null) p.copy(lastMessage = lastMsg, lastSenderName = "You") else p
                        } else {
                            p
                        }
                    }
                }
            }

        fun renameConversation(
            id: String,
            name: String,
        ) =
            viewModelScope.launch {
                runCatching {
                    db.from("conversations").update({
                        set("name", name.trim())
                    }) { filter { eq("id", id) } }
                }
                _conversation.update { it?.copy(name = name.trim()) }
                val userId = repo.currentUserId.first() ?: return@launch
                loadConversations(userId)
            }

        fun prepareCameraCapture(
            context: Context,
            conversationId: String,
        ): Uri? =
            runCatching {
                val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
                val file = File(captureDir, "group_pending.jpg")
                pendingCameraFile = file
                pendingCameraConversationId = conversationId
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()

        fun onCameraResult(success: Boolean) =
            viewModelScope.launch {
                if (!success) {
                    pendingCameraFile = null
                    pendingCameraConversationId = ""
                    return@launch
                }
                val file = pendingCameraFile ?: return@launch
                val convId = pendingCameraConversationId.ifEmpty { return@launch }
                pendingCameraFile = null
                pendingCameraConversationId = ""
                val bytes = withContext(Dispatchers.IO) { file.readBytes().also { file.delete() } }
                uploadGroupImage(convId, bytes)
            }

        fun saveImageFromUri(
            context: Context,
            uri: Uri,
            conversationId: String,
        ) =
            viewModelScope.launch {
                val bytes =
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: return@launch
                uploadGroupImage(conversationId, bytes)
            }

        fun removeImage(conversationId: String) =
            viewModelScope.launch {
                runCatching {
                    SupabaseManager.client.storage
                        .from("group-images")
                        .delete("$conversationId/image.jpg")
                    db.from("conversations").update({
                        set("image_uri", null as String?)
                    }) { filter { eq("id", conversationId) } }
                }
                _conversation.update { it?.copy(imageUri = null) }
                val userId = repo.currentUserId.first() ?: return@launch
                loadConversations(userId)
            }

        private fun uploadGroupImage(
            conversationId: String,
            bytes: ByteArray,
        ) =
            viewModelScope.launch {
                runCatching {
                    val bucket = SupabaseManager.client.storage.from("group-images")
                    bucket.upload("$conversationId/image.jpg", bytes) { upsert = true }
                    val url = bucket.publicUrl("$conversationId/image.jpg") + "?t=${System.currentTimeMillis()}"
                    db.from("conversations").update({
                        set("image_uri", url)
                    }) { filter { eq("id", conversationId) } }
                    _conversation.update { it?.copy(imageUri = url) }
                    val userId = repo.currentUserId.first() ?: return@runCatching
                    loadConversations(userId)
                }.onFailure { e -> Log.e("ChatVM", "Group image upload failed", e) }
            }

        fun deleteConversation(conversationId: String) =
            viewModelScope.launch {
                runCatching {
                    runCatching {
                        SupabaseManager.client.storage
                            .from("group-images")
                            .delete("$conversationId/image.jpg")
                    }
                    db.from("conversations").delete { filter { eq("id", conversationId) } }
                }.onSuccess {
                    _conversations.update { it.filter { preview -> preview.conversation.id != conversationId } }
                    val userId = repo.currentUserId.first() ?: return@onSuccess
                    loadConversations(userId)
                    _conversationDeleted.emit(Unit)
                }.onFailure { e ->
                    Log.e("ChatVM", "deleteConversation failed", e)
                    _errorEvent.emit("Failed to delete conversation")
                }
            }

        override fun onCleared() {
            viewModelScope.launch {
                realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
                reactionsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
                participantsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
                typingChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
                notifChannels.values.forEach { ch ->
                    runCatching { SupabaseManager.client.realtime.removeChannel(ch) }
                }
            }
        }
    }
