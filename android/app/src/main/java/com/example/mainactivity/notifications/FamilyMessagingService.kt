package com.example.mainactivity.notifications

import com.example.mainactivity.data.ConversationModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.UserModel
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM data messages and renders them with [NotificationHelper].
 *
 * All pushes are **data-only** (no `notification` block) so this service always runs and
 * we control channel/styling/de-dup ourselves — even when the app is backgrounded or
 * killed. The `type` data field selects the rendering path:
 *   - `message`  → MessagingStyle chat notification (suppressed if that chat is on screen)
 *   - `birthday` → birthday reminder
 *   - `event`    → calendar event reminder
 *
 * The server ([supabase/functions]) decides recipients and timing; this is purely display.
 */
class FamilyMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { FamilyRepository.get(applicationContext).registerPushToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "message" -> showMessage(data)
            "birthday" ->
                NotificationHelper.postBirthdayNotification(
                    applicationContext,
                    data["name"].orEmpty(),
                    data["daysUntil"]?.toIntOrNull() ?: 0,
                )
            "event" ->
                NotificationHelper.postEventNotification(
                    applicationContext,
                    data["activity"].orEmpty(),
                    data["daysUntil"]?.toIntOrNull() ?: 0,
                )
        }
    }

    private fun showMessage(data: Map<String, String>) {
        val conversationId = data["conversationId"] ?: return
        // Don't notify for the conversation the user is currently looking at — the live
        // Realtime subscription already shows the message in-screen.
        if (ActiveChat.conversationId == conversationId) return

        val conversation =
            ConversationModel(
                id = conversationId,
                name = data["conversationName"].orEmpty(),
                imageUri = data["imageUri"],
            )
        val msg =
            MessageModel(
                id = data["messageId"].orEmpty(),
                conversationId = conversationId,
                userFrom = data["senderId"].orEmpty(),
                text = data["text"].orEmpty(),
                messageType = data["messageType"] ?: "text",
            )
        val sender = data["senderName"]?.let { UserModel(id = msg.userFrom, name = it) }
        NotificationHelper.postMessageNotification(applicationContext, conversation, msg, sender)
    }
}
