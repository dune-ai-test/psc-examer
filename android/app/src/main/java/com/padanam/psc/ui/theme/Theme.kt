package com.padanam.psc.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.padanam.psc.data.model.ThemeMode

private val PadanamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun PadanamTheme(
    mode: String = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (ThemeMode.normalize(mode)) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    // Keep the transparent system nav bar's icons readable against the app
    // background, and stop OEMs adding a light contrast scrim behind them.
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    val colors = if (dark) {
        darkColorScheme(
            primary = Indigo,
            onPrimary = OnDark,
            primaryContainer = IndigoSoft,
            onPrimaryContainer = IndigoText,
            secondary = Navy,
            onSecondary = DarkBackground,
            secondaryContainer = SurfaceMuted,
            onSecondaryContainer = Navy,
            tertiary = Success,
            onTertiary = DarkBackground,
            background = Cloud,
            onBackground = Navy,
            surface = SurfaceWhite,
            onSurface = Navy,
            surfaceVariant = IndigoWash,
            onSurfaceVariant = InkMuted,
            outline = Hairline,
            outlineVariant = Track,
            error = Danger,
            onError = DarkBackground,
            errorContainer = DangerSoft,
            onErrorContainer = Danger
        )
    } else {
        lightColorScheme(
            primary = Indigo,
            onPrimary = OnDark,
            primaryContainer = IndigoSoft,
            onPrimaryContainer = IndigoText,
            secondary = Navy,
            onSecondary = OnDark,
            secondaryContainer = SurfaceMuted,
            onSecondaryContainer = Navy,
            tertiary = Success,
            onTertiary = OnDark,
            background = Cloud,
            onBackground = Navy,
            surface = SurfaceWhite,
            onSurface = Navy,
            surfaceVariant = IndigoWash,
            onSurfaceVariant = InkMuted,
            outline = Hairline,
            outlineVariant = Track,
            error = Danger,
            onError = OnDark,
            errorContainer = DangerSoft,
            onErrorContainer = Danger
        )
    }

    CompositionLocalProvider(LocalPadanamPalette provides paletteFor(dark)) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = PadanamShapes,
            content = content
        )
    }
}
