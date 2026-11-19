package com.padanam.psc.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.padanam.psc.data.model.SetSummary
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.ChoiceChip
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.DarkCardButton
import com.padanam.psc.ui.components.DarkGradientCard
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.PageHeader
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.SearchField
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.SetRow
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.util.Format

private const val FILTER_ALL = "all"
private const val FILTER_SCANNED = "scanned"
private const val FILTER_SAVED = "saved"
private const val FILTER_DUE = "due"

@Composable
fun LibraryScreen(
    appVm: AppViewModel,
    canScan: Boolean,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onOpenSet: (String) -> Unit
) {
    val sets by appVm.sets.collectAsStateWithLifecycle()
    val questionCount by appVm.questionCount.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var subjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showSubjects by remember { mutableStateOf(false) }

    fun isDue(summary: SetSummary): Boolean =
        summary.attemptCount == 0 || (summary.bestScore ?: 0) < 60

    val scannedCount = sets.count { it.set.source != "demo" }
    val savedCount = sets.count { it.attemptCount > 0 }
    val dueCount = sets.count(::isDue)
    val subjects = sets.map { it.subject }.distinct().sorted()

    val filtered = sets.filter { summary ->
        val matchesQuery = query.isBlank() ||
            summary.title.contains(query, ignoreCase = true) ||
            summary.set.titleMl.contains(query, ignoreCase = true) ||
            summary.subject.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            FILTER_SCANNED -> summary.set.source != "demo"
            FILTER_SAVED -> summary.attemptCount > 0
            FILTER_DUE -> isDue(summary)
            else -> true
        }
        val matchesSubject = subjectFilter == null || summary.subject == subjectFilter
        matchesQuery && matchesFilter && matchesSubject
    }

    val week = filtered.filter {
        System.currentTimeMillis() - it.createdAt < 7L * 24 * 60 * 60 * 1000
    }
    val earlier = filtered - week.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                PageHeader(eyebrow = "Your question bank", title = "Library") {
                    CircleAction(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Clear search",
                        onClick = { query = "" },
                        size = 40.dp
                    )
                    CircleAction(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Subject filters",
                        onClick = { showSubjects = !showSubjects },
                        size = 40.dp,
                        container = if (showSubjects) IndigoSoft else SurfaceWhite,
                        tint = if (showSubjects) Indigo else Navy
                    )
                }
            }

            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search sets, subjects, tags"
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        text = "All ${sets.size}",
                        selected = filter == FILTER_ALL,
                        onClick = { filter = FILTER_ALL }
                    )
                    ChoiceChip(
                        text = "Scanned $scannedCount",
                        selected = filter == FILTER_SCANNED,
                        onClick = { filter = FILTER_SCANNED }
                    )
                    ChoiceChip(
                        text = "Saved $savedCount",
                        selected = filter == FILTER_SAVED,
                        onClick = { filter = FILTER_SAVED }
                    )
                    ChoiceChip(
                        text = "Due $dueCount",
                        selected = filter == FILTER_DUE,
                        onClick = { filter = FILTER_DUE }
                    )
                }
            }

            if (showSubjects && subjects.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "FILTER BY SUBJECT",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                            color = InkMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChoiceChip(
                                text = "Any subject",
                                selected = subjectFilter == null,
                                onClick = { subjectFilter = null }
                            )
                            subjects.forEach { subject ->
                                ChoiceChip(
                                    text = subject,
                                    selected = subjectFilter == subject,
                                    onClick = {
                                        subjectFilter = if (subjectFilter == subject) null else subject
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (canScan) {
                item {
                    ScanCard(
                        setCount = sets.size,
                        onScan = onScan,
                        onImport = onImport
                    )
                }
            }

            if (sets.isEmpty()) {
                item {
                    if (canScan) {
                        EmptyStateCard(
                            icon = Icons.Rounded.DocumentScanner,
                            title = "No question sets yet",
                            body = "Scan a photo of a question sheet and every question with its options is saved here automatically.",
                            primaryLabel = "Scan your first question",
                            onPrimary = onScan,
                            primaryIcon = Icons.Rounded.DocumentScanner,
                            secondaryLabel = "Import from photos",
                            onSecondary = onImport,
                            secondaryIcon = Icons.Rounded.Image
                        )
                    } else {
                        EmptyStateCard(
                            icon = Icons.Rounded.CloudQueue,
                            title = "Waiting for your library",
                            body = "Your administrator assigns question sets to this account. Open Settings and press Sync now to check for new sets."
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(IndigoSoft)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            null,
                            tint = Indigo,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            if (canScan) {
                                "One photo can hold up to 5 questions — we split them for you."
                            } else {
                                "Assigned sets arrive after you sign in and sync your account."
                            },
                            style = AppType.BodySmall,
                            color = IndigoText
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceWhite)
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("No sets match", style = AppType.SectionTitle, color = Navy)
                        Text(
                            "Try a different search or filter.",
                            style = AppType.BodySmall,
                            color = InkMuted
                        )
                        Text(
                            "Clear filters",
                            style = AppType.LabelSmallStrong,
                            color = Indigo,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(IndigoSoft)
                                .clickable {
                                    query = ""
                                    filter = FILTER_ALL
                                    subjectFilter = null
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                if (week.isNotEmpty()) {
                    item { SectionHeading(title = "This week", meta = "${week.size} sets") }
                    items(week, key = { it.id }) { summary ->
                        SetRow(
                            summary = summary,
                            isNew = summary.attemptCount == 0 &&
                                System.currentTimeMillis() - summary.createdAt < 6L * 60 * 60 * 1000,
                            onClick = { onOpenSet(summary.id) }
                        )
                    }
                }
                if (earlier.isNotEmpty()) {
                    item { SectionHeading(title = "Earlier", meta = "${earlier.size} sets") }
                    items(earlier, key = { it.id }) { summary ->
                        SetRow(
                            summary = summary,
                            isNew = false,
                            onClick = { onOpenSet(summary.id) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${sets.size} sets · ${Format.number(questionCount)} questions",
                        style = AppType.MonoSmall,
                        color = InkMuted
                    )
                    Text(
                        if (canScan) "ADMIN LIBRARY" else "ASSIGNED LIBRARY",
                        style = AppType.MonoSmall.copy(letterSpacing = 0.5.sp),
                        color = InkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanCard(
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
                    .background(SurfaceWhite.copy(alpha = 0.1f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Rounded.DocumentScanner,
                    null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(13.dp)
                )
                Text("AI capture", style = AppType.MonoChip, color = SurfaceWhite)
            }
            Text(
                when {
                    setCount == 0 -> "READY"
                    else -> "$setCount SETS"
                },
                style = AppType.MonoSmall.copy(letterSpacing = 0.5.sp),
                color = SurfaceWhite.copy(alpha = 0.5f)
            )
        }
        Text(
            "Photograph a question.\nGet every option.",
            style = AppType.HeroTitle,
            color = SurfaceWhite
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DarkCardButton(
                text = "Scan question",
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
