package com.fitcal.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fitcal.app.MainActivity
import com.fitcal.app.ads.AndroidAdManager
import com.fitcal.app.appContext
import java.util.Calendar

class MealReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                MealReminderManager.syncNotifications()
            }
            ACTION_SHOW_MEAL_REMINDER -> {
                val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: "meal"
                val label = intent.getStringExtra(EXTRA_REMINDER_LABEL) ?: "Meal"
                val hour = intent.getIntExtra(EXTRA_REMINDER_HOUR, 12)
                val minute = intent.getIntExtra(EXTRA_REMINDER_MINUTE, 0)
                val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)

                showNotification(context, reminderId, label, slotIndex)
                // Re-schedule for tomorrow at the same hour/minute
                scheduleSingleAlarm(context, slotIndex, reminderId, label, hour, minute)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "fitcal_meal_reminders"
        private const val ACTION_SHOW_MEAL_REMINDER = "com.fitcal.app.ACTION_SHOW_MEAL_REMINDER"
        private const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val EXTRA_REMINDER_LABEL = "extra_reminder_label"
        private const val EXTRA_REMINDER_HOUR = "extra_reminder_hour"
        private const val EXTRA_REMINDER_MINUTE = "extra_reminder_minute"
        private const val EXTRA_SLOT_INDEX = "extra_slot_index"
        private const val BASE_REQUEST_CODE = 24000
        private const val MAX_REMINDER_SLOTS = 32

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Meal Scan Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily reminders around meal times to scan your food and track macros."
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun requestPermissionIfNeeded() {
            if (Build.VERSION.SDK_INT >= 33) {
                val activity = AndroidAdManager.currentActivityRef?.get() ?: return
                val granted = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        BASE_REQUEST_CODE
                    )
                }
            }
        }

        fun syncAlarms(context: Context, masterEnabled: Boolean, reminders: List<MealReminder>) {
            ensureChannel(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            // Cancel all existing slots first
            for (slot in 0 until MAX_REMINDER_SLOTS) {
                val cancelIntent = Intent(context, MealReminderReceiver::class.java).apply {
                    action = ACTION_SHOW_MEAL_REMINDER
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    BASE_REQUEST_CODE + slot,
                    cancelIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pending != null) {
                    alarmManager.cancel(pending)
                    pending.cancel()
                }
            }

            if (!masterEnabled) return

            val activeReminders = reminders.filter { it.enabled }.take(MAX_REMINDER_SLOTS)
            if (activeReminders.isNotEmpty()) {
                requestPermissionIfNeeded()
            }

            activeReminders.forEachIndexed { index, reminder ->
                scheduleSingleAlarm(
                    context = context,
                    slotIndex = index,
                    reminderId = reminder.id,
                    label = reminder.label,
                    hour = reminder.hour,
                    minute = reminder.minute
                )
            }
        }

        private fun scheduleSingleAlarm(
            context: Context,
            slotIndex: Int,
            reminderId: String,
            label: String,
            hour: Int,
            minute: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, MealReminderReceiver::class.java).apply {
                action = ACTION_SHOW_MEAL_REMINDER
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_REMINDER_LABEL, label)
                putExtra(EXTRA_REMINDER_HOUR, hour)
                putExtra(EXTRA_REMINDER_MINUTE, minute)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + slotIndex,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
        }

        private fun showNotification(
            context: Context,
            reminderId: String,
            label: String,
            slotIndex: Int
        ) {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) return
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_meal_reminder", reminderId)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                BASE_REQUEST_CODE + 100 + slotIndex,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (title, body) = MealReminderManager.getNotificationContent(label)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(BASE_REQUEST_CODE + slotIndex, notification)
            } catch (_: SecurityException) {
                // Ignore if notification permission was revoked
            }
        }
    }
}
