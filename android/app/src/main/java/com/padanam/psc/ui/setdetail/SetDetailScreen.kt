package com.padanam.psc.ui.setdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.SetSummary
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.AppTextField
import com.padanam.psc.ui.components.AttemptRow
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.DarkCardButton
import com.padanam.psc.ui.components.DarkGradientCard
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.LanguageSwitch
import com.padanam.psc.ui.components.MonoChip
import com.padanam.psc.ui.components.OptionPill
import com.padanam.psc.ui.components.OptionVisual
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.QuestionText
import com.padanam.psc.ui.components.ResultChip
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AmberInk
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.IndigoWash
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.util.Format
import kotlinx.coroutines.delay

@Composable
fun SetDetailScreen(
    appVm: AppViewModel,
    setId: String,
    onBack: () -> Unit,
    onStartSetup: (String) -> Unit,
    onOpenAttempt: (String) -> Unit
) {
    val summary by appVm.setSummary(setId).collectAsStateWithLifecycle(initialValue = null)
    val questions by appVm.questions(setId).collectAsStateWithLifecycle(emptyList())
    val attempts by appVm.attempts.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val activeExam by appVm.activeExam.collectAsStateWithLifecycle()

    val setAttempts = attempts.filter { it.setId == setId }
    var language by rememberSaveable(setId) { mutableStateOf(settings.language) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var showAll by rememberSaveable(setId) { mutableStateOf(false) }
    var waited by remember(setId) { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(setId) {
        delay(1_200)
        waited = true
    }

    if (summary == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud)
        ) {
            PadanamStatusBar()
            TopBar(title = "Question set", onBack = onBack)
            if (!waited) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp)
                }
            } else {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Set not found",
                        body = "This set may have been deleted on this phone or on another device.",
                        primaryLabel = "Go back",
                        onPrimary = onBack
                    )
                }
            }
        }
        return
    }

    val info = summary ?: return
    val shown = if (showAll) questions else questions.take(5)
    val resume = activeExam?.takeIf { it.setId == setId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        TopBar(
            title = "Question set",
            onBack = onBack,
            trailing = {
                CircleAction(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "Rename set",
                    onClick = { renameOpen = true },
                    size = 40.dp
                )
                CircleAction(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete set",
                    onClick = { deleteOpen = true },
                    size = 40.dp,
                    tint = Danger,
                    container = SurfaceWhite
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SetHero(
                    summary = info,
                    language = language,
                    onLanguage = { language = Lang.normalize(it) },
                    onPractice = { onStartSetup(QuizMode.PRACTICE) },
                    onExam = { onStartSetup(QuizMode.EXAM) }
                )
            }

            if (resume != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AmberSoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(AmberInk),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            "An attempt on this set is still open. Starting a new one will replace it.",
                            style = AppType.BodyTiny,
                            color = AmberInk,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniStat(
                        label = "Questions",
                        value = "${info.questionCount}",
                        note = "in this set",
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "Best",
                        value = info.bestScore?.let { "$it%" } ?: "—",
                        note = if (info.bestScore == null) "not attempted" else "personal best",
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "Attempts",
                        value = "${info.attemptCount}",
                        note = "saved scores",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (questions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeading(title = "Questions")
                        LanguageSwitch(language = language, onChange = { language = Lang.normalize(it) })
                    }
                }

                items(shown, key = { it.id }) { question ->
                    QuestionPreview(
                        number = question.order + 1,
                        question = question,
                        language = language,
                        expanded = expanded[question.id] == true,
                        onToggle = { expanded[question.id] = expanded[question.id] != true }
                    )
                }

                if (questions.size > 5) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceWhite)
                                .clickable { showAll = !showAll }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                if (showAll) "Show fewer questions" else "Show all ${questions.size} questions",
                                style = AppType.LabelSmallStrong,
                                color = Indigo
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(IndigoWash)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            null,
                            tint = Indigo,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Tap a question to reveal its explanation. The correct option is always marked.",
                            style = AppType.BodyTiny,
                            color = IndigoText
                        )
                    }
                }
            } else {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "This set has no questions",
                        body = "Scan the pages again to rebuild this set with bilingual questions.",
                        primaryLabel = "Scan a question",
                        onPrimary = onBack
                    )
                }
            }

            if (setAttempts.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Attempt history",
                        meta = "${setAttempts.size} saved"
                    )
                }
                items(setAttempts.take(6), key = { it.id }) { attempt ->
                    AttemptRow(attempt = attempt, showTitle = false, onClick = { onOpenAttempt(attempt.id) })
                }
            }

            item {
                Text(
                    "Created ${Format.relativeTime(info.createdAt)} · ${info.subject}",
                    style = AppType.MonoSmall,
                    color = InkMuted
                )
            }
        }
    }

    if (renameOpen) {
        var title by remember(info.id) { mutableStateOf(info.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename set", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Give this set a name you will recognise in your library.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    AppTextField(
                        label = "Set name",
                        value = title,
                        onValueChange = { title = it.take(80) },
                        placeholder = "Cell Division Mock",
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            appVm.renameSet(setId, title)
                            renameOpen = false
                        }
                    }
                ) {
                    Text("Save", style = AppType.ButtonSmall, color = Indigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete this set?", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "The set, its ${info.questionCount} questions and ${setAttempts.size} attempt scores will be removed from this phone.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DangerSoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "If Neon sync is on, the set is deleted there too on the next sync.",
                            style = AppType.BodyTiny,
                            color = Danger
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appVm.deleteSet(setId)
                        deleteOpen = false
                        onBack()
                    }
                ) {
                    Text("Delete", style = AppType.ButtonSmall, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("Keep set", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        )
    }
}

@Composable
private fun SetHero(
    summary: SetSummary,
    language: String,
    onLanguage: (String) -> Unit,
    onPractice: () -> Unit,
    onExam: () -> Unit
) {
    DarkGradientCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoChip(
                    text = when (summary.set.source) {
                        "demo" -> "DEMO SET"
                        "import" -> "IMPORTED"
                        else -> "SCANNED"
                    },
                    background = SurfaceWhite.copy(alpha = 0.12f),
                    color = SurfaceWhite.copy(alpha = 0.8f)
                )
                if (summary.bestScore != null && summary.bestScore >= 60) {
                    MonoChip(
                        text = "PASSED",
                        background = Success.copy(alpha = 0.18f),
                        color = Color(0xFF7DE3C0)
                    )
                }
            }
            LanguageSwitch(language = language, onChange = onLanguage, dark = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                summary.subject.uppercase(),
                style = AppType.Eyebrow.copy(letterSpacing = 0.7.sp),
                color = Color(0xFFA99BFF)
            )
            Text(
                summary.title,
                style = AppType.HeroTitle.copy(fontSize = 25.sp, lineHeight = 30.sp),
                color = SurfaceWhite,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${summary.questionCount} questions · ${summary.set.source} · ${Format.relativeTime(summary.createdAt)}",
                style = AppType.BodyTiny,
                color = SurfaceWhite.copy(alpha = 0.55f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceWhite.copy(alpha = 0.07f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroMeta(
                label = "BEST",
                value = summary.bestScore?.let { "$it%" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            HeroMeta(
                label = "ATTEMPTS",
                value = "${summary.attemptCount}",
                modifier = Modifier.weight(1f)
            )
            HeroMeta(
                label = "PASS MARK",
                value = "${Format.passMark(summary.questionCount)}/${summary.questionCount}",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DarkCardButton(
                text = "Practice",
                icon = Icons.Rounded.PlayArrow,
                onClick = onPractice,
                modifier = Modifier.weight(1f),
                filled = true
            )
            DarkCardButton(
                text = "Mock exam",
                icon = Icons.Rounded.Timer,
                onClick = onExam,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeroMeta(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp), color = SurfaceWhite.copy(alpha = 0.45f))
        Text(value, style = AppType.StatValueSmall, color = SurfaceWhite)
    }
}

@Composable
private fun MiniStat(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label.uppercase(), style = AppType.MonoSmall, color = InkMuted)
        Text(value, style = AppType.StatValueSmall, color = Navy)
        Text(note, style = AppType.BodyTiny, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuestionPreview(
    number: Int,
    question: QuestionEntity,
    language: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val options = question.options(language)
    val correctIndex = question.correctOption.uppercase().firstOrNull()?.minus('A') ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Q$number · ${question.topic.uppercase()} · ${question.difficulty.uppercase()}",
                style = AppType.MonoSmall.copy(letterSpacing = 0.4.sp),
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ResultChip(
                text = if (language == Lang.ML) "ML" else "EN",
                color = Indigo,
                background = IndigoSoft
            )
        }

        QuestionText(text = question.question(language), language = language)

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEachIndexed { index, option ->
                OptionPill(
                    letter = ('A' + index).toString(),
                    text = option.ifBlank { "—" },
                    visual = if (index == correctIndex) OptionVisual.CORRECT else OptionVisual.DEFAULT,
                    language = language,
                    trailing = if (index == correctIndex) {
                        {
                            Text(
                                "CORRECT",
                                style = AppType.MonoChip.copy(letterSpacing = 0.6.sp),
                                color = Success
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }

        if (expanded) {
            val explanation = question.explanation(language)
            if (explanation.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IndigoWash)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "WHY THIS ANSWER",
                        style = AppType.MonoSmall.copy(letterSpacing = 0.7.sp),
                        color = Indigo
                    )
                    Text(
                        explanation,
                        style = if (language == Lang.ML) {
                            com.padanam.psc.ui.theme.MlType.Small
                        } else {
                            AppType.BodySmall
                        },
                        color = IndigoText
                    )
                }
            } else {
                Text(
                    "No explanation was captured for this question.",
                    style = AppType.BodyTiny,
                    color = InkMuted
                )
            }
        } else {
            Text(
                "Tap to ${if (question.explanation(language).isBlank()) "collapse" else "see the explanation"}",
                style = AppType.MonoSmall,
                color = Indigo
            )
        }
    }
}
