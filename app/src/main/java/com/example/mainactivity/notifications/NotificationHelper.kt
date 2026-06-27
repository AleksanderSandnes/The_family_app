@file:Suppress("ktlint:standard:function-naming", "InlinedApi", "UseKtx")

package com.example.mainactivity.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.mainactivity.MainActivity
import com.example.mainactivity.data.ConversationModel
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.receivers.ReplyReceiver

object NotificationHelper {
    const val CHANNEL_MESSAGES = "channel_messages"
    const val CHANNEL_BIRTHDAYS = "channel_birthdays"
    const val CHANNEL_CALENDAR = "channel_calendar"
    const val KEY_TEXT_REPLY = "key_text_reply"

    private const val AVATAR_BITMAP_SIZE = 96
    private const val AVATAR_TEXT_RATIO = 0.45f

    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "New message notifications" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BIRTHDAYS, "Birthdays", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Birthday reminders for family members" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALENDAR, "Calendar Events", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Reminders for upcoming calendar events" },
        )
    }

    fun postMessageNotification(
        context: Context,
        conversation: ConversationModel,
        message: MessageModel,
        sender: UserModel?,
    ) {
        val senderName = sender?.name ?: "Family member"
        val person =
            Person
                .Builder()
                .setName(senderName)
                .setIcon(IconCompat.createWithBitmap(createInitialBitmap(senderName)))
                .build()

        val messageText =
            when (message.messageType) {
                "image" -> "📷 Image"
                "voice" -> "🎤 Voice message"
                else -> message.text
            }

        val messagingStyle =
            NotificationCompat
                .MessagingStyle(person)
                .also { style ->
                    if (conversation.name.isNotBlank()) style.conversationTitle = conversation.name
                }.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        messageText,
                        System.currentTimeMillis(),
                        person,
                    ),
                )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setStyle(messagingStyle)
                .addAction(buildReplyAction(context, conversation))
                .setContentIntent(buildOpenIntent(context, conversation))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                NotificationManagerCompat.from(context).notify(conversation.id.hashCode(), notification)
            }
        }
    }

    /** Birthday reminder (server push, type=birthday). Mirrors the title wording of the
     *  retired NotificationWorker so existing copy is preserved. */
    fun postBirthdayNotification(
        context: Context,
        name: String,
        daysUntil: Int,
    ) {
        val title =
            if (daysUntil <= 0) {
                "$name's birthday is today!"
            } else {
                "$name's birthday is in $daysUntil day${if (daysUntil == 1) "" else "s"}"
            }
        postReminder(context, CHANNEL_BIRTHDAYS, ("birthday-$name").hashCode(), title)
    }

    /** Calendar event reminder (server push, type=event). */
    fun postEventNotification(
        context: Context,
        activity: String,
        daysUntil: Int,
    ) {
        val title =
            if (daysUntil <= 0) {
                "$activity is today!"
            } else {
                "$activity is in $daysUntil day${if (daysUntil == 1) "" else "s"}"
            }
        postReminder(context, CHANNEL_CALENDAR, ("event-$activity").hashCode(), title)
    }

    private fun postReminder(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
    ) {
        val icon =
            if (channelId == CHANNEL_BIRTHDAYS) {
                android.R.drawable.ic_popup_reminder
            } else {
                android.R.drawable.ic_menu_today
            }
        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(buildOpenAppIntent(context))
                .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        }
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildReplyAction(
        context: Context,
        conversation: ConversationModel,
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Reply").build()
        val replyIntent =
            PendingIntent.getBroadcast(
                context,
                conversation.id.hashCode(),
                Intent(context, ReplyReceiver::class.java).apply {
                    putExtra("conversation_id", conversation.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        return NotificationCompat.Action
            .Builder(android.R.drawable.ic_menu_send, "Reply", replyIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun buildOpenIntent(
        context: Context,
        conversation: ConversationModel,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            conversation.id.hashCode() + 1,
            Intent(context, MainActivity::class.java).apply {
                data = android.net.Uri.parse("familyapp://chat/${conversation.id}")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createInitialBitmap(name: String): Bitmap {
        val size = AVATAR_BITMAP_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.parseColor("#6366F1")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = size * AVATAR_TEXT_RATIO
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        canvas.drawText(
            initial,
            size / 2f,
            size / 2f - (paint.descent() + paint.ascent()) / 2,
            paint,
        )
        return bitmap
    }
}
