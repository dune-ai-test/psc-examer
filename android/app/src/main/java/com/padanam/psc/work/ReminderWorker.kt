package com.padanam.psc.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.padanam.psc.MainActivity
import com.padanam.psc.R
import com.padanam.psc.data.prefs.ExamStore
import com.padanam.psc.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = runCatching { SettingsRepository(applicationContext).current() }.getOrNull()
            ?: return Result.success()
        if (!settings.reminders) return Result.success()

        val activeExam = runCatching { ExamStore(applicationContext).activeExam.first() }.getOrNull()
        val title: String
        val body: String
        if (activeExam != null) {
            title = "Your exam is still running"
            body = "${activeExam.title} · ${activeExam.answeredCount} of ${activeExam.total} answered. Tap to resume."
        } else {
            title = "Keep your ${settings.name} streak alive"
            body = "A 10-question practice takes about 5 minutes. Your daily goal is ${settings.dailyGoal} questions."
        }

        val launch = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            }
        }
        return Result.success()
    }

    companion object {
        const val NOTIFICATION_ID = 4210
    }
}

object NotificationHelper {
    const val CHANNEL_ID = "padanam_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Study reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "A gentle daily nudge and a reminder when an exam timer is still running."
        }
        manager.createNotificationChannel(channel)
    }
}
