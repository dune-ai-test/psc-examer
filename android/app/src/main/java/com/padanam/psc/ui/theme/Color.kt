package com.padanam.psc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The design system uses semantic colour names throughout the screens. Light
 * and dark palettes both supply the same names, so switching theme never
 * requires a screen-by-screen colour refactor.
 */
data class PadanamPalette(
    val cloud: Color,
    val surface: Color,
    val navy: Color,
    val ink: Color,
    val inkMuted: Color,
    val placeholder: Color,
    val iconMuted: Color,
    val iconFaint: Color,
    val hairline: Color,
    val track: Color,
    val trackSoft: Color,
    val surfaceMuted: Color,
    val surfaceField: Color,
    val indigo: Color,
    val indigoDeep: Color,
    val indigoSoft: Color,
    val indigoWash: Color,
    val indigoText: Color,
    val indigoMuted: Color,
    val indigoPale: Color,
    val indigoBar: Color,
    val success: Color,
    val successInk: Color,
    val successSoft: Color,
    val successWash: Color,
    val successBright: Color,
    val amber: Color,
    val amberDeep: Color,
    val amberInk: Color,
    val amberSoft: Color,
    val amberBar: Color,
    val orange: Color,
    val orangeSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val sky: Color
)

private val LightPalette = PadanamPalette(
    cloud = Color(0xFFF2F4F9),
    surface = Color(0xFFFFFFFF),
    navy = Color(0xFF12172B),
    ink = Color(0xFF12172B),
    inkMuted = Color(0xFF5D6478),
    placeholder = Color(0xFF8B91A3),
    iconMuted = Color(0xFF9AA0B4),
    iconFaint = Color(0xFFB4B9C8),
    hairline = Color(0xFFE6E8F0),
    track = Color(0xFFE4E7F0),
    trackSoft = Color(0xFFEDEFF6),
    surfaceMuted = Color(0xFFF3F4F9),
    surfaceField = Color(0xFFF7F8FB),
    indigo = Color(0xFF5B4BF6),
    indigoDeep = Color(0xFF4A3AE2),
    indigoSoft = Color(0xFFE9E7FE),
    indigoWash = Color(0xFFF1F0FE),
    indigoText = Color(0xFF2A2170),
    indigoMuted = Color(0xFF6A5BE8),
    indigoPale = Color(0xFFB4AAFF),
    indigoBar = Color(0xFFD9D4FB),
    success = Color(0xFF0F9E7E),
    successInk = Color(0xFF0B6B52),
    successSoft = Color(0xFFE9F7F1),
    successWash = Color(0xFFEAF8F2),
    successBright = Color(0xFF7DE3C0),
    amber = Color(0xFFF59E0B),
    amberDeep = Color(0xFFD99000),
    amberInk = Color(0xFF8A5A00),
    amberSoft = Color(0xFFFFF6E8),
    amberBar = Color(0xFFF5B33F),
    orange = Color(0xFFF97316),
    orangeSoft = Color(0xFFFFF1E4),
    danger = Color(0xFFD64545),
    dangerSoft = Color(0xFFFDECEC),
    sky = Color(0xFF3E7BFA)
)

private val DarkPalette = PadanamPalette(
    cloud = Color(0xFF0B0E18),
    surface = Color(0xFF141A2E),
    navy = Color(0xFFF1F3FA),
    ink = Color(0xFFF1F3FA),
    inkMuted = Color(0xFFA5ACC2),
    placeholder = Color(0xFF7F879E),
    iconMuted = Color(0xFF8B93AA),
    iconFaint = Color(0xFF5A6279),
    hairline = Color(0xFF262D45),
    track = Color(0xFF2A324A),
    trackSoft = Color(0xFF222A40),
    surfaceMuted = Color(0xFF1C2338),
    surfaceField = Color(0xFF171E32),
    indigo = Color(0xFF8175FF),
    indigoDeep = Color(0xFF6C5DF7),
    indigoSoft = Color(0xFF26214A),
    indigoWash = Color(0xFF1D1A38),
    indigoText = Color(0xFFCFC9FF),
    indigoMuted = Color(0xFFA79DFF),
    indigoPale = Color(0xFF4C429B),
    indigoBar = Color(0xFF3B3274),
    success = Color(0xFF3CCEA6),
    successInk = Color(0xFF8CEBCB),
    successSoft = Color(0xFF123A2F),
    successWash = Color(0xFF10352B),
    successBright = Color(0xFF7DE3C0),
    amber = Color(0xFFF6B84A),
    amberDeep = Color(0xFFE0A022),
    amberInk = Color(0xFFF7D28A),
    amberSoft = Color(0xFF3A2A0E),
    amberBar = Color(0xFFF5B33F),
    orange = Color(0xFFFB923C),
    orangeSoft = Color(0xFF3B2210),
    danger = Color(0xFFFF8080),
    dangerSoft = Color(0xFF3D1D22),
    sky = Color(0xFF6FA5FF)
)

internal val LocalPadanamPalette = staticCompositionLocalOf { LightPalette }

internal fun paletteFor(dark: Boolean): PadanamPalette = if (dark) DarkPalette else LightPalette

/** Constant white for text and icons laid over dark cards, photos and accents. */
val OnDark = Color(0xFFFFFFFF)

/** Constant near-black for text and icons laid over fixed white buttons. */
val OnLight = Color(0xFF12172B)

// Dark gradient cards keep their dedicated dark palette in both themes.
val CardTop = Color(0xFF1E2544)
val CardBottom = Color(0xFF12172B)
val DarkBackground = Color(0xFF0B0E18)
val DarkSurface = Color(0xFF141A2E)
val Island = Color(0xFF0B0E18)

// Core surfaces
val Cloud: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.cloud
val SurfaceWhite: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.surface
val Navy: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.navy
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.ink
val InkMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.inkMuted
val Placeholder: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.placeholder
val IconMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.iconMuted
val IconFaint: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.iconFaint
val Hairline: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.hairline
val Track: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.track
val TrackSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.trackSoft
val SurfaceMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.surfaceMuted
val SurfaceField: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.surfaceField

// Accents
val Indigo: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigo
val IndigoDeep: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoDeep
val IndigoSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoSoft
val IndigoWash: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoWash
val IndigoText: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoText
val IndigoMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoMuted
val IndigoPale: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoPale
val IndigoBar: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.indigoBar

// Semantic
val Success: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.success
val SuccessInk: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.successInk
val SuccessSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.successSoft
val SuccessWash: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.successWash
val SuccessBright: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.successBright
val Amber: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.amber
val AmberDeep: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.amberDeep
val AmberInk: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.amberInk
val AmberSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.amberSoft
val AmberBar: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.amberBar
val Orange: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.orange
val OrangeSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.orangeSoft
val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.danger
val DangerSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.dangerSoft
val Sky: Color
    @Composable @ReadOnlyComposable get() = LocalPadanamPalette.current.sky
