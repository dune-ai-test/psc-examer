package com.padanam.psc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.padanam.psc.R

val Bricolage = FontFamily(
    Font(R.font.bricolage_500, FontWeight.Medium),
    Font(R.font.bricolage_600, FontWeight.SemiBold),
    Font(R.font.bricolage_700, FontWeight.Bold)
)

val DMSans = FontFamily(
    Font(R.font.dm_sans_400, FontWeight.Normal),
    Font(R.font.dm_sans_500, FontWeight.Medium),
    Font(R.font.dm_sans_600, FontWeight.SemiBold),
    Font(R.font.dm_sans_700, FontWeight.Bold)
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_400, FontWeight.Normal),
    Font(R.font.plex_mono_500, FontWeight.Medium),
    Font(R.font.plex_mono_600, FontWeight.SemiBold)
)

/** Used only for Malayalam question text so conjuncts render correctly. */
val Malayalam = FontFamily(
    Font(R.font.noto_malayalam_400, FontWeight.Normal),
    Font(R.font.noto_malayalam_500, FontWeight.Medium),
    Font(R.font.noto_malayalam_600, FontWeight.SemiBold),
    Font(R.font.noto_malayalam_700, FontWeight.Bold)
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 46.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp
    )
)

/**
 * Reusable styles for the small mono labels and display text used throughout
 * the mockups. Colour is applied at the call site so each surface can tint.
 */
object AppType {
    val Eyebrow = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.9.sp
    )
    val EyebrowTight = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.sp
    )
    val MonoMeta = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.2.sp
    )
    val MonoSmall = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 8.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.6.sp
    )
    val MonoValue = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp
    )
    val MonoChip = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 8.5.sp,
        lineHeight = 11.sp,
        letterSpacing = 1.sp
    )
    val MonoLarge = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val BodyTiny = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 15.sp
    )
    val BodySmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
    val BodyMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
    val LabelSmallStrong = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
    val Label = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    )
    val Button = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
    val ButtonSmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    )
    val Question = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    )
    val QuestionLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp
    )
    val HeroTitle = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp
    )
    val SectionTitle = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
    val StatValue = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp
    )
    val StatValueSmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 23.sp
    )
}

/**
 * Malayalam has taller ascenders and deeper descenders than Latin, so the
 * question, option and explanation styles get a dedicated family and a more
 * generous line height when the exam language is Malayalam.
 */
object MlType {
    val Question = TextStyle(
        fontFamily = Malayalam,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 30.sp
    )
    val QuestionLarge = TextStyle(
        fontFamily = Malayalam,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 32.sp
    )
    val Option = TextStyle(
        fontFamily = Malayalam,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 22.sp
    )
    val OptionSelected = Option.copy(fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(
        fontFamily = Malayalam,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 22.sp
    )
    val Small = TextStyle(
        fontFamily = Malayalam,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 20.sp
    )
    val Label = TextStyle(
        fontFamily = Malayalam,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 20.sp
    )
}
