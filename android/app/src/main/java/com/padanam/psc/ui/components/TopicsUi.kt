package com.padanam.psc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.padanam.psc.data.model.Topics
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Hairline
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.IndigoSoft
import com.padanam.psc.ui.theme.IndigoText
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceField
import com.padanam.psc.ui.theme.SurfaceMuted

/**
 * The fixed main-topic selector. The taxonomy cannot be typed by hand: every
 * value comes from [Topics.MAIN], so the web, server and app keep one vocabulary.
 */
@Composable
fun MainTopicPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Main topic"
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label.uppercase(),
            style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
            color = InkMuted
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(SurfaceField)
                    .border(1.dp, Hairline, shape)
                    .clickable { expanded = true }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    value.ifBlank { "Choose a main topic" },
                    style = AppType.BodyMedium,
                    color = if (value.isBlank()) InkMuted else Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = if (value.isBlank()) InkMuted else Indigo,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                Topics.MAIN.forEach { topic ->
                    val selected = topic.equals(value, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Text(
                                topic,
                                style = AppType.Label,
                                color = if (selected) Indigo else Navy
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Indigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onValueChange(topic)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Main topic plus subtopic editor used by the create, edit and scan-review
 * forms. Subtopics are free-form but existing labels are offered as chips so
 * sets can be moved into the same grouping quickly.
 */
@Composable
fun TopicEditor(
    mainTopic: String,
    onMainTopicChange: (String) -> Unit,
    subTopic: String,
    onSubTopicChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    onSuggest: (() -> Unit)? = null,
    suggesting: Boolean = false,
    suggestEnabled: Boolean = true,
    hint: String? = null
) {
    val cleanSuggestions = suggestions
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals(subTopic.trim(), ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .take(8)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MainTopicPicker(value = mainTopic, onValueChange = onMainTopicChange)

        AppTextField(
            label = "Subtopic",
            value = subTopic,
            onValueChange = { onSubTopicChange(it.take(120)) },
            placeholder = "e.g. Kerala Geography",
            helper = "Leave blank to use the set name."
        )

        if (cleanSuggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "EXISTING SUBTOPICS",
                    style = AppType.MonoSmall.copy(letterSpacing = 0.8.sp),
                    color = InkMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cleanSuggestions.forEach { item ->
                        ChoiceChip(
                            text = item,
                            selected = false,
                            onClick = { onSubTopicChange(item) }
                        )
                    }
                }
            }
        }

        if (onSuggest != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (suggestEnabled) IndigoSoft else SurfaceMuted)
                    .clickable(enabled = suggestEnabled && !suggesting, onClick = onSuggest)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (suggesting) {
                    CircularProgressIndicator(
                        color = Indigo,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (suggestEnabled) Indigo else InkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        if (suggesting) "Classifying this set…" else "Suggest with AI",
                        style = AppType.LabelSmallStrong,
                        color = if (suggestEnabled) IndigoText else InkMuted
                    )
                    Text(
                        "Uses the title, subject and questions to choose a fixed main topic and a reusable subtopic.",
                        style = AppType.BodyTiny,
                        color = InkMuted
                    )
                }
            }
        }

        if (hint != null) {
            Text(hint, style = AppType.BodyTiny, color = InkMuted)
        }
    }
}
