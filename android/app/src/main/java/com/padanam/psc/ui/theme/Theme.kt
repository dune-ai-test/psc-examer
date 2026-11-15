package com.padanam.psc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PadanamColors = lightColorScheme(
    primary = Indigo,
    onPrimary = SurfaceWhite,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = IndigoText,
    secondary = Navy,
    onSecondary = SurfaceWhite,
    secondaryContainer = SurfaceMuted,
    onSecondaryContainer = Navy,
    tertiary = Success,
    onTertiary = SurfaceWhite,
    background = Cloud,
    onBackground = Navy,
    surface = SurfaceWhite,
    onSurface = Navy,
    surfaceVariant = IndigoWash,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    outlineVariant = Track,
    error = Danger,
    onError = SurfaceWhite,
    errorContainer = DangerSoft,
    onErrorContainer = Danger
)

private val PadanamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun PadanamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PadanamColors,
        typography = AppTypography,
        shapes = PadanamShapes,
        content = content
    )
}
