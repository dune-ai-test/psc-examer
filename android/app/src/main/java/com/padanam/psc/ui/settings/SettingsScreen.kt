package com.padanam.psc.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.BuildConfig
import com.padanam.psc.data.model.Lang
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.AppTextField
import com.padanam.psc.ui.components.ChoiceChip
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.LightButton
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.PadanamToggle
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.SettingsRow
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Hairline
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.util.Format

@Composable
fun SettingsScreen(
    appVm: AppViewModel,
    onBack: () -> Unit
) {
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val sets by appVm.sets.collectAsStateWithLifecycle()
    val attempts by appVm.attempts.collectAsStateWithLifecycle()
    val questionCount by appVm.questionCount.collectAsStateWithLifecycle()
    val aiOp by appVm.aiOp.collectAsStateWithLifecycle()
    val dbOp by appVm.dbOp.collectAsStateWithLifecycle()
    val syncOp by appVm.syncOp.collectAsStateWithLifecycle()

    var geminiKey by rememberSaveable(settings.geminiKey) { mutableStateOf(settings.geminiKey) }
    var geminiModel by rememberSaveable(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
    var showKey by remember { mutableStateOf(false) }

    var neonUrl by rememberSaveable(settings.neonUrl) { mutableStateOf(settings.neonUrl) }
    var neonEndpoint by rememberSaveable(settings.neonEndpoint) { mutableStateOf(settings.neonEndpoint) }
    var autoSync by rememberSaveable(settings.autoSync) { mutableStateOf(settings.autoSync) }

    var name by rememberSaveable(settings.name) { mutableStateOf(settings.name) }
    var language by rememberSaveable(settings.language) { mutableStateOf(settings.language) }
    var goal by rememberSaveable(settings.dailyGoal) { mutableIntStateOf(settings.dailyGoal) }
    var reminders by rememberSaveable(settings.reminders) { mutableStateOf(settings.reminders) }

    var resetOpen by remember { mutableStateOf(false) }
    var clearNeon by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        TopBar(title = "Settings", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Settings",
                        style = AppType.HeroTitle.copy(fontSize = 26.sp),
                        color = Navy
                    )
                    Text(
                        "Keys, backup and study defaults. Everything stays on this phone.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            }

            item {
                InfoNote(
                    text = "The app is offline-first. Room holds your library; Neon is an optional second copy. Gemini is only called while a scan is running.",
                    icon = Icons.Rounded.Lock
                )
            }

            // ------------------------------------------------------------ AI
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    SettingHeader(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AI extraction",
                        subtitle = "Gemini vision · English + Malayalam",
                        status = when {
                            aiOp.busy -> "TESTING"
                            settings.aiConfigured -> "READY"
                            else -> "KEY NEEDED"
                        },
                        statusOk = settings.aiConfigured && !aiOp.busy
                    )
                    AppTextField(
                        label = "Gemini API key",
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        placeholder = "Paste your key from Google AI Studio",
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            Icon(
                                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key",
                                tint = InkMuted,
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .clickable { showKey = !showKey }
                            )
                        },
                        helper = "Get a free key at aistudio.google.com. It is stored in app preferences only."
                    )
                    AppTextField(
                        label = "Model",
                        value = geminiModel,
                        onValueChange = { geminiModel = it },
                        placeholder = "gemini-2.5-flash",
                        helper = "gemini-2.5-flash is the safe default. Use a model available to your key."
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LightButton(
                            text = "Save key",
                            onClick = {
                                appVm.saveAi(geminiKey, geminiModel)
                                appVm.clearAiStatus()
                            },
                            icon = Icons.Rounded.Check,
                            modifier = Modifier.weight(1f),
                            height = 50.dp
                        )
                        PrimaryButton(
                            text = if (aiOp.busy) "Testing…" else "Test",
                            onClick = {
                                appVm.clearAiStatus()
                                appVm.testGemini(geminiKey, geminiModel)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !aiOp.busy,
                            height = 50.dp
                        )
                    }
                    OperationMessage(
                        busy = aiOp.busy,
                        message = aiOp.message,
                        success = aiOp.success,
                        busyText = "Testing your Gemini key…"
                    )
                }
            }

            // ---------------------------------------------------------- NEON
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    SettingHeader(
                        icon = Icons.Rounded.CloudQueue,
                        title = "Neon backup",
                        subtitle = "Questions and every attempt score",
                        status = when {
                            dbOp.busy || syncOp.busy -> "WORKING"
                            settings.neonConfigured -> "CONNECTED"
                            else -> "LOCAL ONLY"
                        },
                        statusOk = settings.neonConfigured && !dbOp.busy && !syncOp.busy
                    )
                    AppTextField(
                        label = "Connection string",
                        value = neonUrl,
                        onValueChange = { neonUrl = it },
                        placeholder = "postgresql://user:password@ep-…neon.tech/db?sslmode=require",
                        singleLine = false,
                        minLines = 2,
                        helper = "The pooled connection string from your Neon dashboard."
                    )
                    AppTextField(
                        label = "HTTP endpoint (optional)",
                        value = neonEndpoint,
                        onValueChange = { neonEndpoint = it },
                        placeholder = "https://api.…neon.tech/sql",
                        helper = "Leave blank to derive api.<region>.neon.tech from the connection string."
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Auto-sync after saving", style = AppType.Label, color = Navy)
                            Text(
                                "Pushes new sets and attempts in the background",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                        PadanamToggle(checked = autoSync, onCheckedChange = { autoSync = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LightButton(
                            text = "Save",
                            onClick = {
                                appVm.saveDatabase(neonUrl, neonEndpoint, autoSync)
                                appVm.clearDbStatus()
                            },
                            icon = Icons.Rounded.Check,
                            modifier = Modifier.weight(1f),
                            height = 50.dp
                        )
                        PrimaryButton(
                            text = if (dbOp.busy) "Testing…" else "Test",
                            onClick = {
                                appVm.clearDbStatus()
                                appVm.testNeon(neonUrl, neonEndpoint)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !dbOp.busy,
                            height = 50.dp
                        )
                    }
                    LightButton(
                        text = if (syncOp.busy) "Syncing…" else "Sync now",
                        onClick = { appVm.syncNow() },
                        icon = Icons.Rounded.Refresh,
                        container = IndigoSoft,
                        tint = Indigo,
                        height = 46.dp
                    )
                    OperationMessage(
                        busy = dbOp.busy,
                        message = dbOp.message,
                        success = dbOp.success,
                        busyText = "Connecting to Neon…"
                    )
                    if (!dbOp.busy && syncOp.message.isNotBlank()) {
                        OperationMessage(
                            busy = syncOp.busy,
                            message = syncOp.message,
                            success = syncOp.success,
                            busyText = "Syncing with Neon…"
                        )
                    }
                    Text(
                        "Last sync · ${if (settings.lastSyncAt > 0L) Format.relativeTime(settings.lastSyncAt) else "never"}",
                        style = AppType.MonoSmall,
                        color = InkMuted
                    )
                    if (!settings.neonConfigured) {
                        Text(
                            "The tables are created automatically on the first successful sync. The SQL is also in android/neon/schema.sql.",
                            style = AppType.BodyTiny,
                            color = InkMuted
                        )
                    }
                }
            }

            // --------------------------------------------------------- STUDY
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    SettingHeader(
                        icon = Icons.Rounded.Settings,
                        title = "Study defaults",
                        subtitle = "Your name, language and daily target",
                        status = null,
                        statusOk = true
                    )
                    AppTextField(
                        label = "Your name",
                        value = name,
                        onValueChange = { name = it.take(40) },
                        placeholder = "Maya"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "QUESTION FORMAT",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                            color = InkMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip(
                                text = "English first",
                                selected = language == Lang.EN,
                                onClick = { language = Lang.EN }
                            )
                            ChoiceChip(
                                text = "Malayalam first",
                                selected = language == Lang.ML,
                                onClick = { language = Lang.ML }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Daily goal", style = AppType.Label, color = Navy)
                            Text("$goal questions per day", style = AppType.BodyTiny, color = InkMuted)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StepButton("−") { goal = (goal - 5).coerceIn(5, 200) }
                            StepButton("+") { goal = (goal + 5).coerceIn(5, 200) }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Study reminders", style = AppType.Label, color = Navy)
                            Text("One notification at 7pm", style = AppType.BodyTiny, color = InkMuted)
                        }
                        PadanamToggle(checked = reminders, onCheckedChange = { reminders = it })
                    }
                    PrimaryButton(
                        text = "Save study defaults",
                        onClick = {
                            appVm.saveProfile(name, language, goal, reminders)
                        },
                        icon = Icons.Rounded.Check
                    )
                }
            }

            // ------------------------------------------------------- STORAGE
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                null,
                                tint = Navy,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your library", style = AppType.Label, color = Navy, maxLines = 1)
                            Text(
                                "${sets.size} sets · ${Format.number(questionCount)} questions · ${attempts.size} attempts",
                                style = AppType.BodyTiny,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    ThinDivider()
                    SettingsRow(
                        label = "Export library",
                        value = "JSON",
                        onClick = { appVm.exportLibrary() }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Reset library",
                        destructive = true,
                        onClick = {
                            clearNeon = false
                            resetOpen = true
                        }
                    )
                }
            }

            // --------------------------------------------------------- ABOUT
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    SettingsRow(label = "App version", value = "v${BuildConfig.VERSION_NAME}")
                    ThinDivider()
                    SettingsRow(label = "Package", value = "com.padanam.psc")
                    ThinDivider()
                    SettingsRow(label = "Questions", value = "Gemini + Room + Neon")
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DangerSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = Danger,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        "Never share an APK with personal Gemini or Neon keys embedded. This build is for one person.",
                        style = AppType.BodyTiny,
                        color = Danger
                    )
                }
            }
        }
    }

    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Reset library?", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This removes every set, question, attempt score and the active exam from this phone. Your Gemini and Neon settings are kept.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Also clear the Neon copy", style = AppType.Label, color = Navy)
                            Text(
                                "Deletes every row from your Neon tables",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                        PadanamToggle(checked = clearNeon, onCheckedChange = { clearNeon = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appVm.resetLibrary(clearNeon)
                        resetOpen = false
                    }
                ) {
                    Text("Reset", style = AppType.ButtonSmall, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetOpen = false }) {
                    Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        )
    }
}

@Composable
private fun SettingHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    status: String?,
    statusOk: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(IndigoSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Indigo, modifier = Modifier.size(17.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = AppType.SectionTitle.copy(fontSize = 14.sp), color = Navy)
            Text(
                subtitle,
                style = AppType.BodyTiny,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (status != null) {
            Text(
                status,
                style = AppType.MonoChip,
                color = if (statusOk) Success else InkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (statusOk) SuccessSoft else SurfaceMuted)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun OperationMessage(
    busy: Boolean,
    message: String,
    success: Boolean,
    busyText: String
) {
    if (!busy && message.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    busy -> SurfaceMuted
                    success -> SuccessSoft
                    else -> DangerSoft
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Indigo,
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (success) Success else Danger)
            )
        }
        Text(
            if (busy) busyText else message,
            style = AppType.BodyTiny,
            color = when {
                busy -> InkMuted
                success -> Success
                else -> Danger
            }
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(SurfaceMuted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.SectionTitle, color = Navy)
    }
}
