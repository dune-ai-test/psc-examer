package com.padanam.psc.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.ChoiceChip
import com.padanam.psc.ui.components.DarkGradientCard
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.LanguageSwitch
import com.padanam.psc.ui.components.OptionPill
import com.padanam.psc.ui.components.OptionVisual
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.QuestionText
import com.padanam.psc.ui.components.ResultChip
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AmberBar
import com.padanam.psc.ui.theme.AmberDeep
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.IndigoWash
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.MlType
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessInk
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.util.Format
import kotlinx.coroutines.delay

private const val FILTER_ALL = "all"
private const val FILTER_CORRECT = "correct"
private const val FILTER_MISSED = "missed"
private const val FILTER_SKIPPED = "skipped"

@Composable
fun ReviewScreen(
    appVm: AppViewModel,
    attemptId: String,
    onBack: () -> Unit
) {
    val attempt by appVm.attempt(attemptId).collectAsStateWithLifecycle(initialValue = null)
    var answers by remember(attemptId) { mutableStateOf<List<AnswerRecord>>(emptyList()) }
    var loaded by remember(attemptId) { mutableStateOf(false) }
    var waited by remember(attemptId) { mutableStateOf(false) }
    var language by rememberSaveable(attemptId) { mutableStateOf(Lang.EN) }
    var filter by rememberSaveable(attemptId) { mutableStateOf(FILTER_ALL) }

    LaunchedEffect(attemptId) {
        answers = appVm.answers(attemptId)
        loaded = true
        delay(1_200)
        waited = true
    }

    if (attempt == null || !loaded) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud)
        ) {
            TopSafeArea()
            TopBar(title = "Review", onBack = onBack)
            if (!waited) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp)
                }
            } else {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.HourglassEmpty,
                        title = "Attempt not found",
                        body = "This attempt is no longer stored on this phone.",
                        primaryLabel = "Go back",
                        onPrimary = onBack
                    )
                }
            }
        }
        return
    }

    val data = attempt ?: return
    val correctCount = answers.count { it.isCorrect && !it.isSkipped }
    val missedCount = answers.count { !it.isSkipped && !it.isCorrect }
    val skippedCount = answers.count { it.isSkipped }
    val filtered = when (filter) {
        FILTER_CORRECT -> answers.withIndex().filter { it.value.isCorrect && !it.value.isSkipped }
        FILTER_MISSED -> answers.withIndex().filter { !it.value.isSkipped && !it.value.isCorrect }
        FILTER_SKIPPED -> answers.withIndex().filter { it.value.isSkipped }
        else -> answers.withIndex().toList()
    }
    val passed = data.scorePercent >= Format.PASS_PERCENT
    val isExam = data.mode == QuizMode.EXAM
    val averageSeconds = if (answers.isEmpty()) 0 else answers.sumOf { it.timeSpentSeconds } / answers.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        TopBar(
            title = "Answer review",
            onBack = onBack,
            trailing = {
                LanguageSwitch(
                    language = language,
                    onChange = { language = Lang.normalize(it) }
                )
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp),
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
                            "ATTEMPT REVIEW",
                            style = AppType.MonoSmall.copy(letterSpacing = 1.1.sp),
                            color = OnDark.copy(alpha = 0.6f)
                        )
                        ResultChip(
                            text = if (passed) "PASSED" else "NEEDS WORK",
                            color = if (passed) Color(0xFF7DE3C0) else AmberBar,
                            background = OnDark.copy(alpha = 0.1f)
                        )
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${data.scorePercent}%",
                            style = AppType.HeroTitle.copy(fontSize = 44.sp, lineHeight = 44.sp),
                            color = OnDark
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${data.correct} of ${data.total} correct",
                            style = AppType.BodyTiny,
                            color = OnDark.copy(alpha = 0.62f),
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }

                    Text(
                        data.setTitle,
                        style = AppType.SectionTitle.copy(fontSize = 14.sp),
                        color = OnDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${if (isExam) "Mock exam" else "Practice"} · ${data.subject} · " +
                            Format.fileDate(data.completedAt),
                        style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                        color = OnDark.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(OnDark.copy(alpha = 0.07f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewStat(
                            label = "CORRECT",
                            value = "$correctCount",
                            tint = Color(0xFF7DE3C0),
                            modifier = Modifier.weight(1f)
                        )
                        ReviewStat(
                            label = "MISSED",
                            value = "$missedCount",
                            tint = AmberBar,
                            modifier = Modifier.weight(1f)
                        )
                        ReviewStat(
                            label = "SKIPPED",
                            value = "$skippedCount",
                            tint = OnDark.copy(alpha = 0.65f),
                            modifier = Modifier.weight(1f)
                        )
                        ReviewStat(
                            label = "AVG / Q",
                            value = if (averageSeconds > 0) "${averageSeconds}s" else "—",
                            tint = Color(0xFFB4AAFF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        text = "All ${answers.size}",
                        selected = filter == FILTER_ALL,
                        onClick = { filter = FILTER_ALL }
                    )
                    ChoiceChip(
                        text = "Correct $correctCount",
                        selected = filter == FILTER_CORRECT,
                        onClick = { filter = FILTER_CORRECT }
                    )
                    ChoiceChip(
                        text = "Missed $missedCount",
                        selected = filter == FILTER_MISSED,
                        onClick = { filter = FILTER_MISSED }
                    )
                    ChoiceChip(
                        text = "Skipped $skippedCount",
                        selected = filter == FILTER_SKIPPED,
                        onClick = { filter = FILTER_SKIPPED }
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.SearchOff,
                        title = if (answers.isEmpty()) "No answers saved" else "Nothing in this filter",
                        body = if (answers.isEmpty()) {
                            "This attempt has no stored answer snapshots to review."
                        } else {
                            "Try another filter to see the rest of the paper."
                        },
                        primaryLabel = if (answers.isEmpty()) null else "Show all answers",
                        onPrimary = if (answers.isEmpty()) null else ({ filter = FILTER_ALL })
                    )
                }
            } else {
                items(filtered, key = { it.value.questionId }) { indexed ->
                    AnswerReviewCard(
                        number = indexed.index + 1,
                        total = answers.size,
                        record = indexed.value,
                        language = language
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IndigoWash)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        null,
                        tint = Indigo,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Answers and explanations are stored inside this attempt, so the review works even if the set is deleted later.",
                        style = AppType.BodyTiny,
                        color = IndigoText
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = AppType.MonoSmall.copy(fontSize = 7.5.sp, letterSpacing = 0.6.sp),
            color = OnDark.copy(alpha = 0.4f),
            maxLines = 1
        )
        Text(value, style = AppType.MonoValue, color = tint)
    }
}

@Composable
private fun AnswerReviewCard(
    number: Int,
    total: Int,
    record: AnswerRecord,
    language: String
) {
    val skipped = record.isSkipped
    val correct = record.isCorrect && !skipped
    val statusText = when {
        skipped -> "SKIPPED"
        correct -> "CORRECT"
        else -> "MISSED"
    }
    val statusColor = when {
        skipped -> InkMuted
        correct -> Success
        else -> AmberDeep
    }
    val statusBackground = when {
        skipped -> SurfaceMuted
        correct -> SuccessSoft
        else -> AmberSoft
    }
    val options = record.options(language)
    val correctLetter = record.correctOption.uppercase().take(1)
    val selectedLetter = record.selectedOption?.uppercase()?.take(1)
    val explanation = record.explanation(language)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Q$number OF $total",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.7.sp),
                    color = InkMuted
                )
                if (record.flagged) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AmberSoft)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Flag,
                            null,
                            tint = AmberDeep,
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            "FLAGGED",
                            style = AppType.MonoChip.copy(fontSize = 7.sp, letterSpacing = 0.5.sp),
                            color = AmberDeep
                        )
                    }
                }
            }
            ResultChip(
                text = statusText,
                color = statusColor,
                background = statusBackground
            )
        }

        Text(
            "${record.topic.ifBlank { "General" }.uppercase()} · ${record.difficulty.uppercase()}",
            style = AppType.MonoSmall.copy(letterSpacing = 0.5.sp),
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        QuestionText(
            text = record.question(language),
            language = language
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val letter = AnswerRecord.letterAt(index)
                val visual = when {
                    letter == correctLetter -> OptionVisual.CORRECT
                    letter == selectedLetter -> OptionVisual.WRONG
                    else -> OptionVisual.DEFAULT
                }
                OptionPill(
                    letter = letter,
                    text = option.ifBlank { "—" },
                    visual = visual,
                    language = language,
                    trailing = when {
                        letter == correctLetter -> {
                            {
                                Text(
                                    "CORRECT",
                                    style = AppType.MonoChip.copy(fontSize = 7.5.sp, letterSpacing = 0.6.sp),
                                    color = Success
                                )
                            }
                        }
                        letter == selectedLetter -> {
                            {
                                Text(
                                    "YOUR ANSWER",
                                    style = AppType.MonoChip.copy(fontSize = 7.5.sp, letterSpacing = 0.6.sp),
                                    color = AmberDeep
                                )
                            }
                        }
                        else -> null
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                when {
                    skipped -> "No option was selected"
                    correct -> "You picked ${selectedLetter ?: "—"}"
                    else -> "You picked ${selectedLetter ?: "—"} · answer is $correctLetter"
                },
                style = AppType.BodyTiny,
                color = InkMuted
            )
            if (record.timeSpentSeconds > 0) {
                Text(
                    Format.timer(record.timeSpentSeconds),
                    style = AppType.MonoSmall,
                    color = InkMuted
                )
            }
        }

        if (explanation.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(IndigoWash)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Indigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = OnDark,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                    Text(
                        "WHY THIS ANSWER",
                        style = AppType.MonoSmall.copy(letterSpacing = 0.7.sp),
                        color = Indigo
                    )
                }
                Text(
                    explanation,
                    style = if (language == Lang.ML) MlType.Small else AppType.BodySmall,
                    color = IndigoText
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceMuted)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    null,
                    tint = InkMuted,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    "No explanation was captured for this question.",
                    style = AppType.BodyTiny,
                    color = InkMuted
                )
            }
        }
    }
}
