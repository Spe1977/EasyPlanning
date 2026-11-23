package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.example.R
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.domain.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules upcoming shift reminder notifications over a rolling 30-day window using
 * AlarmManager.setAndAllowWhileIdle (no restricted SCHEDULE_EXACT_ALARM permission required).
 */
class ShiftReminderScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "easyplanning_shift_reminders"
        const val EXTRA_TITLE = "extra_shift_title"
        const val EXTRA_TIME_RANGE = "extra_shift_time_range"
        const val EXTRA_NOTES = "extra_shift_notes"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val PREF_SCHEDULED_IDS = "easyplanning_alarm_ids"
        private const val KEY_IDS = "ids"
        private const val MAX_SCHEDULED_ALARMS = 60
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_shifts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_shifts_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun rescheduleAll(
        seriesList: List<ShiftSeriesEntity>,
        overrides: List<ShiftOverrideEntity>
    ) {
        ensureNotificationChannel()
        cancelAllScheduled()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val today = LocalDate.now()
        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val upcoming = RecurrenceEngine.expandRange(
            seriesList = seriesList,
            overrides = overrides,
            rangeStart = today,
            rangeEnd = today.plusDays(30)
        )

        val scheduledIds = mutableSetOf<String>()
        var count = 0

        for (shift in upcoming) {
            if (count >= MAX_SCHEDULED_ALARMS) break
            if (shift.reminderMinutesBefore < 0) continue

            val shiftStart = LocalDateTime.of(shift.date, shift.startTime)
            val triggerTime = shiftStart.minusMinutes(shift.reminderMinutesBefore.toLong())
            val triggerMillis = triggerTime.atZone(zone).toInstant().toEpochMilli()
            if (triggerMillis <= nowMillis) continue

            val requestCode = ((shift.seriesId * 997L + shift.date.toEpochDay()) and 0x7FFFFFFFL).toInt()
            val intent = Intent(context, ShiftReminderReceiver::class.java).apply {
                putExtra(EXTRA_TITLE, shift.title)
                putExtra(EXTRA_TIME_RANGE, shift.formattedTimeRange())
                putExtra(EXTRA_NOTES, shift.notes)
                putExtra(EXTRA_NOTIFICATION_ID, requestCode)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            scheduledIds += requestCode.toString()
            count++
        }

        context.getSharedPreferences(PREF_SCHEDULED_IDS, Context.MODE_PRIVATE).edit {
            putStringSet(KEY_IDS, scheduledIds)
        }
    }

    fun cancelAllScheduled() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences(PREF_SCHEDULED_IDS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        for (idStr in existing) {
            val reqCode = idStr.toIntOrNull() ?: continue
            val intent = Intent(context, ShiftReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        prefs.edit { remove(KEY_IDS) }
    }
}
