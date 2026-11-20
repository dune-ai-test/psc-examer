package com.padanam.psc.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.padanam.psc.AppContainer
import com.padanam.psc.data.remote.AdminSet
import com.padanam.psc.data.remote.AdminUser
import com.padanam.psc.ui.components.AppTextField
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.LightButton
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft

@Composable
fun AdminScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: AdminViewModel = viewModel(factory = AdminViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.message) {
        if (state.message.startsWith("Password updated")) newPassword = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        TopSafeArea()
        TopBar(title = "Admin console", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(IndigoSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AdminPanelSettings,
                                null,
                                tint = Indigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text("Administrator", style = AppType.SectionTitle, color = Navy)
                    }
                    Text(
                        "Create learner accounts and assign sets from the shared cloud library.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            }

            item {
                InfoNote(
                    text = "A learner sees only the sets you assign. Assignments copy the set into their own Padanam workspace; their attempts never change your library.",
                    icon = Icons.Rounded.CloudSync
                )
            }

            if (state.error.isNotBlank()) {
                item { MessageCard(text = state.error, positive = false) }
            }
            if (state.message.isNotBlank() && state.error.isBlank()) {
                item { MessageCard(text = state.message, positive = true) }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cloud library", style = AppType.SectionTitle, color = Navy)
                            Text(
                                "${state.sets.size} sets on the server",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                        if (state.busy || state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Indigo,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Text(
                        "Press sync first so new scans exist on the server. Only synced sets can be assigned.",
                        style = AppType.BodyTiny,
                        color = InkMuted
                    )
                    LightButton(
                        text = if (state.busy) "Syncing…" else "Sync admin library",
                        onClick = vm::syncLibrary,
                        icon = Icons.Rounded.CloudSync,
                        container = IndigoSoft,
                        tint = Indigo,
                        height = 46.dp
                    )
                }
            }

            item {
                SectionHeading(
                    title = "Learners",
                    meta = "${state.users.count { it.role != "admin" }} accounts"
                )
            }

            if (state.users.none { it.role != "admin" }) {
                item {
                    Text(
                        "No learner accounts yet. Create the first one below.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            } else {
                items(state.users.filter { it.role != "admin" }, key = { it.id }) { user ->
                    LearnerRow(
                        user = user,
                        selected = state.selectedUserId == user.id,
                        onClick = { vm.selectUser(user.id) }
                    )
                }
            }

            item {
                SectionHeading(title = "Create learner")
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            null,
                            tint = Indigo,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("New account", style = AppType.SectionTitle, color = Navy)
                    }
                    AppTextField(
                        label = "Username",
                        value = username,
                        onValueChange = { username = it.take(32) },
                        placeholder = "maya.psc",
                        helper = "Lowercase letters, numbers, dots, dashes or underscores."
                    )
                    AppTextField(
                        label = "Display name",
                        value = displayName,
                        onValueChange = { displayName = it.take(80) },
                        placeholder = "Maya"
                    )
                    AppTextField(
                        label = "Temporary password",
                        value = password,
                        onValueChange = { password = it.take(200) },
                        placeholder = "At least 8 characters",
                        visualTransformation = PasswordVisualTransformation()
                    )
                    PrimaryButton(
                        text = if (state.busy) "Working…" else "Create account",
                        onClick = {
                            vm.createUser(username, displayName, password)
                            password = ""
                        },
                        icon = Icons.Rounded.PersonAdd,
                        enabled = !state.busy
                    )
                }
            }

            item {
                SectionHeading(title = "Assign a library")
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
                    Text(
                        state.selectedUser?.let { "Assigning to ${it.label} (@${it.username})" }
                            ?: "Choose a learner above.",
                        style = AppType.Label,
                        color = Navy
                    )
                    if (state.sets.isEmpty()) {
                        Text(
                            "No sets on the server yet. Scan a set and sync, then come back.",
                            style = AppType.BodySmall,
                            color = InkMuted
                        )
                    } else {
                        state.sets.forEach { set ->
                            SetCheckRow(
                                set = set,
                                checked = set.id in state.selectedSetIds,
                                onToggle = { vm.toggleSet(set.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    PrimaryButton(
                        text = if (state.busy) "Assigning…" else "Assign selected sets",
                        onClick = vm::assignSelectedSets,
                        enabled = !state.busy && state.selectedUserId != null &&
                            state.selectedSetIds.isNotEmpty(),
                        icon = Icons.Rounded.CloudSync
                    )
                }
            }

            item {
                SectionHeading(title = "Reset learner password")
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            null,
                            tint = Indigo,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Set a new password", style = AppType.SectionTitle, color = Navy)
                    }
                    Text(
                        state.selectedUser?.let {
                            "Set a new password for ${it.label} (@${it.username})."
                        } ?: "Choose a learner above.",
                        style = AppType.Label,
                        color = Navy
                    )
                    AppTextField(
                        label = "New password",
                        value = newPassword,
                        onValueChange = { newPassword = it.take(200) },
                        placeholder = "At least 8 characters",
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Text(
                        "The learner can sign in with this password immediately. Their current session stays valid until it expires.",
                        style = AppType.BodyTiny,
                        color = InkMuted
                    )
                    PrimaryButton(
                        text = if (state.busy) "Working…" else "Reset password",
                        onClick = {
                            state.selectedUserId?.let { userId ->
                                vm.resetPassword(userId, newPassword)
                            }
                        },
                        enabled = !state.busy && state.selectedUserId != null &&
                            newPassword.length >= 8,
                        icon = Icons.Rounded.Lock
                    )
                }
            }
        }
    }
}

@Composable
private fun LearnerRow(
    user: AdminUser,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) IndigoSoft else SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (selected) Indigo else SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.label.take(1).uppercase().ifBlank { "?" },
                style = AppType.SectionTitle.copy(fontSize = 14.sp),
                color = if (selected) OnDark else Navy
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                user.label,
                style = AppType.Label,
                color = Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${user.username} · ${user.setIds.size} assigned",
                style = AppType.BodyTiny,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Text("SELECTED", style = AppType.MonoChip, color = Indigo)
        }
    }
}

@Composable
private fun SetCheckRow(
    set: AdminSet,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) IndigoSoft else SurfaceMuted)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Indigo,
                uncheckedColor = InkMuted,
                checkmarkColor = OnDark
            )
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                set.title,
                style = AppType.Label,
                color = Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${set.subject} · ${set.questions} questions",
                style = AppType.BodyTiny,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MessageCard(text: String, positive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (positive) SuccessSoft else DangerSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (positive) Success else Danger)
        )
        Text(
            text,
            style = AppType.BodyTiny,
            color = if (positive) Success else Danger
        )
    }
}
