package com.example.mainactivity.notifications

/**
 * Tracks which chat conversation (if any) is currently on screen.
 *
 * Set by `ChatViewModel.setCurrentConversation` and read by [FamilyMessagingService] so a
 * push-delivered message notification can be suppressed while the user is already looking
 * at that conversation (the live Realtime subscription renders it in-screen instead).
 */
object ActiveChat {
    @Volatile
    var conversationId: String? = null
}
