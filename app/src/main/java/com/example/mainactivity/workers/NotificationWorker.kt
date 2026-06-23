package com.example.mainactivity.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mainactivity.MainActivity
import com.example.mainactivity.data.BirthdayEntity
import com.example.mainactivity.data.CalendarEventEntity
import com.example.mainactivity.data.FamilyRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val CHANNEL_BIRTHDAYS = "channel_birthdays"
private const val CHANNEL_CALENDAR = "channel_calendar"
const val NOTIFICATION_WORK_NAME = "family_notifications"

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = FamilyRepository.get(applicationContext)
        if (!repo.notificationsEnabled.first()) return Result.success()

        val userId = repo.currentUserId.first() ?: return Result.success()
        val daysBefore = repo.notifyDaysBefore.first()
        val today = LocalDate.now()

        createChannels()

        val user = repo.userDao.findById(userId) ?: return Result.success()
        val memberIds: List<Long> = if (user.familyId != null) {
            repo.userDao.membersOfFamily(user.familyId).first().map { it.id }
        } else {
            listOf(userId)
        }

        repo.birthdayDao.birthdaysFor(user.familyId, userId).first().forEach { b ->
            val daysUntil = daysUntilRecurring(b.date, today) ?: return@forEach
            if (daysUntil == 0 || (daysBefore > 0 && daysUntil == daysBefore)) {
                postBirthdayNotification(b, daysUntil)
            }
        }

        repo.calendarDao.eventsForUsers(memberIds).first().forEach { e ->
            val daysUntil = daysUntilOneTime(e.dateFrom, today) ?: return@forEach
            if (daysUntil == 0 || (daysBefore > 0 && daysUntil == daysBefore)) {
                postEventNotification(e, daysUntil)
            }
        }

        return Result.success()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BIRTHDAYS, "Birthdays", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Birthday reminders for family members" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALENDAR, "Calendar events", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Reminders for upcoming calendar events" }
        )
    }

    private fun tapIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun postBirthdayNotification(b: BirthdayEntity, daysUntil: Int) {
        val title = if (daysUntil == 0) "${b.name}'s birthday is today!"
        else "${b.name}'s birthday is in $daysUntil day${if (daysUntil == 1) "" else "s"}"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            (b.id.toInt() * 2),
            NotificationCompat.Builder(applicationContext, CHANNEL_BIRTHDAYS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(tapIntent())
                .build()
        )
    }

    private fun postEventNotification(e: CalendarEventEntity, daysUntil: Int) {
        val title = if (daysUntil == 0) "${e.activity} is today!"
        else "${e.activity} is in $daysUntil day${if (daysUntil == 1) "" else "s"}"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            (e.id.toInt() * 2 + 1),
            NotificationCompat.Builder(applicationContext, CHANNEL_CALENDAR)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(tapIntent())
                .build()
        )
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(nextNineAmDelayMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NOTIFICATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOTIFICATION_WORK_NAME)
        }

        private fun nextNineAmDelayMillis(): Long {
            val now = LocalDateTime.now()
            val nineAmToday = now.toLocalDate().atTime(9, 0)
            val nineAm = if (now.isBefore(nineAmToday)) nineAmToday else nineAmToday.plusDays(1)
            return Duration.between(now, nineAm).toMillis().coerceAtLeast(0L)
        }
    }
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun daysUntilRecurring(dateStr: String, today: LocalDate): Int? {
    val monthDay = try {
        MonthDay.parse(dateStr.trim(), DATE_FORMATTER)
    } catch (_: Exception) { return null }
    var target = monthDay.atYear(today.year)
    if (target < today.minusDays(1)) target = monthDay.atYear(today.year + 1)
    return ChronoUnit.DAYS.between(today, target).toInt()
}

fun daysUntilOneTime(dateStr: String, today: LocalDate): Int? {
    val target = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull()
        ?: runCatching { MonthDay.parse(dateStr.trim(), DATE_FORMATTER).atYear(today.year) }.getOrNull()
        ?: return null
    val days = ChronoUnit.DAYS.between(today, target).toInt()
    return if (days < 0) null else days
}
