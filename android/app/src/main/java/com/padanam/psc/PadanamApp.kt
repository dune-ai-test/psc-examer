package com.padanam.psc

import android.app.Application
import com.padanam.psc.work.NotificationHelper
import com.padanam.psc.work.ReminderScheduler
import kotlinx.coroutines.launch

class PadanamApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        /* Startup must never crash the app. A restored backup or a device
           without notification permission should not stop the library from
           opening; both helpers are best-effort. */
        runCatching { NotificationHelper.createChannel(this) }
        container.appScope.launch {
            runCatching {
                val settings = container.settingsRepository.current()
                ReminderScheduler.schedule(this@PadanamApp, settings.reminders)
            }
        }
    }
}
