package com.padanam.psc.ui.auth

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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padanam.psc.ui.AppViewModel
import com.padanam.psc.ui.components.AppTextField
import com.padanam.psc.ui.components.BrandMark
import com.padanam.psc.ui.components.InfoNote
import com.padanam.psc.ui.components.PadanamStatusBar
import com.padanam.psc.ui.components.PrimaryButton
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceWhite

@Composable
fun LoginScreen(
    appVm: AppViewModel,
    initialServer: String
) {
    val busy by appVm.authBusy.collectAsStateWithLifecycle()
    val error by appVm.authError.collectAsStateWithLifecycle()

    var server by rememberSaveable(initialServer) { mutableStateOf(initialServer) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
    ) {
        PadanamStatusBar()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BrandMark(size = 48.dp)
                    Text(
                        "Padanam",
                        style = AppType.HeroTitle.copy(fontSize = 34.sp),
                        color = Navy
                    )
                    Text(
                        "Sign in to open your question library. Your account is the same one used on the Padanam website.",
                        style = AppType.BodySmall,
                        color = InkMuted
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(IndigoSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Login, null, tint = Indigo, modifier = Modifier.size(17.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Account", style = AppType.SectionTitle, color = Navy)
                            Text(
                                "Use the username your admin created.",
                                style = AppType.BodyTiny,
                                color = InkMuted
                            )
                        }
                    }
                    AppTextField(
                        label = "Username",
                        value = username,
                        onValueChange = {
                            username = it.take(32)
                            appVm.clearAuthError()
                        },
                        placeholder = "maya.psc"
                    )
                    AppTextField(
                        label = "Password",
                        value = password,
                        onValueChange = {
                            password = it
                            appVm.clearAuthError()
                        },
                        placeholder = "Your password",
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }

            item {
                AppTextField(
                    label = "Padanam site address",
                    value = server,
                    onValueChange = {
                        server = it.trim()
                        appVm.clearAuthError()
                    },
                    placeholder = "https://your-site.pages.dev",
                    helper = "The deployed website address. The app uses its secure /api functions; no database or AI key is stored on this phone.",
                    singleLine = true
                )
            }

            if (error.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DangerSoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Danger)
                        )
                        Text(error, style = AppType.BodyTiny, color = Danger)
                    }
                }
            }

            item {
                PrimaryButton(
                    text = if (busy) "Signing in…" else "Sign in",
                    onClick = { appVm.signIn(server, username, password) },
                    icon = Icons.Rounded.Login,
                    enabled = !busy
                )
            }

            item {
                InfoNote(
                    text = "Learners see the sets the administrator assigns. Accounts, sets and scores sync through your Padanam workspace; this phone keeps an offline copy.",
                    icon = Icons.Rounded.Lock
                )
            }
        }
    }
}

@Composable
fun AuthLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrandMark(size = 52.dp)
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Indigo,
                strokeWidth = 2.dp
            )
            Text("Opening Padanam…", style = AppType.BodySmall, color = InkMuted)
        }
    }
}
