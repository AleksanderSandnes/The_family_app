package com.sandnes.familyapp.notifications

import com.sandnes.familyapp.data.ConversationModel
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.MessageModel
import com.sandnes.familyapp.data.UserModel
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

        // Drop duplicate FCM deliveries of the same message (retries / a token registered
        // under more than one recipient) so it never banners twice.
        val messageId = data["messageId"].orEmpty()
        if (!Dedup.firstSeen(messageId)) return

        val senderId = data["senderId"].orEmpty()
        scope.launch {
            // Never notify the sender about their own message. A device can carry another
            // account's token row (two logins on one phone, or a rotated token), so the
            // server-side sender filter isn't enough — guard again against the current user.
            val currentUserId =
                runCatching { FamilyRepository.get(applicationContext).currentUserId.first() }.getOrNull()
            if (senderId.isNotEmpty() && senderId == currentUserId) return@launch

            val conversation =
                ConversationModel(
                    id = conversationId,
                    name = data["conversationName"].orEmpty(),
                    imageUri = data["imageUri"],
                )
            val msg =
                MessageModel(
                    id = messageId,
                    conversationId = conversationId,
                    userFrom = senderId,
                    text = data["text"].orEmpty(),
                    messageType = data["messageType"] ?: "text",
                )
            val sender = data["senderName"]?.let { UserModel(id = msg.userFrom, name = it) }
            NotificationHelper.postMessageNotification(applicationContext, conversation, msg, sender)
        }
    }

    /** Bounded set of recently shown message ids, so a re-delivered push never banners twice. */
    private object Dedup {
        private const val CAP = 100
        private val ids = LinkedHashSet<String>()

        @Synchronized
        fun firstSeen(id: String): Boolean {
            if (id.isEmpty()) return true
            if (!ids.add(id)) return false
            if (ids.size > CAP) ids.iterator().let { it.next(); it.remove() }
            return true
        }
    }
}
