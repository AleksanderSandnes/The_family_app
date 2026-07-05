package com.sandnes.familyapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val conversationId = intent.getStringExtra("conversation_id") ?: return
        val replyText =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FamilyRepository.get(context).sendMessage(conversationId, replyText)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
