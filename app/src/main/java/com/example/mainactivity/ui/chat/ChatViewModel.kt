package com.example.mainactivity.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.ConversationModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _conversations = MutableStateFlow<List<ConversationModel>>(emptyList())
    val conversations: StateFlow<List<ConversationModel>> = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val currentUserId: Flow<String?> = repo.currentUserId

    private val _conversation = MutableStateFlow<ConversationModel?>(null)
    val conversation: StateFlow<ConversationModel?> = _conversation.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages.asStateFlow()

    private val _replyTo = MutableStateFlow<MessageModel?>(null)
    val replyTo: StateFlow<MessageModel?> = _replyTo.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
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
    }

    private suspend fun loadConversations(userId: String) {
        _isLoading.value = true
        runCatching {
            val user = repo.getUser(userId)
            _conversations.value = if (user?.familyId != null) {
                db.from("conversations")
                    .select { filter { or { eq("user_from", userId); eq("family_id", user.familyId) } } }
                    .decodeList<ConversationModel>()
                    .filter { it.familyId == null || it.familyId == user.familyId }
            } else {
                db.from("conversations")
                    .select { filter { eq("user_from", userId) } }
                    .decodeList<ConversationModel>()
                    .filter { it.familyId == null }
            }
        }
        _isLoading.value = false
    }

    fun loadConversation(conversationId: String) = viewModelScope.launch {
        runCatching {
            _conversation.value = db.from("conversations")
                .select { filter { eq("id", conversationId) } }
                .decodeList<ConversationModel>()
                .firstOrNull()
            _messages.value = db.from("messages")
                .select { filter { eq("conversation_id", conversationId) }; order("sent_at", Order.ASCENDING) }
                .decodeList<MessageModel>()
            subscribeToMessages(conversationId)
        }
    }

    private suspend fun subscribeToMessages(conversationId: String) {
        realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("messages-$conversationId")
        realtimeChannel = channel
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
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

    fun createConversation(name: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val user = repo.getUser(userId) ?: return@launch
        runCatching {
            db.from("conversations").insert(buildJsonObject {
                put("user_from", userId)
                put("name", name.trim())
                if (user.familyId != null) put("family_id", user.familyId)
            })
        }
        loadConversations(userId)
    }

    fun setReplyTo(msg: MessageModel) { _replyTo.value = msg }
    fun clearReplyTo() { _replyTo.value = null }

    fun send(conversationId: String, text: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val pendingReplyTo = _replyTo.value
        runCatching {
            db.from("messages").insert(buildJsonObject {
                put("conversation_id", conversationId)
                put("user_from", userId)
                put("text", text)
                if (pendingReplyTo != null) put("reply_to_id", pendingReplyTo.id)
            })
            _replyTo.value = null
            _messages.value = db.from("messages")
                .select { filter { eq("conversation_id", conversationId) }; order("sent_at", Order.ASCENDING) }
                .decodeList<MessageModel>()
        }
    }

    fun renameConversation(id: String, name: String) = viewModelScope.launch {
        runCatching {
            db.from("conversations").update({
                set("name", name.trim())
            }) { filter { eq("id", id) } }
        }
        _conversation.update { it?.copy(name = name.trim()) }
        val userId = repo.currentUserId.first() ?: return@launch
        loadConversations(userId)
    }

    fun prepareCameraCapture(context: Context, conversationId: String): Uri? = try {
        val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
        val file = File(captureDir, "group_pending.jpg")
        pendingCameraFile = file
        pendingCameraConversationId = conversationId
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) { null }

    fun onCameraResult(context: Context, success: Boolean) = viewModelScope.launch {
        if (!success) { pendingCameraFile = null; pendingCameraConversationId = ""; return@launch }
        val file = pendingCameraFile ?: return@launch
        val convId = pendingCameraConversationId.ifEmpty { return@launch }
        pendingCameraFile = null
        pendingCameraConversationId = ""
        val bytes = withContext(Dispatchers.IO) { file.readBytes().also { file.delete() } }
        uploadGroupImage(convId, bytes)
    }

    fun saveImageFromUri(context: Context, uri: Uri, conversationId: String) = viewModelScope.launch {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return@launch
        uploadGroupImage(conversationId, bytes)
    }

    fun removeImage(conversationId: String) = viewModelScope.launch {
        runCatching {
            SupabaseManager.client.storage.from("group-images").delete("$conversationId/image.jpg")
            db.from("conversations").update({
                set("image_uri", null as String?)
            }) { filter { eq("id", conversationId) } }
        }
        _conversation.update { it?.copy(imageUri = null) }
        val userId = repo.currentUserId.first() ?: return@launch
        loadConversations(userId)
    }

    private fun uploadGroupImage(conversationId: String, bytes: ByteArray) = viewModelScope.launch {
        runCatching {
            val bucket = SupabaseManager.client.storage.from("group-images")
            bucket.upload("$conversationId/image.jpg", bytes) { upsert = true }
            // Append timestamp so Coil treats each upload as a new image and doesn't serve a stale cache
            val url = bucket.publicUrl("$conversationId/image.jpg") + "?t=${System.currentTimeMillis()}"
            db.from("conversations").update({
                set("image_uri", url)
            }) { filter { eq("id", conversationId) } }
            _conversation.update { it?.copy(imageUri = url) }
            val userId = repo.currentUserId.first() ?: return@runCatching
            loadConversations(userId)
        }.onFailure { e -> Log.e("ChatVM", "Group image upload failed", e) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        }
    }
}
