package com.padanam.psc.util

import com.padanam.psc.data.local.AttemptEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Format {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clockFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
    private val eyebrowFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val shortDateYearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val fileStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ENGLISH)

    fun clockNow(epochMs: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(clockFormatter)

    fun eyebrow(epochMs: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(eyebrowFormatter).uppercase(Locale.ENGLISH)

    fun greeting(epochMs: Long = System.currentTimeMillis()): String {
        val hour = Instant.ofEpochMilli(epochMs).atZone(zone).hour
        return when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "P"
            parts.size == 1 -> parts.first().take(2).uppercase(Locale.ENGLISH)
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase(Locale.ENGLISH)
        }
    }

    /** 42:18 or 1:04:33, suitable for a running clock. */
    fun timer(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) {
            String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ENGLISH, "%02d:%02d", m, s)
        }
    }

    fun duration(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m > 0 -> "${m} min"
            else -> "${safe}s"
        }
    }

    fun durationMinutes(minutes: Int): String = when {
        minutes <= 0 -> "No limit"
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
        minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "$minutes min"
    }

    fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return "never"
        val diff = (now - epochMs).coerceAtLeast(0L)
        val minutes = diff / 60_000
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h ago"

        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days == 1L -> "yesterday"
            days < 7L -> "$days days ago"
            days < 365L -> date.format(shortDateFormatter)
            else -> date.format(shortDateYearFormatter)
        }
    }

    fun dateShort(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return Instant.ofEpochMilli(epochMs).atZone(zone).format(shortDateFormatter)
    }

    fun examDateLabel(value: String): String =
        runCatching { LocalDate.parse(value).format(shortDateYearFormatter) }.getOrElse { value }

    fun daysUntil(value: String): Long? =
        runCatching { ChronoUnit.DAYS.between(today(), LocalDate.parse(value)) }.getOrNull()

    fun exportStamp(): String =
        Instant.now().atZone(zone).format(fileStampFormatter)

    fun fileDate(epochMs: Long): String {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
        return zoned.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))
    }

    fun dayShort(date: LocalDate): String =
        date.dayOfWeek.name.take(1).uppercase(Locale.ENGLISH)

    fun localDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun today(): LocalDate = LocalDate.now(zone)

    fun dayIndex(epochMs: Long): Int = localDate(epochMs).dayOfWeek.value - 1

    fun number(value: Int): String = String.format(Locale.ENGLISH, "%,d", value)

    fun percent(value: Int): String = "$value%"

    const val PASS_PERCENT = 40

    fun passMark(total: Int): Int = kotlin.math.ceil(total * PASS_PERCENT / 100.0).toInt()

    fun averagePerQuestion(attempts: List<AttemptEntity>): Int {
        val totalQuestions = attempts.sumOf { it.total }
        val totalSeconds = attempts.sumOf { it.durationSeconds }
        return if (totalQuestions <= 0) 0 else (totalSeconds / totalQuestions)
    }

    fun attemptStreak(attempts: List<AttemptEntity>): Int {
        if (attempts.isEmpty()) return 0
        val days = attempts.map { localDate(it.completedAt) }.toSet()
        var cursor = today()
        if (cursor !in days) cursor = cursor.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun answeredOn(attempts: List<AttemptEntity>, date: LocalDate): Int =
        attempts.filter { localDate(it.completedAt) == date }.sumOf { it.total }
}
