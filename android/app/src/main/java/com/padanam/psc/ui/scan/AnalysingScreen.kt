package com.padanam.psc.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.padanam.psc.AppContainer
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.EmptyStateCard
import com.padanam.psc.ui.components.LightButton
import com.padanam.psc.ui.components.MonoChip
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.ProgressTrack
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.IconFaint
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.IndigoWash
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.Orange
import com.padanam.psc.ui.theme.OrangeSoft
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Track
import com.padanam.psc.util.Format
import com.padanam.psc.util.ImageUtils
import com.padanam.psc.util.PreparedImage
import java.io.File
import kotlinx.coroutines.delay

/**
 * Progress screen shown while Gemini reads the captured pages and the set is
 * written to Room. The mockup's "keep this screen open" note is honest: the
 * upload lives in this ViewModel. A failure swaps the progress block for a
 * recovery card with retry, settings and cancel paths.
 */
@Composable
fun AnalysingScreen(
    container: AppContainer,
    onSaved: (String) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: AnalysingViewModel = viewModel(factory = AnalysingViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.start() }

    // Hold the finished state for a beat so 100% is visible before moving on.
    LaunchedEffect(state.stage, state.setId) {
        if (state.stage == "DONE") {
            val setId = state.setId ?: return@LaunchedEffect
            delay(700)
            onSaved(setId)
        }
    }

    val images = state.images
    val primary = images.firstOrNull()
    val isDone = state.stage == "DONE"
    val isError = state.stage == "ERROR"

    fun closeScreen() {
        if (!isDone) {
            // Nothing was saved, so the cache copies are no longer needed.
            images.forEach { image -> runCatching { File(image.path).delete() } }
            vm.cancel()
        }
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        TopBar(
            title = "Analysing",
            leading = {
                CircleAction(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close scan",
                    onClick = { closeScreen() }
                )
            },
            trailing = {
                CircleAction(
                    icon = Icons.Rounded.AutoAwesome,
                    contentDescription = "Scan settings",
                    onClick = onOpenSettings
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (primary == null) {
                EmptyStateCard(
                    icon = Icons.Rounded.PhotoCamera,
                    title = "No photo to analyse",
                    body = "The scanner did not hand over an image. Go back and take a photo, or import one from your gallery.",
                    primaryLabel = "Back to scanner",
                    onPrimary = { closeScreen() }
                )
            } else {
                FileCard(
                    primary = primary,
                    pageCount = images.size,
                    totalBytes = images.sumOf { it.sizeBytes },
                    state = state
                )

                if (isError) {
                    ErrorCard(
                        message = state.error ?: "The scan was interrupted.",
                        onRetry = { vm.retry() },
                        onOpenSettings = onOpenSettings,
                        onCancel = { closeScreen() }
                    )
                } else {
                    ProgressBlock(state = state)

                    if (isDone) {
                        SavedBanner(questionCount = state.questionCount)
                    }

                    StepList(state = state)

                    if (isDone) {
                        Text(
                            "Saved to your library · opening the result…",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                            color = Success,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "Usually ready in 3 seconds — keep this screen open",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
                            color = InkMuted.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    primary: PreparedImage,
    pageCount: Int,
    totalBytes: Long,
    state: AnalysingState
) {
    val modified = remember(primary.path) { primary.file.lastModified() }
    val status = when (state.stage) {
        "DONE" -> "SAVED"
        "ERROR" -> "PAUSED"
        else -> "${state.progress}%"
    }
    val statusBackground = when (state.stage) {
        "DONE" -> SuccessSoft
        "ERROR" -> DangerSoft
        else -> IndigoWash
    }
    val statusColor = when (state.stage) {
        "DONE" -> Success
        "ERROR" -> Danger
        else -> Indigo
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 74.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IndigoWash),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = primary.file,
                contentDescription = primary.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(SurfaceWhite.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                primary.displayName,
                style = AppType.BodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(ImageUtils.sizeLabel(totalBytes))
                    if (modified > 0L) append(" · ").append(Format.fileDate(modified))
                    if (pageCount > 1) append(" · ").append("$pageCount pages")
                },
                style = AppType.MonoMeta,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.stage == "READING" || state.stage == "MATCHING") {
                Text(
                    "English + Malayalam",
                    style = AppType.BodyTiny,
                    color = InkMuted.copy(alpha = 0.75f)
                )
            }
        }

        MonoChip(
            text = status,
            background = statusBackground,
            color = statusColor
        )
    }
}

@Composable
private fun ProgressBlock(state: AnalysingState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${state.progress}%",
                style = AppType.HeroTitle.copy(fontSize = 32.sp, lineHeight = 32.sp),
                color = Navy
            )
            Text(
                state.stageLabel,
                style = AppType.MonoSmall.copy(letterSpacing = 0.9.sp),
                color = Indigo,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        ProgressTrack(
            progress = state.progress / 100f,
            track = Track,
            fill = Indigo,
            height = 8.dp
        )

        Text(
            if (state.stage == "DONE") {
                "Saved. Opening the scan result…"
            } else {
                state.note
            },
            style = AppType.BodySmall,
            color = InkMuted
        )
    }
}

@Composable
private fun SavedBanner(questionCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SuccessSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Success),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = SurfaceWhite,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "Saved to your library",
                style = AppType.LabelSmallStrong,
                color = Success
            )
            Text(
                "$questionCount question${if (questionCount == 1) "" else "s"} · English and Malayalam",
                style = AppType.BodyTiny,
                color = InkMuted
            )
        }
    }
}

private enum class StepStatus { COMPLETE, ACTIVE, PENDING }

@Composable
private fun StepList(state: AnalysingState) {
    val stageIndex = when (state.stage) {
        "UPLOADING" -> 0
        "READING" -> 1
        "MATCHING" -> 2
        "SAVING" -> 3
        "DONE" -> 4
        else -> -1
    }
    val isError = state.stage == "ERROR"

    fun statusFor(index: Int): StepStatus = when {
        isError -> StepStatus.PENDING
        stageIndex > index -> StepStatus.COMPLETE
        stageIndex == index -> StepStatus.ACTIVE
        else -> StepStatus.PENDING
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
    ) {
        StepRow(
            label = if (state.images.size > 1) {
                "Uploading ${state.images.size} images"
            } else {
                "Uploading image"
            },
            meta = state.images.sumOf { it.sizeBytes }
                .takeIf { it > 0L }
                ?.let { ImageUtils.sizeLabel(it) }
                ?: "QUEUED",
            status = statusFor(0)
        )
        ThinDivider()
        StepRow(
            label = "Reading text",
            meta = if (statusFor(1) == StepStatus.COMPLETE) "DONE" else "EN + ML",
            status = statusFor(1)
        )
        ThinDivider()
        StepRow(
            label = "Matching options A–D",
            meta = if (statusFor(2) == StepStatus.COMPLETE) "VERIFIED" else "A–D",
            status = statusFor(2)
        )
        ThinDivider()
        StepRow(
            label = "Saving to database",
            meta = if (statusFor(3) == StepStatus.COMPLETE) "SAVED" else "JSON",
            status = statusFor(3)
        )
    }
}

@Composable
private fun StepRow(
    label: String,
    meta: String,
    status: StepStatus
) {
    val labelColor = when (status) {
        StepStatus.COMPLETE -> Navy
        StepStatus.ACTIVE -> Navy
        StepStatus.PENDING -> InkMuted
    }
    val labelWeight = when (status) {
        StepStatus.COMPLETE -> androidx.compose.ui.text.font.FontWeight.Medium
        StepStatus.ACTIVE -> androidx.compose.ui.text.font.FontWeight.SemiBold
        StepStatus.PENDING -> androidx.compose.ui.text.font.FontWeight.Normal
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        StepStatus.COMPLETE -> IndigoSoft
                        StepStatus.ACTIVE -> OrangeSoft
                        StepStatus.PENDING -> SurfaceMuted
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                StepStatus.COMPLETE -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(12.dp)
                )
                StepStatus.ACTIVE -> CircularProgressIndicator(
                    color = Orange,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(12.dp)
                )
                StepStatus.PENDING -> Icon(
                    Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = IconFaint,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Text(
            label,
            modifier = Modifier.weight(1f),
            style = AppType.Label.copy(fontWeight = labelWeight),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            meta,
            style = AppType.MonoSmall,
            color = when (status) {
                StepStatus.ACTIVE -> Orange
                StepStatus.COMPLETE -> InkMuted
                StepStatus.PENDING -> IconFaint
            },
            maxLines = 1
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DangerSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Scan paused",
                    style = AppType.SectionTitle,
                    color = Navy
                )
                Text(
                    "NOTHING WAS SAVED",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                    color = InkMuted
                )
            }
        }

        Text(
            message,
            style = AppType.BodySmall,
            color = InkMuted
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(IndigoWash)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "BEFORE RETRYING",
                style = AppType.MonoSmall.copy(letterSpacing = 0.7.sp),
                color = Indigo
            )
            Text(
                "Check that your Gemini key is set in Settings and that the photo is sharp, not cropped, and well lit.",
                style = AppType.BodyTiny,
                color = IndigoText
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            LightButton(
                text = "Try again",
                onClick = onRetry,
                icon = Icons.Rounded.Refresh,
                tint = SurfaceWhite,
                container = Indigo,
                height = 46.dp,
                modifier = Modifier.weight(1f)
            )
            LightButton(
                text = "Settings",
                onClick = onOpenSettings,
                icon = Icons.Rounded.Settings,
                tint = Navy,
                container = SurfaceMuted,
                height = 46.dp,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "Cancel this scan",
                style = AppType.LabelSmallStrong.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                ),
                color = InkMuted
            )
        }
    }
}
