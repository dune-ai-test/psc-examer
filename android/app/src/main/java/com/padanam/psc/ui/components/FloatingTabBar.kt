package com.padanam.psc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.DMSans
import com.padanam.psc.ui.theme.IconMuted
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.SurfaceWhite

enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home("home", "Home", Icons.Rounded.Home),
    Library("library", "Library", Icons.Rounded.Layers),
    Progress("progress", "Progress", Icons.Rounded.BarChart),
    Profile("profile", "Profile", Icons.Rounded.Person)
}

/** Scroll padding that keeps the last list item clear of the floating bar. */
val FloatingTabBarSpace = 132.dp

@Composable
fun FloatingTabBar(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(SurfaceWhite.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.65f), shape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MainTab.entries.forEach { tab ->
                val active = selected == tab.route
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (active) IndigoSoft else Color.Transparent)
                        .clickable { onSelect(tab.route) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Indigo else IconMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        tab.label,
                        style = AppType.EyebrowTight.copy(
                            fontFamily = DMSans,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            letterSpacing = 0.sp
                        ),
                        color = if (active) Indigo else IconMuted
                    )
                }
            }
        }
    }
}
