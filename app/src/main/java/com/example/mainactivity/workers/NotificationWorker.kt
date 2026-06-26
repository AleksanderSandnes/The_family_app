package com.example.mainactivity.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mainactivity.MainActivity
import com.example.mainactivity.data.BirthdayModel
import com.example.mainactivity.data.CalendarEventModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import com.example.mainactivity.notifications.NotificationHelper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

const val NOTIFICATION_WORK_NAME = "family_notifications"
private const val NOTIFICATION_HOUR = 9

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = FamilyRepository.get(applicationContext)
        if (!repo.notificationsEnabled.first()) return Result.success()

        val userId = repo.currentUserId.first() ?: return Result.success()
        val daysBefore = repo.notifyDaysBefore.first()
        val today = LocalDate.now()

        NotificationHelper.createAllChannels(applicationContext)

        val user =
            runCatching {
                SupabaseManager.client.postgrest
                    .from("users")
                    .select { filter { eq("id", userId) } }
                    .decodeList<UserModel>()
                    .firstOrNull()
            }.getOrNull() ?: return Result.success()

        notifyBirthdays(fetchBirthdays(userId, user.familyId), today, daysBefore)
        notifyEvents(fetchEvents(userId, user.familyId), today, daysBefore)
        return Result.success()
    }

    private suspend fun fetchBirthdays(
        userId: String,
        familyId: String?,
    ): List<BirthdayModel> =
        runCatching {
            val db = SupabaseManager.client.postgrest
            if (familyId != null) {
                db.from("birthdays").select {
                    filter {
                        or {
                            eq("made_by_user_id", userId)
                            eq("family_id", familyId)
                        }
                    }
                }
            } else {
                db.from("birthdays").select { filter { eq("made_by_user_id", userId) } }
            }.decodeList<BirthdayModel>()
        }.getOrDefault(emptyList())

    private suspend fun fetchEvents(
        userId: String,
        familyId: String?,
    ): List<CalendarEventModel> =
        runCatching {
            val db = SupabaseManager.client.postgrest
            if (familyId != null) {
                db.from("calendar_events").select {
                    filter {
                        or {
                            eq("user_id", userId)
                            eq("family_id", familyId)
                        }
                    }
                }
            } else {
                db.from("calendar_events").select { filter { eq("user_id", userId) } }
            }.decodeList<CalendarEventModel>()
        }.getOrDefault(emptyList())

    private fun notifyBirthdays(
        birthdays: List<BirthdayModel>,
        today: LocalDate,
        daysBefore: Int,
    ) = birthdays.forEach { b ->
        val daysUntil = daysUntilRecurring(b.date, today) ?: return@forEach
        if (daysUntil == 0 || (daysBefore > 0 && daysUntil == daysBefore)) {
            postBirthdayNotification(b, daysUntil)
        }
    }

    private fun notifyEvents(
        events: List<CalendarEventModel>,
        today: LocalDate,
        daysBefore: Int,
    ) = events.forEach { e ->
        val daysUntil = daysUntilOneTime(e.dateFrom, today) ?: return@forEach
        if (daysUntil == 0 || (daysBefore > 0 && daysUntil == daysBefore)) {
            postEventNotification(e, daysUntil)
        }
    }

    private fun tapIntent(): PendingIntent {
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        return PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun postBirthdayNotification(
        b: BirthdayModel,
        daysUntil: Int,
    ) {
        val title =
            if (daysUntil == 0) {
                "${b.name}'s birthday is today!"
            } else {
                "${b.name}'s birthday is in $daysUntil day${if (daysUntil == 1) "" else "s"}"
            }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            b.id.hashCode() * 2,
            NotificationCompat
                .Builder(applicationContext, NotificationHelper.CHANNEL_BIRTHDAYS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(tapIntent())
                .build(),
        )
    }

    private fun postEventNotification(
        e: CalendarEventModel,
        daysUntil: Int,
    ) {
        val title =
            if (daysUntil == 0) {
                "${e.activity} is today!"
            } else {
                "${e.activity} is in $daysUntil day${if (daysUntil == 1) "" else "s"}"
            }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            e.id.hashCode() * 2 + 1,
            NotificationCompat
                .Builder(applicationContext, NotificationHelper.CHANNEL_CALENDAR)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(tapIntent())
                .build(),
        )
    }

    companion object {
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(nextNineAmDelayMillis(), TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NOTIFICATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOTIFICATION_WORK_NAME)
        }

        private fun nextNineAmDelayMillis(): Long {
            val now = LocalDateTime.now()
            val nineAmToday = now.toLocalDate().atTime(NOTIFICATION_HOUR, 0)
            val nineAm = if (now.isBefore(nineAmToday)) nineAmToday else nineAmToday.plusDays(1)
            return Duration.between(now, nineAm).toMillis().coerceAtLeast(0L)
        }
    }
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

internal fun daysUntilRecurring(
    dateStr: String,
    today: LocalDate,
): Int? {
    val monthDay =
        runCatching {
            val d = LocalDate.parse(dateStr.trim())
            MonthDay.of(d.month, d.dayOfMonth)
        }.getOrNull() ?: runCatching {
            MonthDay.parse(dateStr.trim(), DATE_FORMATTER)
        }.getOrNull() ?: return null
    var target = monthDay.atYear(today.year)
    if (target < today) target = monthDay.atYear(today.year + 1)
    return ChronoUnit.DAYS.between(today, target).toInt()
}

internal fun daysUntilOneTime(
    dateStr: String,
    today: LocalDate,
): Int? {
    val target =
        runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull()
            ?: runCatching { MonthDay.parse(dateStr.trim(), DATE_FORMATTER).atYear(today.year) }.getOrNull()
            ?: return null
    val days = ChronoUnit.DAYS.between(today, target).toInt()
    return if (days < 0) null else days
}
