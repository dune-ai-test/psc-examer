package com.padanam.psc.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.LanguageSwitch
import com.padanam.psc.ui.components.LightButton
import com.padanam.psc.ui.components.MonoChip
import com.padanam.psc.ui.components.OptionPill
import com.padanam.psc.ui.components.OptionVisual
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.QuestionText
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.Lang
import com.padanam.psc.ui.theme.AmberDeep
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.DarkSurface
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoMuted
import com.padanam.psc.ui.theme.IndigoPale
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.IndigoWash
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.MlType
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.PlexMono
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessBright
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceWhite
import kotlinx.coroutines.delay

/**
 * Confirmation screen after a successful AI extraction. The saved banner and
 * question cards mirror the mockup; the JSON block exposes exactly what was
 * verified so a bad extraction is obvious before practice starts.
 */
@Composable
fun ScanResultScreen(
    appVm: AppViewModel,
    setId: String,
    onClose: () -> Unit,
    onViewSet: () -> Unit,
    onPractice: () -> Unit,
    onScanAgain: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val summary by appVm.setSummary(setId).collectAsStateWithLifecycle(initialValue = null)
    val questions by appVm.questions(setId).collectAsStateWithLifecycle(initialValue = emptyList())
    var language by rememberSaveable(setId) { mutableStateOf(Lang.EN) }
    var waited by remember(setId) { mutableStateOf(false) }

    LaunchedEffect(setId) {
        delay(1_400)
        waited = true
    }

    val set = summary?.set
    val loading = set == null && questions.isEmpty() && !waited

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        TopBar(
            title = "Scan result",
            onBack = onClose
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp)
            }
        } else if (set == null && questions.isEmpty()) {
            Box(modifier = Modifier.padding(20.dp)) {
                EmptyStateCard(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Set not found",
                    body = "This scan is no longer in the library. It may have been deleted on this phone or another device.",
                    primaryLabel = "Scan again",
                    onPrimary = onScanAgain,
                    secondaryLabel = "Open settings",
                    onSecondary = onOpenSettings
                )
            }
        } else {
            val questionCount = summary?.questionCount ?: questions.size
            val subject = set?.subject?.takeIf { it.isNotBlank() } ?: "General"
            val title = set?.title?.takeIf { it.isNotBlank() } ?: "Scanned set"
            val firstQuestion = questions.firstOrNull()
            val correctLetter = firstQuestion?.correctOption?.uppercase()?.take(1).orEmpty()
                .ifBlank { "—" }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SavedBanner(
                        questionCount = questionCount,
                        subject = subject
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "EXTRACTED QUESTIONS",
                                style = AppType.Eyebrow.copy(letterSpacing = 0.9.sp),
                                color = InkMuted
                            )
                            Text(
                                title,
                                style = AppType.SectionTitle,
                                color = Navy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "$questionCount question${if (questionCount == 1) "" else "s"} ready",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                        LanguageSwitch(
                            language = language,
                            onChange = { language = Lang.normalize(it) }
                        )
                    }
                }

                if (questions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.ErrorOutline,
                            title = "No questions saved",
                            body = "The set exists but Gemini did not return a usable question. Scan a sharper photo to try again.",
                            primaryLabel = "Scan again",
                            onPrimary = onScanAgain
                        )
                    }
                } else {
                    itemsIndexed(questions, key = { _, question -> question.id }) { index, question ->
                        ScannedQuestionCard(
                            index = index,
                            question = question,
                            language = language
                        )
                    }

                    item {
                        JsonSummaryCard(
                            questionCount = questionCount,
                            firstQuestion = firstQuestion,
                            answer = correctLetter
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryButton(
                                text = if (questionCount == 1) "Practice this question" else "Practice these questions",
                                onClick = onPractice,
                                icon = Icons.Rounded.PlayArrow,
                                height = 50.dp,
                                modifier = Modifier.weight(1f)
                            )
                            LightButton(
                                text = "Again",
                                onClick = onScanAgain,
                                icon = Icons.Rounded.DocumentScanner,
                                container = SurfaceWhite,
                                tint = Navy,
                                height = 50.dp,
                                modifier = Modifier.weight(0.42f)
                            )
                        }
                    }

                    item {
                        LightButton(
                            text = "Open the full set",
                            onClick = onViewSet,
                            icon = Icons.Rounded.ArrowForward,
                            container = SurfaceWhite,
                            tint = Navy,
                            height = 48.dp
                        )
                    }

                    item {
                        Text(
                            "${questions.size} answer${if (questions.size == 1) "" else "s"} verified · saved locally, synced to Neon when online",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                            color = InkMuted.copy(alpha = 0.75f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedBanner(
    questionCount: Int,
    subject: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IndigoSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Indigo),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = SurfaceWhite,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "Saved to your library",
                style = AppType.LabelSmallStrong,
                color = IndigoText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$questionCount QUESTION${if (questionCount == 1) "" else "S"} · 4 OPTIONS · ${subject.uppercase()}",
                style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                color = IndigoMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScannedQuestionCard(
    index: Int,
    question: QuestionEntity,
    language: String
) {
    val confidence = question.confidence.coerceIn(0, 100)
    val chipBackground = when {
        confidence >= 85 -> SuccessSoft
        confidence >= 60 -> AmberSoft
        else -> DangerSoft
    }
    val chipColor = when {
        confidence >= 85 -> Success
        confidence >= 60 -> AmberDeep
        else -> Danger
    }
    val correctLetter = question.correctOption.uppercase().take(1)
    val options = question.options(language)
    val explanation = question.explanation(language)

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
            Text(
                "Q${index + 1} · SINGLE ANSWER",
                style = AppType.MonoSmall.copy(letterSpacing = 0.6.sp),
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MonoChip(
                text = "$confidence% match",
                background = chipBackground,
                color = chipColor
            )
        }

        QuestionText(
            text = question.question(language),
            language = language
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { optionIndex, option ->
                val letter = ('A' + optionIndex).toString()
                val correct = letter == correctLetter
                OptionPill(
                    letter = letter,
                    text = option.ifBlank { "—" },
                    visual = if (correct) OptionVisual.CORRECT else OptionVisual.DEFAULT,
                    language = language,
                    trailing = if (correct) {
                        {
                            Text(
                                "CORRECT",
                                style = AppType.MonoChip.copy(fontSize = 7.5.sp, letterSpacing = 0.7.sp),
                                color = Success
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }

        Text(
            "${question.topic.ifBlank { "General" }.uppercase()} · ${question.difficulty.ifBlank { "Medium" }.uppercase()}",
            style = AppType.MonoSmall.copy(letterSpacing = 0.5.sp),
            color = InkMuted.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (explanation.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndigoWash)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "WHY THIS ANSWER",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.7.sp),
                    color = Indigo
                )
                Text(
                    explanation,
                    style = if (language == Lang.ML) MlType.Small else AppType.BodyTiny,
                    color = IndigoText
                )
            }
        }
    }
}

@Composable
private fun JsonSummaryCard(
    questionCount: Int,
    firstQuestion: QuestionEntity?,
    answer: String
) {
    val codeStyle = AppType.MonoSmall.copy(
        fontFamily = PlexMono,
        fontSize = 8.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp
    )
    val id = firstQuestion?.id.orEmpty().ifBlank { "unknown" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (questionCount == 1) "question.json" else "questions.json",
                style = AppType.MonoSmall,
                color = SurfaceWhite.copy(alpha = 0.5f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = SuccessBright,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    "saved",
                    style = AppType.MonoSmall.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                    color = SuccessBright
                )
            }
        }

        Text("{", style = codeStyle, color = SurfaceWhite.copy(alpha = 0.38f))
        Text(
            "  \"id\": \"${id.take(16)}\",",
            style = codeStyle,
            color = SurfaceWhite.copy(alpha = 0.8f)
        )
        if (questionCount > 1) {
            Text(
                "  \"questions\": $questionCount,",
                style = codeStyle,
                color = SurfaceWhite.copy(alpha = 0.8f)
            )
        }
        Text(
            "  \"options\": \"A–D\",",
            style = codeStyle,
            color = SurfaceWhite.copy(alpha = 0.8f)
        )
        Text(
            "  \"answer\": \"$answer\",",
            style = codeStyle,
            color = IndigoPale
        )
        Text(
            "  \"verified\": true",
            style = codeStyle,
            color = SurfaceWhite.copy(alpha = 0.8f)
        )
        Text("}", style = codeStyle, color = SurfaceWhite.copy(alpha = 0.38f))
    }
}
