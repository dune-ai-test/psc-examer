package com.padanam.psc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Bricolage
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DangerSoft
import com.padanam.psc.ui.theme.Hairline
import com.padanam.psc.ui.theme.IconMuted
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoWash
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.Placeholder
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceField
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Track
import com.padanam.psc.util.Format

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                leading != null -> leading()
                onBack != null -> CircleAction(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
            }
        }
        Text(
            text = title.uppercase(),
            style = AppType.Eyebrow.copy(letterSpacing = 1.2.sp),
            color = InkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = IconMuted,
            modifier = Modifier.size(18.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AppType.BodyMedium.copy(color = Navy),
            cursorBrush = SolidColor(Indigo),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = AppType.BodyMedium,
                            color = Placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Clear search",
                tint = IconMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp), color = InkMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceField)
                .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                .heightIn(min = 48.dp)
                .padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = AppType.BodyMedium.copy(color = Navy),
                cursorBrush = SolidColor(Indigo),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = AppType.BodyMedium,
                                color = Placeholder,
                                maxLines = if (singleLine) 1 else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (trailing != null) trailing()
        }
        if (helper != null) {
            Text(helper, style = AppType.BodyTiny, color = InkMuted)
        }
    }
}

@Composable
fun PadanamToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(42.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) Indigo else Track)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(19.dp)
                .clip(CircleShape)
                .background(SurfaceWhite)
        )
    }
}

@Composable
fun SegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) SurfaceWhite else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = AppType.LabelSmallStrong,
                    color = if (active) Navy else InkMuted
                )
            }
        }
    }
}

@Composable
fun DarkCardButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) SurfaceWhite else SurfaceWhite.copy(alpha = 0.12f))
            .then(
                if (filled) Modifier else Modifier.border(
                    1.dp,
                    SurfaceWhite.copy(alpha = 0.18f),
                    RoundedCornerShape(14.dp)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) Navy else SurfaceWhite,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = if (filled) AppType.Button.copy(fontSize = 13.5.sp) else AppType.ButtonSmall,
            color = if (filled) Navy else SurfaceWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    primaryIcon: ImageVector? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryIcon: ImageVector? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(IndigoWash),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Indigo, modifier = Modifier.size(26.dp))
        }
        Text(
            title,
            style = AppType.Question.copy(fontSize = 19.sp, lineHeight = 25.sp),
            color = Navy,
            textAlign = TextAlign.Center
        )
        Text(
            body,
            style = AppType.BodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
        if (primaryLabel != null && onPrimary != null) {
            PrimaryButton(
                text = primaryLabel,
                onClick = onPrimary,
                icon = primaryIcon,
                height = 50.dp
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            LightButton(
                text = secondaryLabel,
                onClick = onSecondary,
                icon = secondaryIcon,
                container = SurfaceMuted,
                height = 46.dp
            )
        }
    }
}

@Composable
fun AttemptRow(
    attempt: AttemptEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val passed = attempt.scorePercent >= 60
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (passed) SuccessSoft else DangerSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${attempt.scorePercent}%",
                style = AppType.MonoValue.copy(fontSize = 10.sp),
                color = if (passed) Success else Danger
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (showTitle) attempt.setTitle else "${attempt.correct} of ${attempt.total} correct",
                style = AppType.LabelSmallStrong.copy(fontSize = 12.5.sp),
                color = Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(if (attempt.mode == "practice") "Practice" else "Mock exam")
                    append(" · ")
                    append(Format.relativeTime(attempt.completedAt))
                    append(" · ")
                    append(Format.duration(attempt.durationSeconds))
                },
                style = AppType.MonoSmall.copy(letterSpacing = 0.1.sp),
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${attempt.correct}/${attempt.total}",
            style = AppType.MonoMeta,
            color = InkMuted
        )
    }
}

@Composable
fun LegendDot(color: Color, label: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text("$label $count", style = AppType.MonoSmall, color = InkMuted)
    }
}

@Composable
fun FieldHint(text: String, color: Color = InkMuted, modifier: Modifier = Modifier) {
    Text(text, style = AppType.BodyTiny, color = color, modifier = modifier)
}

@Composable
fun SmallTag(text: String, modifier: Modifier = Modifier, background: Color = SurfaceMuted) {
    Text(
        text.uppercase(),
        style = AppType.MonoChip,
        color = InkMuted,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Navy),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "P",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Bricolage,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.5f).sp
            ),
            color = SurfaceWhite
        )
    }
}
