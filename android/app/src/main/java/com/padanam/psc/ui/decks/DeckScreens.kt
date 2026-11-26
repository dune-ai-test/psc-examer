package com.padanam.psc.ui.decks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.model.DeckCard
import com.padanam.psc.data.model.DeckContinueInfo
import com.padanam.psc.data.model.DeckOverview
import com.padanam.psc.data.model.DeckResult
import com.padanam.psc.data.model.DeckSubtopic
import com.padanam.psc.data.model.DeckSummary
import com.padanam.psc.data.model.DeckTopic
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.DarkCardButton
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.LightButton
import com.padanam.psc.ui.components.MonoChip
import com.padanam.psc.ui.components.PageHeader
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.ProgressTrack
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoWash
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Malayalam
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.Orange
import com.padanam.psc.ui.theme.OrangeSoft
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessInk
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Track
import kotlinx.coroutines.launch

private val DeckGradient = listOf(
    Color(0xFF5B4BF6),
    Color(0xFF4232D8),
    Color(0xFF241C6D)
)
private val DeckLavender = Color(0xFFD6D0FF)
private val DeckMutedText = Color(0xFFEAE7FF)

private fun plural(count: Int, word: String): String =
    "$count $word${if (count == 1) "" else "s"}"

private fun pad(value: Int): String = value.toString().padStart(2, '0')

/* ── Deck library ─────────────────────────────────────────── */

@Composable
fun DeckLibraryScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenStudy: (String, String) -> Unit
) {
    val overview by appVm.deckOverview.collectAsStateWithLifecycle()
    val continueInfo by appVm.deckContinue.collectAsStateWithLifecycle()
    val cont = continueInfo
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        appVm.refreshRemoteDecks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        PageHeader(
            eyebrow = "Swipe decks",
            title = "Deck library",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            actions = {
                CircleAction(Icons.Rounded.MenuBook, "Question library", onOpenLibrary)
                CircleAction(Icons.Rounded.Close, "Close decks", onBack)
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (overview.cards == 0) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Layers,
                        title = "No deck cards yet",
                        body = "Every question in your library automatically becomes a deck card. " +
                            "Add questions to a set, or restore the demo sets to start swiping.",
                        primaryLabel = "Open library",
                        onPrimary = onOpenLibrary,
                        primaryIcon = Icons.Rounded.MenuBook
                    )
                }
            } else {
                item {
                    DeckHero {
                        HeroKicker("Swipe deck")
                        Text(
                            "One question.\nOne answer.",
                            style = AppType.HeroTitle.copy(fontSize = 29.sp, lineHeight = 31.sp),
                            color = OnDark
                        )
                        Text(
                            "Read the prompt, reveal the answer, then swipe right when you have it " +
                                "or left to see it again.",
                            style = AppType.BodySmall,
                            color = DeckMutedText.copy(alpha = 0.76f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            DeckStat(overview.cards, "cards")
                            DeckStat(overview.topicCount, "topics")
                            DeckStat("${overview.pct}%", "learned")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (cont != null) {
                                DarkCardButton(
                                    text = "Continue · ${cont.remaining} left",
                                    icon = Icons.Rounded.PlayArrow,
                                    onClick = { onOpenStudy(cont.mainTopic, cont.subTopic) },
                                    modifier = Modifier.weight(1f),
                                    filled = true
                                )
                            } else {
                                DarkCardButton(
                                    text = "Browse topics",
                                    icon = Icons.Rounded.Layers,
                                    onClick = { scope.launch { listState.animateScrollToItem(2) } },
                                    modifier = Modifier.weight(1f),
                                    filled = true
                                )
                            }
                            DarkCardButton(
                                text = "Library",
                                icon = Icons.Rounded.MenuBook,
                                onClick = onOpenLibrary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    SectionHeading(
                        title = "Main topics",
                        meta = plural(overview.topicCount, "topic")
                    )
                }
                items(overview.topics, key = { it.name }) { topic ->
                    TopicCard(topic = topic, onClick = { onOpenTopic(topic.name) })
                }
                item {
                    InfoNote(
                        text = "Deck cards use the correct option and explanation from each " +
                            "question. Your place in a round stays on this phone; learned cards " +
                            "sync with your account.",
                        icon = Icons.Rounded.Layers
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroKicker(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OnDark.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.Layers, null, tint = OnDark, modifier = Modifier.size(12.dp))
        Text(text.uppercase(), style = AppType.MonoChip, color = OnDark)
    }
}

@Composable
private fun DeckStat(value: Any, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            value.toString(),
            style = AppType.StatValue.copy(fontSize = 19.sp),
            color = OnDark
        )
        Text(label.uppercase(), style = AppType.MonoChip, color = DeckMutedText.copy(alpha = 0.6f))
    }
}

@Composable
private fun DeckHero(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(DeckGradient))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun TopicCard(topic: DeckTopic, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndigoWash),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Layers, null, tint = Indigo, modifier = Modifier.size(18.dp))
            }
            MonoChip(text = plural(topic.total, "card"))
        }
        Text(topic.name, style = AppType.SectionTitle, color = Navy)
        Text(
            "${plural(topic.subtopics.size, "subtopic")} · ${topic.learned} learned",
            style = AppType.BodySmall,
            color = InkMuted
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressTrack(
                progress = topic.pct / 100f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${topic.pct}%",
                style = AppType.MonoValue,
                color = if (topic.pct == 100) SuccessInk else Indigo
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                when {
                    topic.pct == 100 -> "All cards known"
                    topic.pct > 0 -> "Keep going"
                    else -> "Not started yet"
                },
                style = AppType.BodyTiny,
                color = InkMuted
            )
            Icon(Icons.Rounded.ChevronRight, null, tint = InkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/* ── Topic shelf ──────────────────────────────────────────── */

@Composable
fun DeckTopicScreen(
    appVm: AppViewModel,
    topicName: String,
    onBack: () -> Unit,
    onOpenStudy: (String, String) -> Unit
) {
    val overview by appVm.deckOverview.collectAsStateWithLifecycle()
    val session by appVm.deckSession.collectAsStateWithLifecycle()
    val summary by appVm.deckSummary.collectAsStateWithLifecycle()
    val topic = overview.topics.firstOrNull { it.name.equals(topicName.trim(), ignoreCase = true) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        appVm.refreshRemoteDecks()
    }

    val activeTopic = session != null &&
        session!!.mainTopic.equals(topic?.name.orEmpty(), ignoreCase = true) &&
        session!!.subTopic.isBlank()
    val topicLeft = if (activeTopic && summary != null && !summary!!.complete) {
        summary!!.total - summary!!.sorted
    } else {
        0
    }

    fun openOrStart(topicValue: String, sub: String) {
        scope.launch {
            val sameScope = session != null &&
                session!!.mainTopic.equals(topicValue, ignoreCase = true) &&
                session!!.subTopic.equals(sub, ignoreCase = true)
            val complete = sameScope && summary?.complete == true
            if (!sameScope || complete) {
                appVm.startDeckRound(topicValue, sub)
            }
            onOpenStudy(topicValue, sub)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        PageHeader(
            eyebrow = "Deck topic",
            title = topic?.name ?: "Deck topic",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            actions = {
                CircleAction(Icons.Rounded.ArrowBack, "All decks", onBack)
            }
        )

        if (topic == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Rounded.Layers,
                    title = "This deck topic is empty",
                    body = "The questions behind this topic may have moved. Open the deck library to pick another topic.",
                    primaryLabel = "All decks",
                    onPrimary = onBack,
                    primaryIcon = Icons.Rounded.ArrowBack
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DeckHero {
                    HeroKicker("${topic.learned} of ${topic.total} learned")
                    Text(topic.name, style = AppType.HeroTitle, color = OnDark)
                    Text(
                        "Choose a subtopic below, or review the whole topic as one longer deck.",
                        style = AppType.BodySmall,
                        color = DeckMutedText.copy(alpha = 0.76f)
                    )
                    ProgressTrack(
                        progress = topic.pct / 100f,
                        track = OnDark.copy(alpha = 0.16f),
                        fill = OnDark,
                        height = 7.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkCardButton(
                            text = if (topicLeft > 0) {
                                "Continue this topic · $topicLeft left"
                            } else {
                                "Review this topic"
                            },
                            icon = Icons.Rounded.PlayArrow,
                            onClick = { openOrStart(topic.name, "") },
                            modifier = Modifier.fillMaxWidth(),
                            filled = true
                        )
                        topic.subtopics.firstOrNull()?.let { first ->
                            DarkCardButton(
                                text = "Start with ${first.name}",
                                icon = Icons.Rounded.MenuBook,
                                onClick = { openOrStart(topic.name, first.name) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                SectionHeading(
                    title = "Subtopics",
                    meta = plural(topic.total, "card")
                )
            }

            items(topic.subtopics, key = { it.name }) { sub ->
                val subSession = session != null &&
                    session!!.mainTopic.equals(topic.name, ignoreCase = true) &&
                    session!!.subTopic.equals(sub.name, ignoreCase = true)
                val subSummary = if (subSession) summary else null
                SubtopicCard(
                    sub = sub,
                    inProgress = subSummary != null && !subSummary.complete,
                    done = subSummary?.complete == true,
                    left = subSummary?.let { it.total - it.sorted } ?: 0,
                    onClick = { onOpenStudy(topic.name, sub.name) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(IndigoWash)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Ready for the whole topic?", style = AppType.LabelSmallStrong, color = Navy)
                        Text(
                            "${plural(topic.total, "card")} · about ${maxOf(1, Math.round(topic.total * 10f / 60f))} min",
                            style = AppType.BodyTiny,
                            color = InkMuted
                        )
                    }
                    Text(
                        "Review",
                        style = AppType.ButtonSmall,
                        color = Indigo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { openOrStart(topic.name, "") }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtopicCard(
    sub: DeckSubtopic,
    inProgress: Boolean,
    done: Boolean,
    left: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sub.name, style = AppType.LabelSmallStrong, color = Navy, modifier = Modifier.weight(1f))
                MonoChip(
                    text = when {
                        inProgress -> "$left left"
                        done -> "Round done"
                        else -> "${sub.pct}%"
                    },
                    background = when {
                        inProgress -> IndigoSoft
                        done -> SuccessSoft
                        else -> SurfaceMuted
                    },
                    color = when {
                        inProgress -> Indigo
                        done -> SuccessInk
                        else -> InkMuted
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressTrack(
                    progress = sub.pct / 100f,
                    modifier = Modifier.weight(1f),
                    fill = if (done) Success else Indigo,
                    height = 5.dp
                )
                Text(plural(sub.total, "card"), style = AppType.MonoMeta, color = InkMuted)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = InkMuted, modifier = Modifier.size(18.dp))
    }
}

/* ── Focused runner ───────────────────────────────────────── */

@Composable
fun DeckStudyScreen(
    appVm: AppViewModel,
    mainTopic: String,
    subTopic: String,
    onClose: () -> Unit,
    onOpenTopic: (String) -> Unit
) {
    val summary by appVm.deckSummary.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(!deckGuideSeen) }

    LaunchedEffect(mainTopic, subTopic) {
        appVm.refreshRemoteDecks()
        val ok = appVm.ensureDeckRound(mainTopic, subTopic)
        failed = !ok
        ready = true
    }

    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()

        if (!ready) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo)
            }
            return@Column
        }

        val live = summary
        if (failed || live == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Rounded.Layers,
                    title = "No cards in this deck",
                    body = "Add questions to a set with this topic, then come back to swipe.",
                    primaryLabel = "Back",
                    onPrimary = onClose,
                    primaryIcon = Icons.Rounded.ArrowBack
                )
            }
            return@Column
        }

        RunnerTopBar(
            summary = live,
            onClose = onClose,
            onUndo = { scope.launch { appVm.undoDeckCard() } },
            onGuide = { guideOpen = true }
        )

        if (live.complete) {
            Completion(
                summary = live,
                onReviewMissed = {
                    scope.launch {
                        appVm.startDeckRound(
                            live.session.mainTopic,
                            live.session.subTopic,
                            keys = live.missedKeys,
                            round = live.session.round + 1
                        )
                    }
                },
                onShuffle = {
                    scope.launch {
                        appVm.startDeckRound(
                            live.session.mainTopic,
                            live.session.subTopic,
                            shuffle = true,
                            round = live.session.round + 1
                        )
                    }
                },
                onBack = { onOpenTopic(live.session.mainTopic) }
            )
        } else {
            val card = live.current
            if (card == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo)
                }
            } else {
                RunnerCard(
                    appVm = appVm,
                    summary = live,
                    card = card,
                    language = settings.language,
                    malayalam = card.usesMalayalam(settings.language)
                )
            }
        }
    }

    if (guideOpen) {
        DeckGuideDialog(onDismiss = {
            deckGuideSeen = true
            guideOpen = false
        })
    }
}

/** Shows the gesture guide once per app process, like the web app's first round. */
private var deckGuideSeen = false

@Composable
private fun RunnerTopBar(
    summary: DeckSummary,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onGuide: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleAction(Icons.Rounded.Close, "Close deck", onClose)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    pad(if (summary.complete) summary.total else summary.position),
                    style = AppType.HeroTitle.copy(fontSize = 25.sp),
                    color = Navy
                )
                Text("/ ${pad(summary.total)}", style = AppType.MonoValue, color = InkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.sorted > 0) {
                    CircleAction(Icons.Rounded.Undo, "Undo last swipe", onUndo)
                }
                CircleAction(Icons.Rounded.HelpOutline, "How to swipe", onGuide)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "SWIPE DECK · ${summary.session.subTopic.ifBlank { summary.session.mainTopic }}",
                style = AppType.MonoChip,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            MonoChip(text = "Round ${pad(summary.session.round)}")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            summary.cards.forEach { card ->
                val result = summary.results[card.key]
                val current = summary.current?.key == card.key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (current) 7.dp else 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            when {
                                result == DeckResult.GOT_IT -> Success
                                result == DeckResult.AGAIN -> Orange
                                current -> Indigo
                                else -> Track
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RunnerCard(
    appVm: AppViewModel,
    summary: DeckSummary,
    card: DeckCard,
    language: String,
    malayalam: Boolean
) {
    val scope = rememberCoroutineScope()
    var revealedKey by remember { mutableStateOf("") }
    var dragX by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    val revealed = revealedKey == card.key

    LaunchedEffect(card.key) {
        dragX = 0f
    }

    fun sort(result: String) {
        if (busy) return
        busy = true
        revealedKey = ""
        dragX = 0f
        scope.launch {
            appVm.sortDeckCard(result)
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragX
                    rotationZ = dragX / 55f
                }
                .pointerInput(card.key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val amount = dragX
                            dragX = 0f
                            if (amount <= -105f) sort(DeckResult.AGAIN)
                            else if (amount >= 105f) sort(DeckResult.GOT_IT)
                        },
                        onDragCancel = { dragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragX = (dragX + dragAmount).coerceIn(-220f, 220f)
                        }
                    )
                }
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceWhite)
                .clickable { revealedKey = if (revealed) "" else card.key }
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(DeckGradient))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        card.subTopic.uppercase(),
                        style = AppType.MonoChip,
                        color = DeckLavender,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "CARD ${pad(summary.position)} / ${pad(summary.total)}",
                        style = AppType.MonoChip,
                        color = OnDark.copy(alpha = 0.72f)
                    )
                }

                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("QUESTION ${pad(summary.position)}", style = AppType.MonoChip, color = InkMuted)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                null,
                                tint = Indigo,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (revealed) "Tap to hide" else "Tap to reveal",
                                style = AppType.BodyTiny,
                                color = Indigo
                            )
                        }
                    }

                    Text(
                        card.stem(language),
                        style = if (malayalam) {
                            AppType.QuestionLarge.copy(fontFamily = Malayalam)
                        } else {
                            AppType.QuestionLarge
                        },
                        color = Navy
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (revealed) SuccessSoft else SurfaceMuted)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ANSWER", style = AppType.MonoChip, color = if (revealed) SuccessInk else InkMuted)
                            MonoChip(
                                text = if (revealed) "Revealed" else "Hidden",
                                background = if (revealed) OnDark.copy(alpha = 0.7f) else SurfaceWhite,
                                color = if (revealed) SuccessInk else InkMuted
                            )
                        }
                        if (revealed) {
                            Text(
                                "${card.answerLetter()}. ${card.answer(language)}",
                                style = if (malayalam) {
                                    AppType.Label.copy(fontFamily = Malayalam, fontWeight = FontWeight.SemiBold)
                                } else {
                                    AppType.Label.copy(fontWeight = FontWeight.SemiBold)
                                },
                                color = Navy
                            )
                            val why = card.explanation(language)
                            if (why.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("WHY", style = AppType.MonoChip, color = SuccessInk)
                                    Text(
                                        why,
                                        style = if (malayalam) {
                                            AppType.BodySmall.copy(fontFamily = Malayalam)
                                        } else {
                                            AppType.BodySmall
                                        },
                                        color = InkMuted
                                    )
                                }
                            }
                        } else {
                            Text(
                                "Check yourself before sorting the card.",
                                style = AppType.BodySmall,
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("← AGAIN", style = AppType.MonoChip, color = Orange)
            Text("SWIPE OR TAP", style = AppType.MonoChip, color = InkMuted)
            Text("GOT IT →", style = AppType.MonoChip, color = SuccessInk)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeckAction(
                label = "Again",
                icon = Icons.Rounded.ArrowBack,
                color = Orange,
                container = OrangeSoft,
                onClick = { sort(DeckResult.AGAIN) },
                modifier = Modifier.weight(1f)
            )
            DeckAction(
                label = if (revealed) "Hide" else "Reveal",
                icon = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                color = Indigo,
                container = IndigoSoft,
                onClick = { revealedKey = if (revealed) "" else card.key },
                modifier = Modifier.weight(1f)
            )
            DeckAction(
                label = "Got it",
                icon = Icons.Rounded.Check,
                color = SuccessInk,
                container = SuccessSoft,
                onClick = { sort(DeckResult.GOT_IT) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun DeckAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = AppType.ButtonSmall.copy(fontSize = 11.5.sp), color = color)
    }
}

/* ── Completion ───────────────────────────────────────────── */

@Composable
private fun ColumnScope.Completion(
    summary: DeckSummary,
    onReviewMissed: () -> Unit,
    onShuffle: () -> Unit,
    onBack: () -> Unit
) {
    val title = summary.session.subTopic.ifBlank { summary.session.mainTopic }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        val trackColor = Track
        val recallColor = if (summary.recall >= 70) Success else Indigo
        Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = recallColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (summary.recall / 100f),
                    useCenter = false,
                    style = Stroke(width = stroke)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${summary.recall}%",
                    style = AppType.HeroTitle.copy(fontSize = 32.sp),
                    color = Navy
                )
                Text("RECALL", style = AppType.MonoChip, color = InkMuted)
            }
        }

        Text(
            "ROUND COMPLETE · $title",
            style = AppType.Eyebrow,
            color = Indigo,
            textAlign = TextAlign.Center
        )
        Text("Deck sorted!", style = AppType.HeroTitle.copy(fontSize = 30.sp), color = Navy)
        Text(
            "You cleared ${plural(summary.total, "prompt")} — ${summary.gotIt} got it" +
                if (summary.again > 0) ", ${summary.again} marked again." else ".",
            style = AppType.BodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceWhite)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CompletionStat(summary.gotIt, "Got it")
            CompletionStat(summary.again, "Again")
            CompletionStat("${summary.recall}%", "Recall")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (summary.missed.isEmpty()) SuccessSoft else IndigoWash)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                if (summary.missed.isEmpty()) "Perfect recall" else "Review the missed cards to lock them in",
                style = AppType.LabelSmallStrong,
                color = if (summary.missed.isEmpty()) SuccessInk else Indigo,
                textAlign = TextAlign.Center
            )
            Text(
                if (summary.missed.isEmpty()) {
                    "Every card in this round is known. Nicely done."
                } else {
                    "${plural(summary.missed.size, "card")} will come back in a focused round."
                },
                style = AppType.BodyTiny,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }

        if (summary.missed.isNotEmpty()) {
            PrimaryButton(
                text = "Review the ${plural(summary.missed.size, "card")} you missed",
                onClick = onReviewMissed,
                icon = Icons.Rounded.Refresh
            )
        }
        LightButton(
            text = "Shuffle and go again",
            onClick = onShuffle,
            icon = Icons.Rounded.Shuffle,
            container = SurfaceWhite
        )
        LightButton(
            text = "Back to ${summary.session.mainTopic}",
            onClick = onBack,
            icon = Icons.Rounded.ChevronRight,
            container = SurfaceMuted,
            height = 46.dp
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CompletionStat(value: Any, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = AppType.StatValue, color = Navy)
        Text(label.uppercase(), style = AppType.MonoChip, color = InkMuted)
    }
}

/* ── Guide dialog ─────────────────────────────────────────── */

@Composable
private fun DeckGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Two swipes, one tap", style = AppType.Question, color = Navy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("That is the whole deck.", style = AppType.BodySmall, color = InkMuted)
                GuideRow(
                    icon = Icons.Rounded.ArrowBack,
                    tint = Orange,
                    title = "Swipe left — Again",
                    body = "See it once more in the missed pile."
                )
                GuideRow(
                    icon = Icons.Rounded.Visibility,
                    tint = Indigo,
                    title = "Tap the card — Reveal",
                    body = "Check the answer before you sort it."
                )
                GuideRow(
                    icon = Icons.Rounded.Check,
                    tint = Success,
                    title = "Swipe right — Got it",
                    body = "Lock the card into your known pile."
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Undo, null, tint = InkMuted, modifier = Modifier.size(16.dp))
                    Text("Changed your mind? Undo any swipe.", style = AppType.BodyTiny, color = InkMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Start swiping", style = AppType.ButtonSmall, color = Indigo)
            }
        }
    )
}

@Composable
private fun GuideRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    body: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = AppType.LabelSmallStrong, color = Navy)
            Text(body, style = AppType.BodyTiny, color = InkMuted)
        }
    }
}

/* ── Home strip ───────────────────────────────────────────── */

@Composable
fun DeckHomeCard(
    overview: DeckOverview,
    continueInfo: DeckContinueInfo?,
    onBrowse: () -> Unit,
    onOpenLibrary: () -> Unit,
    onContinue: (DeckContinueInfo) -> Unit
) {
    if (overview.cards <= 0) return
    DeckHero {
        HeroKicker("Swipe deck")
        Text("One question. One answer.", style = AppType.HeroTitle, color = OnDark)
        Text(
            if (continueInfo != null) {
                if (continueInfo.sorted > 0) {
                    "You are ${continueInfo.sorted} of ${continueInfo.total} cards through " +
                        "${continueInfo.title}. Pick up where you left off."
                } else {
                    "A fresh ${continueInfo.total}-card round in ${continueInfo.title} is ready when you are."
                }
            } else {
                "Read the prompt, reveal the answer, then sort the card. " +
                    "${overview.cards} cards are ready from your library."
            },
            style = AppType.BodySmall,
            color = DeckMutedText.copy(alpha = 0.76f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            DeckStat(overview.cards, "cards")
            DeckStat(overview.topicCount, "topics")
            DeckStat("${overview.pct}%", "learned")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkCardButton(
                text = when {
                    continueInfo == null -> "Browse topics"
                    continueInfo.sorted > 0 -> "Continue · ${continueInfo.remaining} left"
                    else -> "Start · ${continueInfo.total} cards"
                },
                icon = if (continueInfo == null) Icons.Rounded.Layers else Icons.Rounded.PlayArrow,
                onClick = { if (continueInfo == null) onBrowse() else onContinue(continueInfo) },
                modifier = Modifier.weight(1f),
                filled = true
            )
            DarkCardButton(
                text = if (continueInfo == null) "Library" else "All decks",
                icon = if (continueInfo == null) Icons.Rounded.MenuBook else Icons.Rounded.Layers,
                onClick = if (continueInfo == null) onOpenLibrary else onBrowse,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
