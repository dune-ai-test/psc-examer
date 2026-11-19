package com.padanam.psc.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.BuildConfig
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.AppTextField
import com.padanam.psc.ui.components.AttemptRow
import com.padanam.psc.ui.components.Avatar
import com.padanam.psc.ui.components.ChoiceChip
import com.padanam.psc.ui.components.CircleAction
import com.padanam.psc.ui.components.DarkCardButton
import com.padanam.psc.ui.components.DarkGradientCard
import com.padanam.psc.ui.components.PageHeader
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.PadanamToggle
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.SettingsRow
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessBright
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.util.Format
import java.time.LocalDate

@Composable
fun ProfileScreen(
    appVm: AppViewModel,
    canManage: Boolean,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAttempt: (String) -> Unit
) {
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val session by appVm.session.collectAsStateWithLifecycle()
    val sets by appVm.sets.collectAsStateWithLifecycle()
    val attempts by appVm.attempts.collectAsStateWithLifecycle()
    val questionCount by appVm.questionCount.collectAsStateWithLifecycle()
    val syncOp by appVm.syncOp.collectAsStateWithLifecycle()
    val backupOp by appVm.backupOp.collectAsStateWithLifecycle()

    var editOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    var goalOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    val streak = Format.attemptStreak(attempts)
    val languageValue = if (settings.language == Lang.ML) "Malayalam first" else "English first"
    val lastSync = if (settings.lastSyncAt > 0L) {
        Format.relativeTime(settings.lastSyncAt)
    } else {
        "Never synced"
    }
    val account = session?.user

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
                PageHeader(eyebrow = "Account", title = "Profile") {
                    CircleAction(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        size = 40.dp
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Avatar(name = settings.name, size = 54.dp, fontSize = 15)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                settings.name,
                                style = AppType.SectionTitle.copy(fontSize = 20.sp, lineHeight = 25.sp),
                                color = Navy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                settings.target.ifBlank { "Kerala PSC" } +
                                    settings.examDate.takeIf { it.isNotBlank() }
                                        ?.let { date ->
                                            val days = Format.daysUntil(date)
                                            " · ${Format.examDateLabel(date)}" +
                                                (days?.takeIf { it >= 0 }?.let { " · ${it}d" } ?: "")
                                        }
                                        .orEmpty(),
                                style = AppType.BodyTiny,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Personal study space · $languageValue",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SurfaceMuted)
                                .clickable { editOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit profile",
                                tint = Navy,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    ThinDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProfileStat(sets.size.toString(), "Sets")
                        ProfileStat(Format.number(questionCount), "Questions")
                        ProfileStat(streak.toString(), "Day streak")
                    }
                }
            }

            item {
                DarkGradientCard {
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
                                if (settings.serverConfigured) Icons.Rounded.CloudDone else Icons.Rounded.Lock,
                                null,
                                tint = if (settings.serverConfigured) SuccessBright else SurfaceWhite.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "Account sync",
                                style = AppType.MonoChip,
                                color = SurfaceWhite
                            )
                        }
                        Text(
                            if (settings.serverConfigured) "CONNECTED" else "NOT SET UP",
                            style = AppType.MonoSmall.copy(letterSpacing = 0.5.sp),
                            color = if (settings.serverConfigured) SuccessBright else SurfaceWhite.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        if (settings.serverConfigured) {
                            "Questions, every attempt score and your profile sync through the Padanam server."
                        } else {
                            "Add the Padanam site address in Settings to sync this account."
                        },
                        style = AppType.BodySmall,
                        color = SurfaceWhite.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (settings.serverConfigured) "Last sync · $lastSync" else "Local only · ${sets.size} sets",
                            style = AppType.MonoSmall,
                            color = SurfaceWhite.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        DarkCardButton(
                            text = if (settings.serverConfigured) "Sync now" else "Set up",
                            icon = if (settings.serverConfigured) Icons.Rounded.Sync else Icons.Rounded.Settings,
                            onClick = {
                                if (settings.serverConfigured) appVm.syncNow() else onOpenSettings()
                            }
                        )
                    }
                    if (syncOp.message.isNotBlank()) {
                        Text(
                            syncOp.message,
                            style = AppType.BodyTiny,
                            color = if (syncOp.success) SuccessBright else SurfaceWhite.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                SectionHeading(title = "Preferences")
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    if (account != null) {
                        SettingsRow(
                            label = "Account",
                            value = "@${account.username} · ${if (account.isAdmin) "Admin" else "Learner"}"
                        )
                        ThinDivider()
                    }
                    SettingsRow(label = "Your name", value = settings.name, onClick = { editOpen = true })
                    ThinDivider()
                    SettingsRow(
                        label = "Exam target",
                        value = settings.target.ifBlank { "Kerala PSC" },
                        onClick = { editOpen = true }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Exam date",
                        value = settings.examDate.takeIf { it.isNotBlank() }
                            ?.let { Format.examDateLabel(it) } ?: "Not set",
                        onClick = { editOpen = true }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Study reminders",
                        trailing = {
                            PadanamToggle(
                                checked = settings.reminders,
                                onCheckedChange = { appVm.setReminders(it) }
                            )
                        }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Question format",
                        value = if (settings.language == Lang.ML) "ML + EN" else "EN + ML",
                        onClick = { languageOpen = true }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Daily goal",
                        value = "${settings.dailyGoal} questions",
                        onClick = { goalOpen = true }
                    )
                    if (canManage) {
                        ThinDivider()
                        SettingsRow(
                            label = "Admin console",
                            value = "Learners & assignments",
                            onClick = onOpenAdmin
                        )
                        ThinDivider()
                        SettingsRow(
                            label = "Export library",
                            value = "JSON",
                            onClick = { appVm.exportLibrary() }
                        )
                        ThinDivider()
                        SettingsRow(
                            label = "Import backup",
                            value = if (backupOp.busy) "Reading…" else "Web or Android",
                            onClick = {
                                if (!backupOp.busy) {
                                    importPicker.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/plain",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            }
                        )
                        if (backupOp.message.isNotBlank()) {
                            ThinDivider()
                            Text(
                                backupOp.message,
                                style = AppType.BodyTiny,
                                color = if (backupOp.success) Success else InkMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            if (attempts.isNotEmpty()) {
                item {
                    SectionHeading(title = "Recent attempts", meta = "${attempts.size} total")
                }
                items(attempts.take(3), key = { it.id }) { attempt ->
                    AttemptRow(attempt = attempt, onClick = { onOpenAttempt(attempt.id) })
                }
            }

            item {
                SectionHeading(title = "Support")
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    SettingsRow(
                        label = "Help & feedback",
                        onClick = { helpOpen = true }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "Privacy & security",
                        onClick = { privacyOpen = true }
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "AI & database settings",
                        value = "Open",
                        onClick = onOpenSettings
                    )
                    ThinDivider()
                    SettingsRow(
                        label = "About Padanam",
                        value = "v${BuildConfig.VERSION_NAME}"
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Verified,
                        null,
                        tint = Success,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "  Built for one aspirant · Kerala PSC",
                        style = AppType.MonoSmall,
                        color = InkMuted
                    )
                }
            }
        }
    }

    if (editOpen) {
        EditProfileDialog(
            settings = settings,
            onDismiss = { editOpen = false },
            onSave = { name, target, examDate, language, goal, reminders ->
                appVm.saveProfile(name, target, examDate, language, goal, reminders)
                editOpen = false
            }
        )
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Import this backup?", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Importing replaces this phone's library, results and profile with the contents of the file. Your server address and account stay as they are.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "Both Padanam web backups and older Android exports are accepted.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImport
                        pendingImport = null
                        if (uri != null) appVm.importLibrary(uri)
                    }
                ) {
                    Text("Import backup", style = AppType.ButtonSmall, color = Indigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        )
    }

    if (languageOpen) {
        AlertDialog(
            onDismissRequest = { languageOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Question format", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This chooses which language appears first in questions and explanations. Both languages are always stored.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            text = "English first",
                            selected = settings.language == Lang.EN,
                            onClick = {
                                appVm.setLanguage(Lang.EN)
                                languageOpen = false
                            }
                        )
                        ChoiceChip(
                            text = "Malayalam first",
                            selected = settings.language == Lang.ML,
                            onClick = {
                                appVm.setLanguage(Lang.ML)
                                languageOpen = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageOpen = false }) {
                    Text("Close", style = AppType.ButtonSmall, color = Indigo)
                }
            }
        )
    }

    if (goalOpen) {
        GoalDialog(
            initial = settings.dailyGoal,
            onDismiss = { goalOpen = false },
            onSave = {
                appVm.setDailyGoal(it)
                goalOpen = false
            }
        )
    }

    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Help & feedback", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Scan a printed question sheet with every option visible. The scanner extracts the text, both languages and the correct answer.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "If a scan misses a question, check that the page is flat, bright and fully in frame, then scan again. Sets and attempts are always saved locally.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "This is a personal build — feedback goes to the person who built it for you.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) {
                    Text("Got it", style = AppType.ButtonSmall, color = Indigo)
                }
            }
        )
    }

    if (privacyOpen) {
        AlertDialog(
            onDismissRequest = { privacyOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Privacy & security", style = AppType.Question, color = Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your library is cached on this phone. While you are signed in, sets, attempts and your profile sync to your own account workspace in the shared Padanam database.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "Photographs are sent to the Padanam scanning service only while a scan is running. No AI or database key is stored on this phone, and no images are saved in the cloud.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "The AI provider key stays in Cloudflare. This phone only holds your signed-in session, which is cleared when you sign out.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { privacyOpen = false }) {
                    Text("Close", style = AppType.ButtonSmall, color = Indigo)
                }
            }
        )
    }
}

@Composable
private fun RowScope.ProfileStat(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = AppType.StatValueSmall, color = Navy)
        Text(label, style = AppType.BodyTiny, color = InkMuted)
    }
}

@Composable
private fun EditProfileDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Boolean) -> Unit
) {
    var name by remember(settings.name) { mutableStateOf(settings.name) }
    var target by remember(settings.target) { mutableStateOf(settings.target) }
    var examDate by remember(settings.examDate) { mutableStateOf(settings.examDate) }
    var language by remember(settings.language) { mutableStateOf(settings.language) }
    var goal by remember(settings.dailyGoal) { mutableIntStateOf(settings.dailyGoal) }
    var reminders by remember(settings.reminders) { mutableStateOf(settings.reminders) }
    var dateError by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceWhite)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit profile", style = AppType.Question, color = Navy)
            Text(
                "Saved on this phone and synced to your account.",
                style = AppType.BodySmall,
                color = InkMuted
            )
            AppTextField(
                label = "Your name",
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = "Maya"
            )
            AppTextField(
                label = "Exam target",
                value = target,
                onValueChange = { target = it.take(70) },
                placeholder = "Kerala PSC"
            )
            AppTextField(
                label = "Exam date",
                value = examDate,
                onValueChange = {
                    examDate = it.take(10)
                    dateError = ""
                },
                placeholder = "2027-03-14",
                helper = dateError.ifBlank { "Optional · YYYY-MM-DD" }
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
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "DAILY GOAL",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                    color = InkMuted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GoalStepButton("−") { goal = (goal - 5).coerceIn(5, 200) }
                    Text(
                        "$goal questions",
                        style = AppType.Label,
                        color = Navy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    GoalStepButton("+") { goal = (goal + 5).coerceIn(5, 200) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Study reminders", style = AppType.Label, color = Navy)
                    Text("One nudge at 7pm", style = AppType.BodyTiny, color = InkMuted)
                }
                PadanamToggle(checked = reminders, onCheckedChange = { reminders = it })
            }
            PrimaryButton(
                text = "Save profile",
                onClick = {
                    val cleanDate = examDate.trim()
                    val validDate = cleanDate.isBlank() ||
                        runCatching { LocalDate.parse(cleanDate) }.isSuccess
                    if (!validDate) {
                        dateError = "Use YYYY-MM-DD, for example 2027-03-14."
                    } else {
                        onSave(
                            name.trim().ifBlank { "Maya" },
                            target.trim().ifBlank { "Kerala PSC" },
                            cleanDate,
                            language,
                            goal,
                            reminders
                        )
                    }
                },
                icon = Icons.Rounded.Check
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
            }
        }
    }
}

@Composable
private fun GoalStepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(IndigoSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.SectionTitle, color = Indigo)
    }
}

@Composable
private fun GoalDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var goal by remember(initial) { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Daily goal", style = AppType.Question, color = Navy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "A gentle target for questions answered each day. It resets at midnight.",
                    style = AppType.BodySmall,
                    color = InkMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GoalStepButton("−") { goal = (goal - 5).coerceIn(5, 200) }
                    Text(
                        "$goal",
                        style = AppType.StatValue.copy(fontSize = 30.sp),
                        color = Navy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    GoalStepButton("+") { goal = (goal + 5).coerceIn(5, 200) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(goal) }) {
                Text("Save goal", style = AppType.ButtonSmall, color = Indigo)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
            }
        }
    )
}
