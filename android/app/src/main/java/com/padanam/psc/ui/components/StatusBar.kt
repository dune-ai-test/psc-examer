package com.padanam.psc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.padanam.psc.ui.theme.DMSans
import com.padanam.psc.ui.theme.Island
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.util.Format
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The mockups draw their own iPhone-style status bar. We hide the system bar
 * and render this one so every screen has identical 62dp chrome.
 */
@Composable
fun PadanamStatusBar(
    dark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val time by produceState(initialValue = Format.clockNow()) {
        while (true) {
            value = Format.clockNow()
            delay(30_000)
        }
    }
    val tint = if (dark) OnDark else Navy
    val island = if (dark) Color(0xFF1A2030) else Island

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            .height(62.dp)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.width(104.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = time,
                color = tint,
                style = TextStyle(
                    fontFamily = DMSans,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(30.dp)
                .background(island, CircleShape)
        )
        Row(
            modifier = Modifier.width(104.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = Icons.Rounded.SignalCellularAlt,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Icon(
                imageVector = Icons.Rounded.Wifi,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(start = 6.dp).size(15.dp)
            )
            Icon(
                imageVector = Icons.Rounded.BatteryFull,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(start = 6.dp).size(17.dp)
            )
        }
    }
}
