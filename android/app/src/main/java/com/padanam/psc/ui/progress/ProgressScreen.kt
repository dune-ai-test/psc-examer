package com.padanam.psc.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.AttemptRow
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.FloatingTabBarSpace
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.PageHeader
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.ProgressTrack
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.StatTile
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.theme.Amber
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoBar
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.Sky
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.TrackSoft
import com.padanam.psc.util.AppJson
import com.padanam.psc.util.Format
import java.time.temporal.ChronoUnit
import kotlinx.serialization.builtins.ListSerializer

private data class SubjectStat(val name: String, val total: Int, val correct: Int, val percent: Int)
private data class TopicStat(val name: String, val total: Int, val correct: Int, val percent: Int)
private data class DayBar(val label: String, val count: Int, val today: Boolean)

@Composable
fun ProgressScreen(
    appVm: AppViewModel,
    onOpenAttempt: (String) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val attempts by appVm.attempts.collectAsStateWithLifecycle()
    val allAnswers = remember(attempts) { decodeAnswers(attempts) }
    var calendarOpen by remember { mutableStateOf(false) }

    val totalQuestions = attempts.sumOf { it.total }
    val totalCorrect = attempts.sumOf { it.correct }
    val accuracy = if (totalQuestions == 0) 0 else Math.round(totalCorrect * 100f / totalQuestions)

    val now = System.currentTimeMillis()
    val recent = attempts.filter { now - it.completedAt <= 7L * 24 * 60 * 60 * 1000 }
    val previous = attempts.filter {
        val age = now - it.completedAt
        age > 7L * 24 * 60 * 60 * 1000 && age <= 14L * 24 * 60 * 60 * 1000
    }
    val delta = if (recent.isEmpty() || previous.isEmpty()) null else
        accuracyOf(recent) - accuracyOf(previous)

    val bars = remember(attempts) { weekBars(attempts) }
    val best = attempts.maxByOrNull { it.scorePercent }
    val avgTime = Format.averagePerQuestion(attempts)
    val subjects = attempts.groupBy { it.subject.ifBlank { "General" } }
        .map { (name, rows) ->
            val total = rows.sumOf { it.total }
            val correct = rows.sumOf { it.correct }
            SubjectStat(name, total, correct, if (total == 0) 0 else Math.round(correct * 100f / total))
        }
        .sortedByDescending { it.total }
        .take(6)
    val topics = allAnswers.filter { !it.isSkipped }
        .groupBy { it.topic.ifBlank { "General" } }
        .map { (name, rows) ->
            val correct = rows.count { it.isCorrect }
            TopicStat(name, rows.size, correct, Math.round(correct * 100f / rows.size))
        }
    val focus = topics.minWithOrNull(compareBy<TopicStat> { it.percent }.thenByDescending { it.total })
    val studyDays = attempts.map { Format.localDate(it.completedAt) }.distinct()
        .count { ChronoUnit.DAYS.between(it, Format.today()) < 30 }
    val streak = Format.attemptStreak(attempts)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = FloatingTabBarSpace),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                PageHeader(eyebrow = "Last 30 days", title = "Progress") {
                    CircleAction(
                        icon = Icons.Rounded.CalendarMonth,
                        contentDescription = "Study calendar",
                        onClick = { calendarOpen = true },
                        size = 40.dp
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ACCURACY", style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp), color = InkMuted)
                        DeltaChip(delta)
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "$accuracy%",
                            style = AppType.StatValue.copy(fontSize = 46.sp, lineHeight = 46.sp),
                            color = Navy
                        )
                        Text(
                            "of ${Format.number(totalQuestions)} answers",
                            style = AppType.BodySmall,
                            color = InkMuted,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "ANSWERS / DAY",
                                style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                                color = InkMuted
                            )
                            Text("LAST 7 DAYS", style = AppType.MonoSmall, color = InkMuted)
                        }
                        val max = bars.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            bars.forEach { bar ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    val height = if (bar.count == 0) 4.dp else
                                        (10 + 44 * (bar.count.toFloat() / max)).dp
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .height(height)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    bar.count == 0 -> TrackSoft
                                                    bar.today -> Indigo
                                                    else -> IndigoBar
                                                }
                                            )
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bars.forEach { bar ->
                                Text(
                                    bar.label,
                                    style = AppType.MonoSmall,
                                    color = if (bar.today) Indigo else InkMuted,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        ThinDivider()
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatTile(
                        label = "Best score",
                        value = if (best != null) "${best.scorePercent}%" else "—",
                        note = best?.setTitle ?: "No attempts yet",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Avg time",
                        value = if (avgTime > 0) "${avgTime}s" else "—",
                        note = "per question",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (attempts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Insights,
                        title = "Your progress starts here",
                        body = "Finish a practice set or a mock exam and your accuracy, timing and weak topics will appear on this page.",
                        primaryLabel = "Browse your library",
                        onPrimary = onOpenLibrary,
                        primaryIcon = Icons.Rounded.CenterFocusStrong
                    )
                }
                item {
                    InfoNote(text = "Every submitted attempt is saved with its score, so you can always come back to the exact paper.")
                }
            } else {
                item {
                    SectionHeading(title = "By subject", meta = "${subjects.size} subjects")
                }
                items(subjects, key = { it.name }) { subject ->
                    SubjectRow(subject)
                }

                item {
                    val focusTitle = if (focus != null) "Focus next: ${focus.name}" else "Focus next: keep practising"
                    val focusMeta = if (focus != null) {
                        "${focus.total} questions · ${focus.percent}% accuracy · tap to browse"
                    } else {
                        "Your weak topics will appear after a few attempts"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(IndigoSoft)
                            .clickable(onClick = onOpenLibrary)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CenterFocusStrong,
                                null,
                                tint = Indigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                focusTitle,
                                style = AppType.SectionTitle.copy(fontSize = 14.sp),
                                color = IndigoText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(focusMeta, style = AppType.BodyTiny, color = InkMuted)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Indigo, modifier = Modifier.size(18.dp))
                    }
                }

                item {
                    SectionHeading(title = "Recent attempts", meta = "${attempts.size} total")
                }
                items(attempts.take(5), key = { it.id }) { attempt ->
                    AttemptRow(attempt = attempt, onClick = { onOpenAttempt(attempt.id) })
                }

                item {
                    InfoNote(text = "Accuracy is calculated across every question you have submitted on this device and in Neon.")
                }
            }
        }
    }

    if (calendarOpen) {
        AlertDialog(
            onDismissRequest = { calendarOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Study calendar", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A quiet overview of the last 30 days.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    CalendarStat("Current streak", "$streak days")
                    CalendarStat("Active days", "$studyDays of 30")
                    CalendarStat("Attempts", "${attempts.size}")
                    CalendarStat("Questions answered", Format.number(totalQuestions))
                    CalendarStat("Overall accuracy", "$accuracy%")
                }
            },
            confirmButton = {
                TextButton(onClick = { calendarOpen = false }) {
                    Text("Close", style = AppType.ButtonSmall, color = Indigo)
                }
            }
        )
    }
}

@Composable
private fun CalendarStat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = AppType.BodySmall, color = InkMuted)
        Text(value, style = AppType.MonoValue, color = Navy)
    }
}

@Composable
private fun DeltaChip(delta: Int?) {
    val positive = (delta ?: 0) >= 0
    val background = if (delta == null || positive) SuccessSoft else AmberSoft
    val foreground = if (delta == null || positive) Success else Amber
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            delta == null -> Icon(
                Icons.Rounded.TrendingUp,
                null,
                tint = foreground,
                modifier = Modifier.size(12.dp)
            )
            positive -> Icon(
                Icons.Rounded.TrendingUp,
                null,
                tint = foreground,
                modifier = Modifier.size(12.dp)
            )
            else -> Icon(
                Icons.Rounded.TrendingDown,
                null,
                tint = foreground,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            when {
                delta == null -> "NEW"
                delta > 0 -> "+$delta pts"
                delta == 0 -> "STEADY"
                else -> "$delta pts"
            },
            style = AppType.MonoChip,
            color = foreground
        )
    }
}

@Composable
private fun SubjectRow(subject: SubjectStat) {
    val palette = listOf(Indigo, Success, Amber, Sky, Color(0xFFF97316), Danger)
    val color = palette[Math.floorMod(subject.name.hashCode(), palette.size)]
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    subject.name,
                    style = AppType.LabelSmallStrong,
                    color = Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${subject.total} Q", style = AppType.MonoSmall, color = InkMuted)
            }
            Text("${subject.percent}%", style = AppType.MonoValue, color = color)
        }
        ProgressTrack(progress = subject.percent / 100f, fill = color, height = 6.dp)
    }
}

private fun accuracyOf(attempts: List<AttemptEntity>): Int {
    val total = attempts.sumOf { it.total }
    val correct = attempts.sumOf { it.correct }
    return if (total == 0) 0 else Math.round(correct * 100f / total)
}

private fun decodeAnswers(attempts: List<AttemptEntity>): List<AnswerRecord> =
    attempts.flatMap { attempt ->
        runCatching {
            AppJson.decodeFromString(
                ListSerializer(AnswerRecord.serializer()),
                attempt.answersJson
            )
        }.getOrDefault(emptyList())
    }

private fun weekBars(attempts: List<AttemptEntity>): List<DayBar> {
    val today = Format.today()
    return (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val count = attempts
            .filter { Format.localDate(it.completedAt) == date }
            .sumOf { it.total.coerceAtLeast(1) }
        DayBar(Format.dayShort(date), count, offset == 0)
    }
}
