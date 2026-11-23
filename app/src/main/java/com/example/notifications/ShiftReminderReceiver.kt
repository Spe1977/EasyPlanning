package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class ShiftReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val title = intent.getStringExtra(ShiftReminderScheduler.EXTRA_TITLE) ?: return
        val timeRange = intent.getStringExtra(ShiftReminderScheduler.EXTRA_TIME_RANGE).orEmpty()
        val notes = intent.getStringExtra(ShiftReminderScheduler.EXTRA_NOTES).orEmpty()
        val notificationId = intent.getIntExtra(ShiftReminderScheduler.EXTRA_NOTIFICATION_ID, 1001)

        ShiftReminderScheduler(context).ensureNotificationChannel()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (notes.isNotBlank()) "$timeRange · $notes" else timeRange

        val notification = NotificationCompat.Builder(context, ShiftReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shift)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
