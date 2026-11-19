package com.padanam.psc.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.padanam.psc.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the last uncaught exception in app-private storage and shows it on the
 * next launch. Phones rarely have USB debugging enabled, so this is the only
 * practical way to get a real stack trace back from a device.
 *
 * Nothing is uploaded automatically; the report leaves the phone only when
 * the user taps Share or Copy on the crash screen.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 64_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun report(context: Context): String? = try {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Padanam crash report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Share crash report"))
        }
    }

    fun copy(context: Context, report: String) {
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
                ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText("Padanam crash report", report))
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()

        val version = runCatching { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }
            .getOrDefault("unknown")
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())

        val report = buildString {
            appendLine("Padanam crash report")
            appendLine("Time: $stamp")
            appendLine("Version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }.take(MAX_CHARS)

        File(context.filesDir, FILE_NAME).writeText(report)
    }
}
