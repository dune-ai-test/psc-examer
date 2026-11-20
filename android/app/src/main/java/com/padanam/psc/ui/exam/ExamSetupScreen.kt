package com.padanam.psc.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.UiEvent
import com.padanam.psc.ui.components.ChoiceChip
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.LanguageSwitch
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.PadanamToggle
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.RuleRow
import com.padanam.psc.ui.components.SegmentedControl
import com.padanam.psc.ui.components.SettingsRow
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExamSetupScreen(
    appVm: AppViewModel,
    setId: String,
    initialMode: String,
    onBack: () -> Unit,
    onStarted: () -> Unit
) {
    val summary by appVm.setSummary(setId).collectAsStateWithLifecycle(initialValue = null)
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val activeExam by appVm.activeExam.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val total = summary?.questionCount ?: 0
    var mode by rememberSaveable(setId, initialMode) {
        mutableStateOf(if (initialMode == QuizMode.PRACTICE) QuizMode.PRACTICE else QuizMode.EXAM)
    }
    var selectedCount by rememberSaveable(setId) {
        mutableIntStateOf(if (initialMode == QuizMode.PRACTICE) total.coerceAtMost(10).coerceAtLeast(1) else total)
    }
    var pace by rememberSaveable(setId) { mutableIntStateOf(45) }
    var shuffle by rememberSaveable(setId) { mutableStateOf(true) }
    var language by rememberSaveable(setId) { mutableStateOf(settings.language) }
    var starting by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var waited by remember(setId) { mutableStateOf(false) }

    LaunchedEffect(setId, total) {
        if (total > 0) {
            selectedCount = if (mode == QuizMode.PRACTICE) total.coerceAtMost(10) else total
        }
    }
    LaunchedEffect(setId) {
        delay(1_200)
        waited = true
    }

    fun start() {
        if (starting) return
        starting = true
        scope.launch {
            val id = appVm.startExam(
                setId = setId,
                mode = mode,
                questionCount = selectedCount,
                durationSeconds = if (mode == QuizMode.EXAM) selectedCount * pace else 0,
                shuffle = shuffle,
                language = Lang.normalize(language)
            )
            starting = false
            if (id != null) {
                onStarted()
            } else {
                appVm.emit(UiEvent("This set has no questions to start.", error = true))
            }
        }
    }

    fun requestStart() {
        if (activeExam != null) confirmReplace = true else start()
    }

    if (summary == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cloud)
        ) {
            TopSafeArea()
            TopBar(
                title = if (mode == QuizMode.PRACTICE) "Practice" else "Mock exam",
                onBack = onBack
            )
            if (!waited) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp)
                }
            } else {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Description,
                        title = "Set unavailable",
                        body = "The questions for this set are no longer on this phone.",
                        primaryLabel = "Go back",
                        onPrimary = onBack
                    )
                }
            }
        }
        return
    }

    val info = summary ?: return
    val isExam = mode == QuizMode.EXAM
    val countOptions = remember(info.questionCount) {
        val all = info.questionCount
        buildList {
            listOf(10, 20, 30).filter { it < all }.forEach { add(it) }
            if (all > 0) add(all)
        }.distinct().sorted()
    }
    val durationOptions = listOf(30, 45, 60)
    val totalSeconds = selectedCount * pace
    val durationLabel = Format.duration(totalSeconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        TopBar(
            title = if (isExam) "Mock exam" else "Practice",
            onBack = onBack,
            trailing = {
                LanguageSwitch(language = language, onChange = { language = Lang.normalize(it) })
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SegmentedControl(
                    options = listOf(
                        QuizMode.PRACTICE to "Practice",
                        QuizMode.EXAM to "Mock exam"
                    ),
                    selected = mode,
                    onSelect = { chosen ->
                        mode = chosen
                        selectedCount = if (chosen == QuizMode.PRACTICE) {
                            info.questionCount.coerceAtMost(10).coerceAtLeast(1)
                        } else {
                            info.questionCount
                        }
                    }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        info.subject.uppercase(),
                        style = AppType.Eyebrow.copy(letterSpacing = 0.8.sp),
                        color = Indigo
                    )
                    Text(
                        info.title,
                        style = AppType.HeroTitle.copy(fontSize = 28.sp, lineHeight = 33.sp),
                        color = Navy
                    )
                    Text(
                        if (isExam) {
                            "${selectedCount} questions · one correct option each"
                        } else {
                            "${selectedCount} questions · untimed practice"
                        },
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    SettingsRow(
                        label = "Duration",
                        value = if (isExam) durationLabel else "No limit"
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Pace",
                        value = if (isExam) "$pace sec / question" else "Self-paced"
                    )
                    ThinDivider()
                    SettingsRow(label = "Questions", value = "$selectedCount")
                    ThinDivider()
                    SettingsRow(
                        label = "Pass mark",
                        value = "${Format.passMark(selectedCount)} / $selectedCount (40%)"
                    )
                    ThinDivider()
                    SettingsRow(label = "Answer type", value = "One of A–F")
                    ThinDivider()
                    SettingsRow(
                        label = "Language",
                        value = if (language == Lang.ML) "Malayalam + English" else "English + Malayalam"
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    SettingsRow(
                        label = "Shuffle questions",
                        value = if (shuffle) "On" else "Off",
                        trailing = {
                            PadanamToggle(
                                checked = shuffle,
                                onCheckedChange = { shuffle = it }
                            )
                        }
                    )
                    Text(
                        if (shuffle) {
                            "Questions are presented in a random order."
                        } else {
                            "Questions follow their saved order."
                        },
                        style = AppType.BodyTiny,
                        color = InkMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "NUMBER OF QUESTIONS",
                        style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                        color = InkMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        countOptions.forEach { count ->
                            ChoiceChip(
                                text = if (count == info.questionCount) "All $count" else "$count",
                                selected = selectedCount == count,
                                onClick = { selectedCount = count }
                            )
                        }
                    }
                }
            }

            if (isExam) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            "EXAM PACE",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                            color = InkMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            durationOptions.forEach { seconds ->
                                ChoiceChip(
                                    text = "$seconds sec",
                                    selected = pace == seconds,
                                    onClick = { pace = seconds }
                                )
                            }
                        }
                        Text(
                            "Each question gets $pace seconds, so this exam allows $durationLabel for $selectedCount questions.",
                            style = AppType.BodyTiny,
                            color = InkMuted
                        )
                    }
                }

                item {
                    InfoNote(
                        text = when {
                            selectedCount == info.questionCount && shuffle ->
                                "The full set is included in a random order."
                            selectedCount == info.questionCount ->
                                "The full set is included in its saved order."
                            shuffle ->
                                "$selectedCount questions are picked at random from this set."
                            else ->
                                "The first $selectedCount questions in this set will be used."
                        }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Before you begin", style = AppType.SectionTitle, color = Navy)
                    RuleRow(
                        icon = Icons.Rounded.Flag,
                        text = "Flag a question and come back to it later"
                    )
                    RuleRow(
                        icon = Icons.Rounded.Timer,
                        text = if (isExam) {
                            "The timer keeps running if you leave the exam"
                        } else {
                            "The clock counts up; practice never auto-submits"
                        }
                    )
                    RuleRow(
                        icon = Icons.Rounded.Description,
                        text = "Explanations unlock after you submit"
                    )
                    RuleRow(
                        icon = Icons.Rounded.PlayArrow,
                        text = "Every attempt stores its score, answers and time"
                    )
                }
            }

            item {
                Text(
                    if (isExam) {
                        "Your answers autosave after every tap. If you leave, the countdown continues."
                    } else {
                        "Practice pulls a mixed selection from this set. Leave any time — progress is kept."
                    },
                    style = AppType.BodyTiny,
                    color = InkMuted
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cloud)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeExam != null) {
                Text(
                    "An attempt is already open. Starting this one will replace it.",
                    style = AppType.BodyTiny,
                    color = InkMuted
                )
            }
            PrimaryButton(
                text = when {
                    starting -> "Starting…"
                    isExam -> "Start exam"
                    else -> "Start practice"
                },
                onClick = ::requestStart,
                icon = Icons.Rounded.PlayArrow,
                enabled = !starting && info.questionCount > 0
            )
            Text(
                if (isExam) "Timer starts the moment you tap start" else "Pick your language, then begin",
                style = AppType.MonoSmall,
                color = InkMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Replace the open attempt?", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "You still have an attempt in progress${activeExam?.title?.let { " — $it" } ?: ""}. Starting this one discards its saved answers.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "Your completed attempts and scores are never touched.",
                        style = AppType.BodyTiny,
                        color = IndigoText
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        scope.launch {
                            appVm.abandonExam()
                            start()
                        }
                    }
                ) {
                    Text("Start new", style = AppType.ButtonSmall, color = Indigo)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            confirmReplace = false
                            onStarted()
                        }
                    ) {
                        Text("Resume", style = AppType.ButtonSmall, color = Navy)
                    }
                    TextButton(onClick = { confirmReplace = false }) {
                        Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
                    }
                }
            }
        )
    }
}
