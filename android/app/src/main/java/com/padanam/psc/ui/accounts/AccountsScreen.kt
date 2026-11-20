package com.padanam.psc.ui.accounts

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.data.auth.AuthSession
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.Avatar
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.components.SectionHeading
import com.padanam.psc.ui.components.TopBar
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.components.ThinDivider
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.IconFaint
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceWhite

/**
 * One saved login. Shared by the Accounts screen and the signed-out sign-in
 * screen so both show the same wording and order.
 */
@Composable
fun SavedAccountRow(
    session: AuthSession,
    active: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && !busy) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Avatar(
            name = session.user.label,
            size = 40.dp,
            background = if (active) Indigo else Navy
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    session.user.label.ifBlank { session.user.username },
                    style = AppType.Label,
                    color = Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                RoleChip(admin = session.user.isAdmin)
            }
            Text(
                "@${session.user.username}",
                style = AppType.BodyTiny,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Indigo,
                strokeWidth = 2.dp
            )
            onRemove != null -> TextButton(onClick = onRemove) {
                Text("Remove", style = AppType.ButtonSmall, color = Danger)
            }
            active -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Current account",
                tint = Success,
                modifier = Modifier.size(20.dp)
            )
            else -> Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = IconFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RoleChip(admin: Boolean) {
    Text(
        if (admin) "ADMIN" else "LEARNER",
        style = AppType.MonoChip,
        color = if (admin) Indigo else Success,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (admin) IndigoSoft else SuccessSoft)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun AccountsScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit
) {
    val sessions by appVm.savedSessions.collectAsStateWithLifecycle()
    val active by appVm.session.collectAsStateWithLifecycle()
    val busy by appVm.accountBusy.collectAsStateWithLifecycle()

    var pendingRemove by remember { mutableStateOf<AuthSession?>(null) }
    val removeTarget = pendingRemove

    if (removeTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Remove this login?", style = AppType.Question, color = Navy) },
            text = {
                Text(
                    "${removeTarget.user.label} (@${removeTarget.user.username}) will be removed " +
                        "from this phone. The account and its library stay on the server. " +
                        "You can sign in again with the password.",
                    style = AppType.BodySmall,
                    color = InkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = removeTarget
                        pendingRemove = null
                        if (target != null) appVm.removeSavedAccount(target.key)
                    }
                ) {
                    Text("Remove", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
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
        TopSafeArea()
        TopBar(title = "Accounts", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Switch account",
                        style = AppType.HeroTitle.copy(fontSize = 28.sp),
                        color = Navy
                    )
                    Text(
                        "Every login saved on this phone opens without a password. " +
                            "Switching also changes the library shown in the app.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            }

            item {
                SectionHeading(title = "Saved on this phone", meta = "${sessions.size}")
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                ) {
                    sessions.forEachIndexed { index, saved ->
                        val isActive = saved.key == active?.key
                        SavedAccountRow(
                            session = saved,
                            active = isActive,
                            busy = busy,
                            onClick = if (isActive) null else ({ appVm.switchAccount(saved.key) }),
                            onRemove = if (isActive) null else ({ pendingRemove = saved })
                        )
                        if (index != sessions.lastIndex) ThinDivider()
                    }
                }
            }

            item {
                PrimaryButton(
                    text = "Add another account",
                    onClick = onAddAccount,
                    icon = Icons.Rounded.PersonAdd,
                    enabled = !busy
                )
            }

            item {
                InfoNote(
                    text = "Add a learner here while the administrator stays signed in. " +
                        "Only the account you sign out of is removed; the others stay saved.",
                    icon = Icons.Rounded.SwapHoriz
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(IndigoSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                null,
                                tint = Indigo,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text("How switching works", style = AppType.SectionTitle, color = Navy)
                    }
                    Text(
                        "Before switching, your current account is synced so scans and edits " +
                            "are not lost. If you are offline with unsaved changes the app asks " +
                            "you to reconnect first.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "Only one library is kept on the phone at a time. After a switch the " +
                            "chosen account's sets download from your Padanam site.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                    Text(
                        "Anyone who can unlock this phone can switch back to the saved " +
                            "administrator login without typing the password.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            }
        }
    }
}
