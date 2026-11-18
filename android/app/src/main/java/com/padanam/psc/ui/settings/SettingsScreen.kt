package com.padanam.psc.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
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
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.AppTextField
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

    var server by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var signOutOpen by remember { mutableStateOf(false) }

    val user = session?.user
    val isAdmin = user?.isAdmin == true
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
                        "Account, sync and study defaults.",
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
                                if (isAdmin) "ADMIN" else "LEARNER",
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
                    if (isAdmin) {
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
