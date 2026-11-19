package com.padanam.psc

import android.app.Application
import com.padanam.psc.util.CrashReporter
import com.padanam.psc.work.NotificationHelper
import com.padanam.psc.work.ReminderScheduler
import kotlinx.coroutines.launch

class PadanamApp : Application() {

    /**
     * Null while the app is showing a crash report. Building the container
     * starts the database and preference stores, which are exactly the pieces
     * that may have failed; the report screen must not depend on them.
     */
    var container: AppContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        /* Install this before anything else can fail. The next launch shows
           the captured stack trace instead of losing it to the system dialog. */
        CrashReporter.install(this)

        if (CrashReporter.report(this) != null) return

        val created = AppContainer(this)
        container = created

        /* Startup must never crash the app. A restored backup or a device
           without notification permission should not stop the library from
           opening; both helpers are best-effort. */
        runCatching { NotificationHelper.createChannel(this) }
        created.appScope.launch {
            runCatching {
                val settings = created.settingsRepository.current()
                ReminderScheduler.schedule(this@PadanamApp, settings.reminders)
            }
        }
    }

    /** Builds the container on first use, including from the crash screen. */
    fun openContainer(): AppContainer =
        container ?: AppContainer(this).also { container = it }
}
