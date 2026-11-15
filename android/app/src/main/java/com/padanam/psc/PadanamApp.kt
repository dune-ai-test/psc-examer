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
        NotificationHelper.createChannel(this)
        container.appScope.launch {
            val settings = runCatching { container.settingsRepository.current() }.getOrNull() ?: return@launch
            ReminderScheduler.schedule(this@PadanamApp, settings.reminders)
        }
    }
}
