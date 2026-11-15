package com.padanam.psc.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME = "padanam_daily_reminder"

    fun schedule(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val nextEvening = nextReminderTime()
        val delayMinutes = Duration.between(LocalDateTime.now(), nextEvening).toMinutes()
            .coerceAtLeast(15)
            .coerceAtMost(24 * 60)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun nextReminderTime(): LocalDateTime {
        val now = LocalDateTime.now()
        var target = now.with(LocalTime.of(19, 30))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target
    }
}
