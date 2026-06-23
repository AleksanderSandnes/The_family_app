package com.example.mainactivity.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.ConversationEntity
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.chatDao

    val conversations: Flow<List<ConversationEntity>> = repo.currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.conversationsForUser(id)
    }

    val currentUserId: Flow<Long?> = repo.currentUserId

    private var pendingCameraConversationId: Long = -1L
    private var pendingCameraFile: File? = null

    fun conversation(id: Long) = dao.observeConversation(id)
    fun messages(conversationId: Long) = dao.messagesForConversation(conversationId)

    fun createConversation(name: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        dao.insertConversation(ConversationEntity(userFrom = userId, userTo = 0, name = name))
    }

    fun send(conversationId: Long, text: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        dao.insertMessage(MessageEntity(conversationId = conversationId, userFrom = userId, text = text))
    }

    fun renameConversation(id: Long, name: String) = viewModelScope.launch {
        val conv = dao.observeConversation(id).first() ?: return@launch
        dao.updateConversation(conv.copy(name = name.trim()))
    }

    fun prepareCameraCapture(context: Context, conversationId: Long): Uri? {
        return try {
            val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
            val file = File(captureDir, "group_pending_$conversationId.jpg")
            pendingCameraFile = file
            pendingCameraConversationId = conversationId
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    fun onCameraResult(context: Context, success: Boolean) = viewModelScope.launch {
        if (!success) { pendingCameraFile = null; pendingCameraConversationId = -1L; return@launch }
        val file = pendingCameraFile ?: return@launch
        val convId = pendingCameraConversationId.takeIf { it != -1L } ?: return@launch
        pendingCameraFile = null
        pendingCameraConversationId = -1L
        persistImage(context, convId) { file.inputStream() }
    }

    fun saveImageFromUri(context: Context, uri: Uri, conversationId: Long) = viewModelScope.launch {
        persistImage(context, conversationId) { context.contentResolver.openInputStream(uri) }
    }

    fun removeImage(conversationId: Long) = viewModelScope.launch {
        val conv = dao.observeConversation(conversationId).first() ?: return@launch
        conv.imageUri?.let { File(it).takeIf { f -> f.exists() }?.delete() }
        dao.updateConversation(conv.copy(imageUri = null))
    }

    private fun persistImage(context: Context, conversationId: Long, openStream: () -> java.io.InputStream?) =
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val conv = dao.observeConversation(conversationId).first() ?: return@withContext
                val dir = File(context.filesDir, "group_images").also { it.mkdirs() }
                val dest = File(dir, "group_${conversationId}_${System.currentTimeMillis()}.jpg")
                openStream()?.use { src -> dest.outputStream().use { src.copyTo(it) } }
                    ?: return@withContext
                dao.updateConversation(conv.copy(imageUri = dest.absolutePath))
                conv.imageUri?.let { oldPath ->
                    val old = File(oldPath)
                    if (old.exists() && old.absolutePath != dest.absolutePath) old.delete()
                }
            }
        }
}
