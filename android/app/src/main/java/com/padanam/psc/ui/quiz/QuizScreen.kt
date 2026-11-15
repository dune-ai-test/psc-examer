package com.padanam.psc.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.padanam.psc.AppContainer
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.LanguageSwitch
import com.padanam.psc.ui.components.LegendDot
import com.padanam.psc.ui.components.OptionPill
import com.padanam.psc.ui.components.OptionVisual
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.ProgressTrack
import com.padanam.psc.ui.components.QuestionText
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.Amber
import com.padanam.psc.ui.theme.AmberDeep
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Hairline
import com.padanam.psc.ui.theme.IconMuted
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Track
import com.padanam.psc.util.Format

@Composable
fun QuizScreen(
    container: AppContainer,
    onFinished: (String) -> Unit,
    onLeave: () -> Unit
) {
    val vm: QuizViewModel = viewModel(factory = QuizViewModel.factory(container))
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.submittedAttemptId) {
        val id = ui.submittedAttemptId
        if (id != null) onFinished(id)
    }

    BackHandler(
        enabled = ui.submittedAttemptId == null &&
            !ui.missing &&
            !ui.paletteOpen &&
            !ui.submitDialog &&
            !ui.leaveDialog
    ) {
        vm.showLeaveDialog(true)
    }

    BackHandler(enabled = ui.missing) { onLeave() }

    if (ui.missing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud)
        ) {
            PadanamStatusBar()
            TopBar(title = "Attempt", onBack = onLeave)
            Box(modifier = Modifier.padding(20.dp)) {
                EmptyStateCard(
                    icon = Icons.Rounded.Timer,
                    title = "No open attempt",
                    body = ui.error ?: "Start a practice or mock exam from a question set.",
                    primaryLabel = "Back home",
                    onPrimary = onLeave
                )
            }
        }
        return
    }

    if (ui.loading || ui.exam == null || ui.current == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud)
        ) {
            PadanamStatusBar()
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp)
            }
        }
        return
    }

    val exam = ui.exam ?: return
    val question = ui.current ?: return
    val isExam = ui.isExam
    val selected = exam.answers[question.id]
    val flagged = question.id in exam.flagged
    val options = question.options(ui.language)
    val isLast = ui.currentNumber >= ui.total
    val timeLow = isExam && ui.remainingSeconds in 1..60
    val progress = if (ui.total == 0) 0f else ui.currentNumber.toFloat() / ui.total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .height(44.dp)
        ) {
            CircleAction(
                icon = Icons.Rounded.Close,
                contentDescription = "Close attempt",
                onClick = { vm.showLeaveDialog(true) },
                size = 40.dp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    "${ui.currentNumber} / ${ui.total}",
                    style = AppType.MonoLarge,
                    color = Navy
                )
                Text(
                    "QUESTION",
                    style = AppType.MonoSmall.copy(letterSpacing = 1.sp),
                    color = InkMuted
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (timeLow) DangerSoft else IndigoSoft)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Rounded.Timer,
                    null,
                    tint = if (timeLow) Danger else Indigo,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    ui.timerText,
                    style = AppType.MonoValue,
                    color = if (timeLow) Danger else Indigo
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            ProgressTrack(
                progress = progress,
                fill = if (timeLow) Danger else Indigo
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${question.topic.uppercase()} · SINGLE ANSWER",
                        style = AppType.MonoSmall.copy(letterSpacing = 0.4.sp),
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (flagged) AmberSoft else Color.Transparent)
                            .clickable { vm.toggleFlag() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Flag,
                                null,
                                tint = if (flagged) AmberDeep else IconMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                if (flagged) "FLAGGED" else "FLAG",
                                style = AppType.MonoChip.copy(letterSpacing = 0.5.sp),
                                color = if (flagged) AmberDeep else IconMuted
                            )
                        }
                    }
                    LanguageSwitch(language = ui.language, onChange = { vm.setLanguage(it) })
                }
            }

            QuestionText(
                text = question.question(ui.language),
                language = ui.language,
                large = false
            )

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                options.forEachIndexed { index, option ->
                    val letter = ('A' + index).toString()
                    OptionPill(
                        letter = letter,
                        text = option.ifBlank { "—" },
                        language = ui.language,
                        visual = if (selected == letter) OptionVisual.SELECTED else OptionVisual.DEFAULT,
                        onClick = { vm.selectOption(letter) },
                        trailing = if (selected == letter) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    tint = Indigo,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            Text(
                when {
                    selected != null -> "Answer saved. You can change it any time before submission."
                    isExam -> "Tap an option to select it. Unanswered questions are marked skipped."
                    else -> "Tap an option. Practice saves your score when you submit."
                },
                style = AppType.BodyTiny,
                color = InkMuted
            )

            if (ui.error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(ui.error ?: "", style = AppType.BodyTiny, color = Danger)
                }
            }

            Spacer(Modifier.height(2.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cloud)
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuizSquareButton(
                    icon = Icons.Rounded.GridView,
                    contentDescription = "Question palette",
                    onClick = { vm.setPalette(true) },
                    badge = "${ui.answeredCount}/${ui.total}"
                )
                QuizSquareButton(
                    icon = Icons.Rounded.Flag,
                    contentDescription = "Flag question",
                    onClick = { vm.toggleFlag() },
                    tint = if (flagged) AmberDeep else Navy,
                    container = if (flagged) AmberSoft else SurfaceWhite,
                    border = if (flagged) Amber else null
                )
                QuizSquareButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Previous question",
                    onClick = { vm.previous() },
                    enabled = exam.currentIndex > 0
                )
                PrimaryFlowButton(
                    label = when {
                        ui.submitting -> "Submitting…"
                        isLast -> if (isExam) "Review & submit" else "Finish practice"
                        else -> "Next question"
                    },
                    icon = if (isLast) Icons.Rounded.Check else Icons.Rounded.ArrowForward,
                    enabled = !ui.submitting,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isLast) vm.requestSubmit() else vm.next()
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${ui.answeredCount} answered · ${ui.flaggedCount} flagged",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                    color = InkMuted
                )
                Text(
                    if (isExam) "Autosaved" else "Practice",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                    color = InkMuted
                )
            }
        }
    }

    if (ui.paletteOpen) {
        AlertDialog(
            onDismissRequest = { vm.setPalette(false) },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("All questions", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LegendDot(color = Indigo, label = "Answered", count = ui.answeredCount)
                        LegendDot(color = Amber, label = "Flagged", count = ui.flaggedCount)
                        LegendDot(
                            color = Track,
                            label = "Left",
                            count = (ui.total - ui.answeredCount).coerceAtLeast(0)
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(ui.questions) { index, item ->
                            val isCurrent = index == (exam.currentIndex)
                            val answered = exam.answers.containsKey(item.id)
                            val isFlagged = item.id in exam.flagged
                            Box(
                                modifier = Modifier
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isCurrent -> Navy
                                            answered -> IndigoSoft
                                            else -> SurfaceWhite
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isCurrent -> Navy
                                            isFlagged -> Amber
                                            answered -> IndigoSoft
                                            else -> Hairline
                                        },
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { vm.goTo(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = AppType.MonoValue.copy(fontSize = 11.sp),
                                    color = when {
                                        isCurrent -> SurfaceWhite
                                        answered -> Indigo
                                        else -> InkMuted
                                    }
                                )
                                if (isFlagged) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(AmberDeep)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Navy)
                            )
                            Text("Current", style = AppType.MonoSmall, color = InkMuted)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(AmberDeep)
                            )
                            Text("Flagged", style = AppType.MonoSmall, color = InkMuted)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.requestSubmit() }) {
                    Text(
                        if (isExam) "Submit exam" else "Finish practice",
                        style = AppType.ButtonSmall,
                        color = Indigo
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setPalette(false) }) {
                    Text("Keep going", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        )
    }

    if (ui.leaveDialog) {
        AlertDialog(
            onDismissRequest = { vm.showLeaveDialog(false) },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Leave this attempt?", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your ${ui.answeredCount} answered question${if (ui.answeredCount == 1) "" else "s"} and the timer are already saved. Resume from Home whenever you are ready.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    if (isExam) {
                        Text(
                            "The countdown keeps running while the app is away.",
                            style = AppType.BodyTiny,
                            color = IndigoText
                        )
                    }
                    Text(
                        "Abandoning discards this attempt and its answers; no score is saved.",
                        style = AppType.BodyTiny,
                        color = Danger
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.showLeaveDialog(false)
                        onLeave()
                    }
                ) {
                    Text("Keep progress", style = AppType.ButtonSmall, color = Indigo)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.showLeaveDialog(false) }) {
                        Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
                    }
                    TextButton(
                        onClick = {
                            vm.abandon()
                            onLeave()
                        }
                    ) {
                        Text("Abandon", style = AppType.ButtonSmall, color = Danger)
                    }
                }
            }
        )
    }

    if (ui.submitDialog) {
        AlertDialog(
            onDismissRequest = { vm.cancelSubmit() },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    if (isExam) "Submit this exam?" else "Finish this practice?",
                    style = AppType.Question,
                    color = Navy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Your score is saved immediately, with a full answer review and explanations.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubmitStat("Answered", "${ui.answeredCount}", Indigo, IndigoSoft, Modifier.weight(1f))
                        SubmitStat(
                            "Skipped",
                            "${(ui.total - ui.answeredCount).coerceAtLeast(0)}",
                            InkMuted,
                            SurfaceMuted,
                            Modifier.weight(1f)
                        )
                        SubmitStat("Flagged", "${ui.flaggedCount}", AmberDeep, AmberSoft, Modifier.weight(1f))
                    }
                    if (ui.answeredCount < ui.total) {
                        Text(
                            "Unanswered questions are counted as skipped and score zero.",
                            style = AppType.BodyTiny,
                            color = AmberDeep
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.submit() },
                    enabled = !ui.submitting
                ) {
                    Text(
                        if (ui.submitting) "Saving…" else "Submit",
                        style = AppType.ButtonSmall,
                        color = Indigo
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelSubmit() }) {
                    Text("Keep going", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        )
    }
}

@Composable
private fun QuizSquareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Navy,
    container: Color = SurfaceWhite,
    border: Color? = null,
    enabled: Boolean = true,
    badge: String? = null
) {
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(container.copy(alpha = if (enabled) 1f else 0.55f))
            .then(if (border != null) Modifier.border(1.5.dp, border, RoundedCornerShape(15.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (enabled) tint else IconMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(19.dp)
        )
        if (badge != null) {
            Text(
                badge,
                style = AppType.MonoSmall.copy(fontSize = 7.sp, letterSpacing = 0.sp),
                color = Indigo,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun PrimaryFlowButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) Navy else Navy.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            style = AppType.Button.copy(fontSize = 13.5.sp),
            color = SurfaceWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Icon(icon, null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SubmitStat(
    label: String,
    value: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label.uppercase(), style = AppType.MonoSmall.copy(fontSize = 7.5.sp), color = color.copy(alpha = 0.75f))
        Text(value, style = AppType.StatValueSmall.copy(fontSize = 18.sp), color = color)
    }
}
