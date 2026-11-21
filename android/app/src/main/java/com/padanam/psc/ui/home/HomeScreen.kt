package com.padanam.psc.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.model.ActiveExam
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.DarkCardButton
import com.padanam.psc.ui.components.DarkGradientCard
import com.padanam.psc.ui.components.FloatingTabBarSpace
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.LightButton
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.PadanamToggle
import com.padanam.psc.ui.components.PageHeader
import com.padanam.psc.ui.components.ProgressTrack
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.SetRow
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.theme.Amber
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.IconFaint
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoMuted
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.Orange
import com.padanam.psc.ui.theme.OrangeSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessBright
import com.padanam.psc.util.Format
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    appVm: AppViewModel,
    canScan: Boolean,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onOpenSet: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onResumeExam: () -> Unit,
    onOpenProfile: () -> Unit,
    onStartMode: (String, String) -> Unit,
    onOpenAttempt: (String) -> Unit
) {
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val sets by appVm.sets.collectAsStateWithLifecycle()
    val attempts by appVm.attempts.collectAsStateWithLifecycle()
    val exam by appVm.activeExam.collectAsStateWithLifecycle()
    val questionCount by appVm.questionCount.collectAsStateWithLifecycle()

    var remindersDialog by remember { mutableStateOf(false) }

    val firstName = settings.name.trim().substringBefore(' ').ifBlank { "there" }
    val newUser = sets.isEmpty() && attempts.isEmpty()
    val savedSet = settings.lastSavedSetId?.let { id -> sets.firstOrNull { it.id == id } }
    val showSaved = savedSet != null && exam == null &&
        System.currentTimeMillis() - settings.lastSavedAt < 7L * 24 * 60 * 60 * 1000
    val latestSet = sets.firstOrNull()
    val latestAttempt = attempts.firstOrNull()
    val streak = Format.attemptStreak(attempts)
    val answeredToday = Format.answeredOn(attempts, Format.today())
    val goal = settings.dailyGoal.coerceAtLeast(1)
    val goalPercent = (answeredToday * 100 / goal).coerceIn(0, 100)
    val due = sets.count { (it.bestScore ?: 0) < Format.PASS_PERCENT || it.attemptCount == 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Format.eyebrow(), style = AppType.Eyebrow, color = InkMuted)
                Text(
                    if (newUser) "Welcome, $firstName" else "${Format.greeting()}, $firstName",
                    style = AppType.HeroTitle.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceWhite)
                    .clickable { remindersDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = "Study reminders",
                    tint = Navy,
                    modifier = Modifier.size(18.dp)
                )
                if (settings.reminders) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Indigo)
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = FloatingTabBarSpace
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            val currentExam = exam
            if (currentExam != null) {
                item { ActiveExamCard(exam = currentExam, onResume = onResumeExam) }
            }

            if (showSaved && savedSet != null) {
                item {
                    SavedStrip(
                        title = savedSet.title,
                        questions = savedSet.questionCount,
                        onOpen = { onOpenSet(savedSet.id) }
                    )
                }
            }

            if (exam == null && canScan) {
                item {
                    ScanCard(
                        newUser = newUser,
                        setCount = sets.size,
                        onScan = onScan,
                        onImport = onImport
                    )
                }
            }

            if (newUser && exam == null) {
                item {
                    if (canScan) {
                        GettingStarted(
                            onScan = onScan,
                            onChooseFormat = onOpenProfile
                        )
                    } else {
                        AwaitingLibrary(onOpenProfile = onOpenProfile)
                    }
                }
            }

            if (!canScan && exam == null) {
                item {
                    TodayFocus(
                        newUser = newUser,
                        answeredToday = answeredToday,
                        goal = goal,
                        due = due
                    )
                }
            }

            item {
                ProgressTiles(
                    streak = streak,
                    answeredToday = answeredToday,
                    goal = goal,
                    goalPercent = goalPercent
                )
            }

            item {
                StudyModes(
                    questionCount = questionCount,
                    setCount = sets.size,
                    due = due,
                    onPractice = {
                        if (latestSet != null) onStartMode(latestSet.id, QuizMode.PRACTICE)
                        else onOpenLibrary()
                    },
                    onMock = {
                        if (latestSet != null) onStartMode(latestSet.id, QuizMode.EXAM)
                        else onOpenLibrary()
                    },
                    onReview = {
                        if (latestAttempt != null) onOpenAttempt(latestAttempt.id)
                        else onOpenLibrary()
                    }
                )
            }

            if (sets.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Recent captures",
                        action = "See all",
                        onAction = onOpenLibrary
                    )
                }
                items(sets.take(3), key = { it.id }) { summary ->
                    SetRow(
                        summary = summary,
                        isNew = summary.attemptCount == 0 &&
                            System.currentTimeMillis() - summary.createdAt < 6L * 60 * 60 * 1000,
                        onClick = { onOpenSet(summary.id) }
                    )
                }
            }

            item {
                InfoNote(
                    text = "Every scan becomes a saved set — question, its options and the answer.",
                    icon = Icons.Rounded.AutoAwesome
                )
            }
        }
    }

    if (remindersDialog) {
        AlertDialog(
            onDismissRequest = { remindersDialog = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Study reminder", style = AppType.Question, color = Navy)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A single daily nudge at 7pm helps keep the streak alive. You can change this any time.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily reminders", style = AppType.Label, color = Navy, modifier = Modifier.weight(1f))
                        PadanamToggle(
                            checked = settings.reminders,
                            onCheckedChange = {
                                appVm.setReminders(it)
                                remindersDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { remindersDialog = false }) {
                    Text("Done", style = AppType.ButtonSmall, color = Indigo)
                }
            }
        )
    }
}

private data class ExamClock(val elapsed: Int, val remaining: Int)

private fun clockFor(exam: ActiveExam): ExamClock {
    val elapsed = ((System.currentTimeMillis() - exam.startedAtEpochMs) / 1000)
        .toInt()
        .coerceAtLeast(0)
    val remaining = if (exam.mode == QuizMode.EXAM && exam.durationSeconds > 0) {
        (exam.durationSeconds - elapsed).coerceAtLeast(0)
    } else {
        0
    }
    return ExamClock(elapsed, remaining)
}

@Composable
private fun rememberClock(exam: ActiveExam): ExamClock {
    val clock by produceState(initialValue = clockFor(exam), exam.examId) {
        while (true) {
            value = clockFor(exam)
            delay(1_000)
        }
    }
    return clock
}

@Composable
private fun ActiveExamCard(exam: ActiveExam, onResume: () -> Unit) {
    val clock = rememberClock(exam)
    val isExam = exam.mode == QuizMode.EXAM
    val timerText = if (isExam) {
        if (clock.remaining > 0) "${Format.timer(clock.remaining)} left" else "Time is up"
    } else {
        "${Format.timer(clock.elapsed)} elapsed"
    }
    val progress = if (exam.total == 0) 0f else exam.answeredCount.toFloat() / exam.total
    val meta = if (isExam) {
        "${exam.subject} · ${exam.total} questions · ${Format.durationMinutes(exam.durationSeconds / 60)}"
    } else {
        "${exam.subject} · ${exam.total} questions · untimed"
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        DarkGradientCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(OnDark.copy(alpha = 0.1f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF8E7CFF)))
                    Text(
                        if (isExam) "Exam in progress" else "Practice in progress",
                        style = AppType.MonoChip,
                        color = OnDark.copy(alpha = 0.8f)
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(OnDark.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        null,
                        tint = Color(0xFFB4AAFF),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(timerText, style = AppType.MonoValue, color = Color(0xFFB4AAFF))
                }
            }

            Text(
                exam.title,
                style = AppType.HeroTitle,
                color = OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(meta.uppercase(), style = AppType.Eyebrow.copy(letterSpacing = 0.4.sp), color = OnDark.copy(alpha = 0.5f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OnDark.copy(alpha = 0.07f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Question ${(exam.currentIndex + 1).coerceAtMost(exam.total)} of ${exam.total}",
                        style = AppType.Label.copy(fontWeight = FontWeight.Medium),
                        color = OnDark
                    )
                    Text(
                        "${exam.answeredCount} answered",
                        style = AppType.MonoSmall,
                        color = OnDark.copy(alpha = 0.5f)
                    )
                }
                ProgressTrack(
                    progress = progress,
                    track = OnDark.copy(alpha = 0.12f),
                    fill = Color(0xFF8E7CFF)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DarkCardButton(
                    text = if (isExam) "Resume exam" else "Resume practice",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    filled = true
                )
                DarkCardButton(
                    text = "Flagged ${exam.flagged.size}",
                    icon = Icons.Rounded.Flag,
                    onClick = onResume
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.CloudDone,
                null,
                tint = InkMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                "Answers autosaved · ${Format.relativeTime(exam.lastSavedAtEpochMs)}",
                style = AppType.MonoSmall.copy(letterSpacing = 0.1.sp),
                color = InkMuted
            )
        }
    }
}

@Composable
private fun SavedStrip(title: String, questions: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IndigoSoft)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Indigo),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, null, tint = OnDark, modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$title saved",
                style = AppType.LabelSmallStrong,
                color = IndigoText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$questions question${if (questions == 1) "" else "s"} · just now",
                style = AppType.MonoSmall,
                color = IndigoMuted
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Indigo, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ScanCard(
    newUser: Boolean,
    setCount: Int,
    onScan: () -> Unit,
    onImport: () -> Unit
) {
    DarkGradientCard(corner = 26.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(OnDark.copy(alpha = 0.1f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Rounded.DocumentScanner,
                    null,
                    tint = OnDark,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    if (newUser) "Start here" else "AI capture",
                    style = AppType.MonoChip,
                    color = OnDark
                )
            }
            Text(
                when {
                    newUser -> "FIRST SCAN FREE"
                    setCount == 0 -> "READY WHEN YOU ARE"
                    else -> "$setCount SETS SAVED"
                },
                style = AppType.MonoSmall.copy(letterSpacing = 0.5.sp),
                color = OnDark.copy(alpha = 0.5f)
            )
        }

        Text(
            if (newUser) "Your first set\nstarts with a photo." else "Photograph a question.\nGet every option.",
            style = AppType.HeroTitle,
            color = OnDark
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DarkCardButton(
                text = if (newUser) "Scan first question" else "Scan question",
                icon = Icons.Rounded.CameraAlt,
                onClick = onScan,
                modifier = Modifier.weight(1f),
                filled = true
            )
            DarkCardButton(
                text = "Upload",
                icon = Icons.Rounded.Image,
                onClick = onImport
            )
        }
    }
}

@Composable
private fun TodayFocus(
    newUser: Boolean,
    answeredToday: Int,
    goal: Int,
    due: Int
) {
    val remaining = (goal - answeredToday).coerceAtLeast(0)
    val heading: String
    val message: String
    val icon: ImageVector
    when {
        newUser -> {
            heading = "Your first set is one photo away"
            message = "Scan a page or open the library to start your first practice set."
            icon = Icons.Rounded.DocumentScanner
        }
        answeredToday >= goal -> {
            heading = "Daily goal complete"
            message = "You answered $answeredToday questions today. A short review keeps the streak alive."
            icon = Icons.Rounded.CheckCircle
        }
        due > 0 -> {
            heading = "$due ${if (due == 1) "set needs" else "sets need"} a pass"
            message = "$remaining more questions will finish today's goal."
            icon = Icons.Rounded.Flag
        }
        else -> {
            heading = "Keep the momentum"
            message = "$remaining more questions will finish today's goal."
            icon = Icons.Rounded.AutoAwesome
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(OrangeSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Orange, modifier = Modifier.size(18.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "TODAY'S FOCUS",
                style = AppType.MonoSmall.copy(letterSpacing = 0.9.sp),
                color = IndigoMuted
            )
            Text(heading, style = AppType.SectionTitle, color = Navy)
            Text(message, style = AppType.BodySmall, color = InkMuted)
        }
    }
}

@Composable
private fun ProgressTiles(
    streak: Int,
    answeredToday: Int,
    goal: Int,
    goalPercent: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .width(122.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceWhite)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OrangeSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    null,
                    tint = Orange,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "$streak",
                    style = AppType.StatValue.copy(fontSize = 26.sp, lineHeight = 30.sp),
                    color = Navy
                )
                Text(
                    if (streak == 1) "day streak" else "day streak",
                    style = AppType.BodySmall.copy(fontSize = 11.sp),
                    color = InkMuted
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(IndigoSoft)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Daily goal",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.9.sp),
                    color = IndigoMuted
                )
                Text("$goalPercent%", style = AppType.MonoValue.copy(fontSize = 9.sp), color = Indigo)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "$answeredToday of $goal",
                    style = AppType.SectionTitle,
                    color = IndigoText
                )
                Text(
                    "questions answered today",
                    style = AppType.BodyTiny,
                    color = IndigoMuted
                )
            }
            ProgressTrack(
                progress = goalPercent / 100f,
                track = OnDark.copy(alpha = 0.7f),
                fill = Indigo
            )
        }
    }
}

@Composable
private fun StudyModes(
    questionCount: Int,
    setCount: Int,
    due: Int,
    onPractice: () -> Unit,
    onMock: () -> Unit,
    onReview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        ModeButton(
            icon = Icons.Rounded.CenterFocusStrong,
            label = "Practice",
            meta = "${Format.number(questionCount)} Q",
            onClick = onPractice,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            icon = Icons.Rounded.Timer,
            label = "Mock exam",
            meta = "$setCount sets",
            onClick = onMock,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            icon = Icons.Rounded.Replay,
            label = "Review",
            meta = "$due due",
            onClick = onReview,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Indigo, modifier = Modifier.size(19.dp))
        }
        Text("$label", style = AppType.LabelSmallStrong, color = Navy)
        Text(meta, style = AppType.MonoMeta, color = InkMuted)
    }
}

@Composable
private fun GettingStarted(
    onScan: () -> Unit,
    onChooseFormat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Getting started", style = AppType.SectionTitle, color = Navy)
            Text("1 OF 3", style = AppType.MonoSmall, color = InkMuted)
        }
        GettingStartedStep(
            label = "Choose your question format",
            state = StepState.DONE,
            onClick = onChooseFormat
        )
        GettingStartedStep(
            label = "Scan your first question",
            state = StepState.ACTIVE,
            onClick = onScan
        )
        GettingStartedStep(
            label = "Take a 10-question practice",
            state = StepState.PENDING,
            onClick = onScan
        )
        ThinDivider()
        InfoNote(
            text = "English and Malayalam are captured together. Switch language any time.",
            icon = Icons.Rounded.AutoAwesome
        )
    }
}

@Composable
private fun AwaitingLibrary(onOpenProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your library is empty", style = AppType.SectionTitle, color = Navy)
        Text(
            "Your administrator assigns question sets to this account. Pull down on the " +
                "library or press Sync now in Settings to check for new sets.",
            style = AppType.BodySmall,
            color = InkMuted
        )
        ThinDivider()
        InfoNote(
            text = "You can practise and take mock exams as soon as a set arrives. " +
                "Scanning and library editing stay with the administrator.",
            icon = Icons.Rounded.AutoAwesome
        )
        LightButton(
            text = "Open profile",
            onClick = onOpenProfile,
            icon = Icons.Rounded.Person,
            container = IndigoSoft,
            tint = Indigo,
            height = 46.dp
        )
    }
}
private enum class StepState { DONE, ACTIVE, PENDING }

@Composable
private fun GettingStartedStep(
    label: String,
    state: StepState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        StepState.DONE -> IndigoSoft
                        StepState.ACTIVE -> Indigo
                        StepState.PENDING -> SurfaceMuted
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                StepState.DONE -> Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = Indigo,
                    modifier = Modifier.size(13.dp)
                )
                StepState.ACTIVE -> Icon(
                    Icons.Rounded.DocumentScanner,
                    null,
                    tint = OnDark,
                    modifier = Modifier.size(13.dp)
                )
                StepState.PENDING -> Icon(
                    Icons.Rounded.RadioButtonUnchecked,
                    null,
                    tint = IconFaint,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            label,
            style = AppType.LabelSmallStrong,
            color = if (state == StepState.PENDING) InkMuted else Navy,
            modifier = Modifier.weight(1f)
        )
        if (state == StepState.ACTIVE) {
            Icon(Icons.Rounded.ChevronRight, null, tint = Indigo, modifier = Modifier.size(18.dp))
        }
    }
}
