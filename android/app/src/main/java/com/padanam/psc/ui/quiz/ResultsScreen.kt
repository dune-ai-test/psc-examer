package com.padanam.psc.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.DarkGradientCard
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.LegendDot
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.ResultChip
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.StatTile
import com.padanam.psc.ui.components.StatusDotBadge
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AmberBar
import com.padanam.psc.ui.theme.AmberDeep
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessBright
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Track
import com.padanam.psc.util.Format
import kotlinx.coroutines.delay

@Composable
fun ResultsScreen(
    appVm: AppViewModel,
    attemptId: String,
    onHome: () -> Unit,
    onReview: (String) -> Unit
) {
    val attempt by appVm.attempt(attemptId).collectAsStateWithLifecycle(initialValue = null)
    val allAttempts by appVm.attempts.collectAsStateWithLifecycle()
    var answers by remember(attemptId) { mutableStateOf<List<AnswerRecord>>(emptyList()) }
    var waited by remember(attemptId) { mutableStateOf(false) }

    LaunchedEffect(attemptId) {
        answers = appVm.answers(attemptId)
        delay(1_200)
        waited = true
    }

    if (attempt == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud)
        ) {
            TopSafeArea()
            TopBar(title = "Results", onBack = onHome)
            if (!waited) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp)
                }
            } else {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.TrendingUp,
                        title = "Result not found",
                        body = "This attempt is no longer stored on this phone.",
                        primaryLabel = "Back home",
                        onPrimary = onHome
                    )
                }
            }
        }
        return
    }

    val data = attempt ?: return
    val sameSet = allAttempts.filter { it.setId == data.setId }
    val previous = sameSet
        .filter { it.id != data.id && it.completedAt < data.completedAt }
        .maxByOrNull { it.completedAt }
    val best = sameSet.maxOfOrNull { it.scorePercent } ?: data.scorePercent
    val delta = previous?.let { data.scorePercent - it.scorePercent }
    val passed = data.scorePercent >= Format.PASS_PERCENT
    val isExam = data.mode == QuizMode.EXAM
    val previewRows = answers
        .withIndex()
        .sortedWith(
            compareBy(
                { if (it.value.isSkipped || !it.value.isCorrect) 0 else 1 },
                { it.index }
            )
        )
        .take(2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        TopBar(
            title = "Results",
            leading = {
                CircleAction(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Back home",
                    onClick = onHome
                )
            },
            trailing = {
                CircleAction(
                    icon = Icons.Rounded.Share,
                    contentDescription = "Share result",
                    onClick = {
                        appVm.shareResult(
                            title = "${data.setTitle} · ${data.scorePercent}%",
                            text = buildString {
                                appendLine("Padanam ${if (isExam) "mock exam" else "practice"} result")
                                appendLine(data.setTitle)
                                appendLine(
                                    "${data.scorePercent}% · ${data.correct}/${data.total} correct"
                                )
                                appendLine(
                                    "${data.wrong} missed · ${data.skipped} skipped · ${Format.duration(data.durationSeconds)}"
                                )
                                append("Pass mark ${Format.passMark(data.total)}/${data.total}")
                            }
                        )
                    }
                )
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DarkGradientCard(corner = 24.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "FINAL SCORE",
                            style = AppType.MonoSmall.copy(letterSpacing = 1.1.sp),
                            color = OnDark.copy(alpha = 0.6f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (passed) Success.copy(alpha = 0.2f)
                                    else AmberSoft.copy(alpha = 0.18f)
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                null,
                                tint = if (passed) SuccessBright else AmberBar,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                if (passed) "PASSED" else "NEEDS WORK",
                                style = AppType.MonoChip.copy(letterSpacing = 0.9.sp),
                                color = if (passed) SuccessBright else AmberBar
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${data.scorePercent}%",
                            style = AppType.HeroTitle.copy(fontSize = 46.sp, lineHeight = 46.sp),
                            color = OnDark
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "${data.correct} of ${data.total} correct",
                            style = AppType.BodyTiny,
                            color = OnDark.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        when {
                            previous == null -> "First attempt · ${data.subject}"
                            delta == 0 -> "Same as last attempt · ${data.subject}"
                            delta != null && delta > 0 -> "+$delta% vs your last attempt · ${data.subject}"
                            else -> "${delta ?: 0}% vs your last attempt · ${data.subject}"
                        },
                        style = AppType.MonoSmall.copy(letterSpacing = 0.3.sp),
                        color = Color(0xFFA99BFF)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "ANSWER BREAKDOWN",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                            color = InkMuted
                        )
                        Text(Format.timer(data.durationSeconds), style = AppType.MonoValue, color = Navy)
                    }
                    BreakdownBar(
                        correct = data.correct,
                        wrong = data.wrong,
                        skipped = data.skipped
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDot(color = Success, label = "Correct", count = data.correct)
                        LegendDot(color = AmberBar, label = "Missed", count = data.wrong)
                        LegendDot(color = Color(0xFFC2C6D2), label = "Skipped", count = data.skipped)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatTile(
                        label = "Time used",
                        value = Format.timer(data.durationSeconds),
                        note = if (isExam) "timed mock exam" else "practice session",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Best score",
                        value = "$best%",
                        note = "${sameSet.size} attempt${if (sameSet.size == 1) "" else "s"} on this set",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (passed) SuccessSoft else AmberSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (passed) Success else AmberDeep),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = OnDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        if (passed) {
                            "Cleared the ${Format.passMark(data.total)}-mark pass line. Review the missed answers to push higher."
                        } else {
                            "You needed ${Format.passMark(data.total)} correct. Open the review to see exactly where the marks went."
                        },
                        style = AppType.BodyTiny,
                        color = if (passed) IndigoText else AmberDeep
                    )
                }
            }

            if (previewRows.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Review answers",
                        action = "See all ${data.total}",
                        onAction = { onReview(data.id) }
                    )
                }
                items(previewRows, key = { it.value.questionId }) { indexed ->
                    val record = indexed.value
                    ReviewPreviewRow(
                        number = indexed.index + 1,
                        record = record,
                        onClick = { onReview(data.id) }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cloud)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = "Review all answers",
                onClick = { onReview(data.id) },
                icon = Icons.Rounded.Check
            )
            Text(
                "Back to home",
                style = AppType.LabelSmallStrong,
                color = Indigo,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onHome)
                    .padding(vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun BreakdownBar(correct: Int, wrong: Int, skipped: Int) {
    val total = (correct + wrong + skipped).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Track)
    ) {
        if (correct > 0) {
            Box(
                modifier = Modifier
                    .weight(correct.toFloat() / total)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Success)
            )
        }
        if (wrong > 0) {
            Box(
                modifier = Modifier
                    .weight(wrong.toFloat() / total)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(AmberBar)
            )
        }
        if (skipped > 0) {
            Box(
                modifier = Modifier
                    .weight(skipped.toFloat() / total)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Color(0xFFC2C6D2))
            )
        }
    }
}

@Composable
private fun ReviewPreviewRow(
    number: Int,
    record: AnswerRecord,
    onClick: () -> Unit
) {
    val status = when {
        record.isSkipped -> Triple("SKIPPED", InkMuted, SurfaceMuted)
        record.isCorrect -> Triple("CORRECT", Success, SuccessSoft)
        else -> Triple("MISSED", AmberDeep, AmberSoft)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        StatusDotBadge(
            icon = if (record.isCorrect) Icons.Rounded.Check else Icons.Rounded.Close,
            tint = status.second,
            background = status.third
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                record.questionEn,
                style = AppType.BodySmall.copy(fontSize = 11.5.sp),
                color = Navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Q$number · ${record.topic}",
                style = AppType.MonoSmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            status.first,
            style = AppType.MonoChip.copy(letterSpacing = 0.4.sp),
            color = status.second
        )
    }
}
