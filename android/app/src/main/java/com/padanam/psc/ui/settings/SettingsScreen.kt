package com.padanam.psc.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.padanam.psc.BuildConfig
import com.padanam.psc.data.model.ThemeMode
import com.padanam.psc.data.model.ViewMode
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
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val session by appVm.session.collectAsStateWithLifecycle()
    val syncOp by appVm.syncOp.collectAsStateWithLifecycle()
    val backupOp by appVm.backupOp.collectAsStateWithLifecycle()
    val diagnostics by appVm.diagnostics.collectAsStateWithLifecycle()
    val sets by appVm.sets.collectAsStateWithLifecycle()
    val questionCount by appVm.questionCount.collectAsStateWithLifecycle()
    val pendingChanges by appVm.pendingChanges.collectAsStateWithLifecycle()
    val online by appVm.online.collectAsStateWithLifecycle()

    var server by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var signOutOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    val user = session?.user
    val isAdmin = user?.isAdmin == true
    val canAdmin = isAdmin && settings.viewMode != ViewMode.LEARNER
    val connected = settings.serverUrl.isNotBlank()

    if (signOutOpen) {
        AlertDialog(
            onDismissRequest = { signOutOpen = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Sign out?", style = AppType.Question, color = Navy) },
            text = {
                Text(
                    "Your account stays on the server. The offline copy on this phone is cleared " +
                        "so the next person cannot open your library.",
                    style = AppType.BodySmall,
                    color = InkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        signOutOpen = false
                        appVm.signOut()
                    }
                ) {
                    Text("Sign out", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { signOutOpen = false }) {
                    Text("Cancel", color = InkMuted)
                }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        TopBar(title = "Settings", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Settings",
                        style = AppType.HeroTitle.copy(fontSize = 28.sp),
                        color = Navy
                    )
                    Text(
                        "Account, offline library, sync and study defaults.",
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(IndigoSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                null,
                                tint = Indigo,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                user?.label?.ifBlank { "Signed in" } ?: "Signed in",
                                style = AppType.SectionTitle,
                                color = Navy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                user?.let { "@${it.username}" } ?: "Account",
                                style = AppType.BodyTiny,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isAdmin) IndigoSoft else SuccessSoft)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                when {
                                    !isAdmin -> "LEARNER"
                                    canAdmin -> "ADMIN"
                                    else -> "ADMIN · LEARNER VIEW"
                                },
                                style = AppType.MonoChip,
                                color = if (isAdmin) Indigo else Success
                            )
                        }
                    }
                    ThinDivider()
                    Text(
                        if (isAdmin) {
                            "You can scan pages, edit the library and assign sets to learners."
                        } else {
                            "Learner accounts practise and take mock exams. Scanning and library " +
                                "editing are done by the administrator."
                        },
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    if (canAdmin) {
                        SettingsRow(
                            label = "Admin console",
                            value = "Learners & assignments",
                            onClick = onOpenAdmin
                        )
                    }
                    LightButton(
                        text = "Sign out",
                        onClick = { signOutOpen = true },
                        icon = Icons.Rounded.Logout,
                        container = DangerSoft,
                        tint = Danger,
                        height = 46.dp
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
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.CloudQueue,
                                    null,
                                    tint = Indigo,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Padanam server", style = AppType.Label, color = Navy)
                                Text(
                                    "Accounts, library and scanning",
                                    style = AppType.BodyTiny,
                                    color = InkMuted
                                )
                            }
                        }
                        Text(
                            if (connected) "CONNECTED" else "NEEDED",
                            style = AppType.MonoChip,
                            color = if (connected) Success else Danger
                        )
                    }
                    AppTextField(
                        label = "Site address",
                        value = server,
                        onValueChange = { server = it.trim() },
                        placeholder = "https://your-site.pages.dev",
                        helper = "The deployed website. The app calls its secure /api functions; no " +
                            "database or AI key is stored on this phone.",
                        singleLine = true
                    )
                    PrimaryButton(
                        text = "Save server address",
                        onClick = { appVm.saveServer(server) },
                        icon = Icons.Rounded.CloudQueue,
                        enabled = server.isNotBlank()
                    )
                }
            }

            item {
                SectionLabel("Appearance")
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndigoSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Palette,
                                null,
                                tint = Indigo,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Theme", style = AppType.Label, color = Navy)
                            Text(
                                "Light, dark or follow the phone setting",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                            ThemeMode.SYSTEM to "System"
                        ).forEach { (value, label) ->
                            ChoiceChip(
                                text = label,
                                selected = settings.themeMode == value,
                                onClick = { appVm.setThemeMode(value) }
                            )
                        }
                    }
                    if (isAdmin) {
                        ThinDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Admin view", style = AppType.Label, color = Navy)
                                Text(
                                    if (settings.viewMode == ViewMode.LEARNER) {
                                        "Learner view is read-only"
                                    } else {
                                        "Scanning, editing and admin tools are visible"
                                    },
                                    style = AppType.BodyTiny,
                                    color = InkMuted
                                )
                            }
                            PadanamToggle(
                                checked = settings.viewMode == ViewMode.ADMIN,
                                onCheckedChange = { checked ->
                                    appVm.setViewMode(
                                        if (checked) ViewMode.ADMIN else ViewMode.LEARNER
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel("Offline library")
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    null,
                                    tint = Indigo,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Downloaded questions", style = AppType.Label, color = Navy)
                                Text(
                                    "${sets.size} sets · ${Format.number(questionCount)} questions cached",
                                    style = AppType.BodyTiny,
                                    color = InkMuted
                                )
                            }
                        }
                        Text(
                            when {
                                !connected -> "NEEDS SERVER"
                                settings.offlineReady -> "READY"
                                else -> "NOT DOWNLOADED"
                            },
                            style = AppType.MonoChip,
                            color = when {
                                !connected -> Danger
                                settings.offlineReady -> Success
                                else -> InkMuted
                            }
                        )
                    }

                    Text(
                        buildString {
                            append(if (online) "Online" else "Offline")
                            append(" · ")
                            append(
                                if (pendingChanges > 0) {
                                    "$pendingChanges local change${if (pendingChanges == 1) "" else "s"} to sync"
                                } else {
                                    "all local changes are synced"
                                }
                            )
                            append(" · ")
                            append(
                                if (settings.offlineReadyAt > 0L) {
                                    "downloaded ${Format.relativeTime(settings.offlineReadyAt)}"
                                } else {
                                    "never downloaded as a complete copy"
                                }
                            )
                        },
                        style = AppType.BodyTiny,
                        color = InkMuted
                    )

                    PrimaryButton(
                        text = when {
                            syncOp.busy -> "Downloading…"
                            settings.offlineReady -> "Refresh offline copy"
                            else -> "Download for offline"
                        },
                        onClick = { appVm.downloadForOffline() },
                        icon = Icons.Rounded.Download,
                        enabled = !syncOp.busy && connected && session != null
                    )

                    InfoNote(
                        text = "Every set, option, answer and explanation is stored on this phone. " +
                            "The app syncs automatically when the internet returns, or you can " +
                            "press Sync now.",
                        icon = Icons.Rounded.CloudDone
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
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Sync,
                                    null,
                                    tint = Indigo,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Cloud sync", style = AppType.Label, color = Navy)
                                Text(
                                    "Shared Padanam workspace",
                                    style = AppType.BodyTiny,
                                    color = InkMuted
                                )
                            }
                        }
                        if (syncOp.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Indigo,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                settings.lastSyncAt.takeIf { it > 0L }
                                    ?.let { Format.relativeTime(it) }
                                    ?: "NOT SYNCED",
                                style = AppType.MonoChip,
                                color = if (settings.lastSyncAt > 0L) Success else InkMuted
                            )
                        }
                    }

                    Text(
                        settings.lastSyncMessage.ifBlank {
                            "Questions, attempts and your profile sync through the Padanam API."
                        },
                        style = AppType.BodyTiny,
                        color = if (syncOp.message.isNotBlank() && !syncOp.success) Danger else InkMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Auto-sync after saving", style = AppType.Label, color = Navy)
                            Text(
                                "Pushes new sets and attempts in the background",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                        PadanamToggle(
                            checked = settings.autoSync,
                            onCheckedChange = { appVm.setAutoSync(it) }
                        )
                    }

                    PrimaryButton(
                        text = if (syncOp.busy) "Syncing…" else "Sync now",
                        onClick = { appVm.syncNow() },
                        icon = Icons.Rounded.Sync,
                        enabled = !syncOp.busy && connected && session != null
                    )

                    if (!connected) {
                        InfoNote(
                            text = "Add the site address above to sync this account.",
                            icon = Icons.Rounded.CloudQueue
                        )
                    }
                }
            }

            if (syncOp.message.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (syncOp.success) SuccessSoft else DangerSoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (syncOp.success) Success else Danger)
                        )
                        Text(
                            syncOp.message,
                            style = AppType.BodyTiny,
                            color = if (syncOp.success) Success else Danger
                        )
                    }
                }
            }

            if (canAdmin) {
                item {
                    SectionLabel("Admin tools")
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(IndigoSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Science,
                                        null,
                                        tint = Indigo,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("Scanner diagnostics", style = AppType.Label, color = Navy)
                                    Text(
                                        "Provider, proxy and model checks",
                                        style = AppType.BodyTiny,
                                        color = InkMuted
                                    )
                                }
                            }
                            if (diagnostics.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Indigo,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        val status = diagnostics.status
                        if (status != null) {
                            Text(
                                "${status.provider.ifBlank { "Provider" }} · " +
                                    status.model.ifBlank { "model not reported" },
                                style = AppType.BodyTiny,
                                color = Navy
                            )
                            Text(
                                "Host: ${status.host.ifBlank { "not reported" }}",
                                style = AppType.MonoSmall,
                                color = InkMuted
                            )
                            if (!status.ready && status.error.isNotBlank()) {
                                Text(
                                    status.error,
                                    style = AppType.BodyTiny,
                                    color = Danger
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LightButton(
                                text = "Status",
                                onClick = { appVm.loadScannerStatus() },
                                icon = Icons.Rounded.CloudDone,
                                container = SurfaceMuted,
                                height = 42.dp,
                                modifier = Modifier.weight(1f)
                            )
                            LightButton(
                                text = "Test",
                                onClick = { appVm.testScanner() },
                                icon = Icons.Rounded.Science,
                                container = SurfaceMuted,
                                height = 42.dp,
                                modifier = Modifier.weight(1f)
                            )
                            LightButton(
                                text = "Models",
                                onClick = { appVm.loadModels() },
                                icon = Icons.Rounded.Storage,
                                container = SurfaceMuted,
                                height = 42.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (diagnostics.message.isNotBlank()) {
                            Text(
                                diagnostics.message,
                                style = AppType.BodyTiny,
                                color = if (diagnostics.error.isBlank()) Success else InkMuted
                            )
                        }
                        if (diagnostics.error.isNotBlank()) {
                            Text(
                                diagnostics.error,
                                style = AppType.BodyTiny,
                                color = Danger
                            )
                        }
                        if (diagnostics.testResult.isNotBlank()) {
                            Text(
                                "Test reply: ${diagnostics.testResult}",
                                style = AppType.MonoSmall,
                                color = InkMuted
                            )
                        }
                        if (diagnostics.models.isNotEmpty()) {
                            Text(
                                "${diagnostics.models.size} models · " +
                                    diagnostics.modelsProvider.ifBlank { "provider" },
                                style = AppType.MonoSmall,
                                color = InkMuted
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                diagnostics.models.take(20).forEach { model ->
                                    ChoiceChip(
                                        text = model.id,
                                        selected = false,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                        Text(
                            "API keys and database secrets stay on the deployed server. " +
                                "The app only calls /api/scan and /api/models.",
                            style = AppType.BodyTiny,
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Storage,
                                    null,
                                    tint = Indigo,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Data & backup", style = AppType.Label, color = Navy)
                                Text(
                                    "Export, restore or return to the demo set",
                                    style = AppType.BodyTiny,
                                    color = InkMuted
                                )
                            }
                        }
                        SettingsRow(
                            label = "Export library",
                            value = "Web-compatible JSON",
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
                        ThinDivider()
                        SettingsRow(
                            label = "Restore demo set",
                            value = "Starter questions",
                            onClick = { appVm.restoreDemos() }
                        )
                        if (backupOp.message.isNotBlank()) {
                            Text(
                                backupOp.message,
                                style = AppType.BodyTiny,
                                color = if (backupOp.success) Success else Danger
                            )
                        }
                        Text(
                            "Import replaces this phone's library, results and profile. " +
                                "Your account and server address are kept.",
                            style = AppType.BodyTiny,
                            color = InkMuted
                        )
                    }
                }
            }

            item {
                SectionLabel("About")
            }

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
                    SettingsRow(label = "Library", value = "Room offline cache")
                    ThinDivider()
                    SettingsRow(label = "Secrets", value = "Stored on the server, not in the app")
                }
            }

            item {
                Text(
                    "Padanam keeps questions, attempts and your profile in your account workspace. " +
                        "Photographs are only sent while a scan is running and are never stored in Neon.",
                    style = AppType.BodyTiny,
                    color = InkMuted
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = AppType.MonoSmall.copy(letterSpacing = 0.9.sp),
        color = InkMuted
    )
}
