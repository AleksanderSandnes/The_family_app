package com.example.mainactivity.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.ConversationEntity
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.chatDao

    val conversations: Flow<List<ConversationEntity>> = repo.currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.conversationsForUser(id)
    }

    val currentUserId: Flow<Long?> = repo.currentUserId

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
}
