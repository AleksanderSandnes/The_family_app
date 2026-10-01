package com.sandnes.familyapp.ui.chat

import androidx.annotation.StringRes
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.MessageModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Report reasons accepted by the `report_message` RPC (supabase/security/moderation.sql). */
enum class ReportReason(
    val dbValue: String,
    @StringRes val label: Int,
) {
    SPAM("spam", R.string.report_reason_spam),
    HARASSMENT("harassment", R.string.report_reason_harassment),
    INAPPROPRIATE("inappropriate", R.string.report_reason_inappropriate),
    OTHER("other", R.string.report_reason_other),
}

@Serializable
data class UserBlockRow(
    @SerialName("blocked_id") val blockedId: String,
)

/** Hides messages from users the current user has blocked; system messages always stay. */
fun visibleMessages(
    messages: List<MessageModel>,
    blocked: Set<String>,
): List<MessageModel> =
    if (blocked.isEmpty()) {
        messages
    } else {
        messages.filter { it.messageType == "system" || it.userFrom !in blocked }
    }

/** Only other people's persisted, non-system messages can be reported or their sender blocked. */
fun canModerate(
    msg: MessageModel,
    myId: String?,
): Boolean = myId != null && msg.userFrom != myId && msg.messageType != "system" && !msg.id.startsWith("temp-")
