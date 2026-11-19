package com.padanam.psc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padanam.psc.data.model.Lang
import com.padanam.psc.ui.theme.AmberDeep
import com.padanam.psc.ui.theme.AmberInk
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Hairline
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.MlType
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessInk
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceField
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite

enum class OptionVisual { DEFAULT, SELECTED, CORRECT, WRONG, SKIPPED }

/** Question text uses a Malayalam-capable family when the UI language is ML. */
@Composable
fun QuestionText(
    text: String,
    language: String,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Text(
        text = text,
        style = when {
            language == Lang.ML && large -> MlType.QuestionLarge
            language == Lang.ML -> MlType.Question
            large -> AppType.QuestionLarge
            else -> AppType.Question
        },
        color = Navy,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun OptionPill(
    letter: String,
    text: String,
    modifier: Modifier = Modifier,
    visual: OptionVisual = OptionVisual.DEFAULT,
    language: String = Lang.EN,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val background = when (visual) {
        OptionVisual.DEFAULT -> SurfaceField
        OptionVisual.SELECTED -> IndigoSoft
        OptionVisual.CORRECT -> SuccessSoft
        OptionVisual.WRONG -> AmberSoft
        OptionVisual.SKIPPED -> SurfaceMuted
    }
    val outline = when (visual) {
        OptionVisual.SELECTED -> Indigo
        OptionVisual.CORRECT -> Success
        OptionVisual.WRONG -> AmberDeep
        OptionVisual.SKIPPED -> Hairline
        OptionVisual.DEFAULT -> Color.Transparent
    }
    val badge = when (visual) {
        OptionVisual.SELECTED -> Indigo
        OptionVisual.CORRECT -> Success
        OptionVisual.WRONG -> AmberDeep
        OptionVisual.DEFAULT, OptionVisual.SKIPPED -> SurfaceWhite
    }
    val badgeText = when (visual) {
        OptionVisual.SELECTED, OptionVisual.CORRECT, OptionVisual.WRONG -> OnDark
        OptionVisual.DEFAULT, OptionVisual.SKIPPED -> InkMuted
    }
    val textColor = when (visual) {
        OptionVisual.SELECTED -> Indigo
        OptionVisual.CORRECT -> SuccessInk
        OptionVisual.WRONG -> AmberInk
        OptionVisual.SKIPPED -> InkMuted
        OptionVisual.DEFAULT -> Navy
    }
    val baseStyle = if (language == Lang.ML) MlType.Option else AppType.BodyMedium
    val textStyle = when (visual) {
        OptionVisual.SELECTED -> baseStyle.copy(fontWeight = FontWeight.SemiBold)
        OptionVisual.CORRECT, OptionVisual.WRONG -> baseStyle.copy(fontWeight = FontWeight.SemiBold)
        else -> baseStyle
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (visual == OptionVisual.DEFAULT) 1.dp else 1.5.dp, outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(badge),
            contentAlignment = Alignment.Center
        ) {
            Text(
                letter.uppercase(),
                style = AppType.MonoValue.copy(fontSize = 9.5.sp),
                color = badgeText
            )
        }
        Text(
            text,
            style = textStyle,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

/** A small status chip used on review and result question rows. */
@Composable
fun ResultChip(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text.uppercase(),
        style = AppType.MonoChip,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun StatusDotBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Int = 26
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.54f).dp))
    }
}
