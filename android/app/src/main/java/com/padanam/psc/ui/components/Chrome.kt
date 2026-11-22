package com.padanam.psc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.model.SetSummary
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.AmberSoft
import com.padanam.psc.ui.theme.Bricolage
import com.padanam.psc.ui.theme.CardBottom
import com.padanam.psc.ui.theme.CardTop
import com.padanam.psc.ui.theme.Cloud
import com.padanam.psc.ui.theme.DMSans
import com.padanam.psc.ui.theme.Hairline
import com.padanam.psc.ui.theme.IconFaint
import com.padanam.psc.ui.theme.IconMuted
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.ui.theme.Success
import com.padanam.psc.ui.theme.SuccessSoft
import com.padanam.psc.ui.theme.SurfaceMuted
import com.padanam.psc.ui.theme.SurfaceWhite
import com.padanam.psc.ui.theme.Track
import com.padanam.psc.util.Format

@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow.uppercase(), style = AppType.Eyebrow, color = InkMuted)
            Text(
                title,
                style = AppType.HeroTitle.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = Navy
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions
        )
    }
}

@Composable
fun CircleAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = SurfaceWhite,
    tint: Color = Navy,
    size: Dp = 40.dp,
    bordered: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .then(if (bordered) Modifier.border(1.dp, Hairline, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    height: Dp = 52.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) Navy else Navy.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppType.Button, color = SurfaceWhite)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun LightButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Navy,
    container: Color = SurfaceWhite,
    height: Dp = 50.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppType.Button, color = tint)
    }
}

@Composable
fun DarkGradientCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    corner: Dp = 26.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val accent = Indigo
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(CardTop, CardBottom)))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.34f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.94f, 0f),
                        radius = size.width * 1.05f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun MonoChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = SurfaceMuted,
    color: Color = InkMuted,
    border: Color? = null
) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = text.uppercase(),
        style = AppType.MonoChip,
        color = color,
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = Track,
    fill: Color = Indigo,
    height: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(fill)
        )
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = AppType.SectionTitle, color = Navy)
        when {
            action != null && onAction != null -> Text(
                action,
                style = AppType.LabelSmallStrong,
                color = Indigo,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            meta != null -> Text(meta, style = AppType.MonoMeta, color = InkMuted)
        }
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Navy else SurfaceWhite)
            .then(if (selected) Modifier else Modifier.border(1.dp, Hairline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = if (selected) {
                AppType.LabelSmallStrong
            } else {
                AppType.LabelSmallStrong.copy(fontWeight = FontWeight.Medium)
            },
            color = if (selected) SurfaceWhite else InkMuted
        )
    }
}

@Composable
fun LanguageSwitch(
    language: String,
    onChange: (String) -> Unit,
    dark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (dark) OnDark.copy(alpha = 0.12f) else SurfaceMuted)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(Lang.EN to "EN", Lang.ML to "ML").forEach { (code, label) ->
            val selected = language == code
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) {
                            if (dark) OnDark.copy(alpha = 0.2f) else SurfaceWhite
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onChange(code) }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    label,
                    style = AppType.MonoChip.copy(letterSpacing = 0.5.sp),
                    color = when {
                        selected && dark -> OnDark
                        selected -> Indigo
                        dark -> OnDark.copy(alpha = 0.55f)
                        else -> InkMuted
                    }
                )
            }
        }
    }
}

@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    background: Color = IndigoSoft,
    foreground: Color = IndigoText
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Indigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = OnDark, modifier = Modifier.size(15.dp))
            }
        }
        Text(text, style = AppType.BodySmall, color = foreground)
    }
}

@Composable
fun Avatar(
    name: String,
    size: Dp = 42.dp,
    fontSize: Int = 13,
    modifier: Modifier = Modifier,
    background: Color = Navy
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            Format.initials(name),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = DMSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize.sp
            ),
            color = SurfaceWhite
        )
    }
}

@Composable
fun SetRow(
    summary: SetSummary,
    isNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (summary.set.source) {
        "demo" -> Icons.Rounded.MenuBook
        "import" -> Icons.Rounded.Image
        else -> Icons.Rounded.DocumentScanner
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IndigoSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Indigo, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                summary.title,
                style = AppType.LabelSmallStrong.copy(fontSize = 13.sp),
                color = Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.set.titleMl.isNotBlank()) {
                Text(
                    summary.set.titleMl,
                    style = AppType.BodyTiny,
                    color = Navy.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!summary.subTopic.equals(summary.title, ignoreCase = true)) {
                Text(
                    summary.subTopic,
                    style = AppType.LabelSmallStrong.copy(fontSize = 11.sp),
                    color = Indigo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${summary.mainTopic} · ${summary.questionCount} Q · ${Format.relativeTime(summary.createdAt)}",
                style = AppType.MonoMeta,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            summary.bestScore != null -> Text(
                "${summary.bestScore}%",
                style = AppType.MonoValue,
                color = Success
            )
            isNew -> MonoChip("NEW", background = IndigoSoft, color = Indigo)
            else -> Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = IconFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceWhite)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label.uppercase(), style = AppType.MonoSmall, color = InkMuted)
        Text(value, style = AppType.StatValue, color = Navy)
        Text(note, style = AppType.BodyTiny, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RuleRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Indigo
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SurfaceWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Text(text, style = AppType.BodySmall, color = InkMuted, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            style = AppType.Label,
            color = if (destructive) com.padanam.psc.ui.theme.Danger else Navy,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(value, style = AppType.MonoMeta, color = InkMuted)
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = IconFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
fun SuccessTick(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(SuccessSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Rounded.Check, null, tint = Success, modifier = Modifier.size(11.dp))
        Text(text, style = AppType.MonoChip, color = Success)
    }
}

@Composable
fun SoftIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = AmberSoft
) {
    Box(
        modifier = modifier.clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Indigo, modifier = Modifier.size(16.dp))
    }
}
